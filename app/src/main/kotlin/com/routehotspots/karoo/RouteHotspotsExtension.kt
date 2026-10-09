package com.routehotspots.karoo

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.HideSymbols
import io.hammerhead.karooext.models.InRideAlert
import io.hammerhead.karooext.models.MapEffect
import io.hammerhead.karooext.models.OnNavigationState
import io.hammerhead.karooext.models.ShowSymbols
import io.hammerhead.karooext.models.Symbol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * RouteHotspots Karoo-extensie — KERNCONCEPT (Elio, 3 okt 2026):
 * zodra er een route op de Karoo actief is, injecteren we automatisch de hotspots
 * langs die route als POI-markers op de kaart. Geen route vooraf bouwen.
 *
 * Flow:
 *   1. Karoo System bindt de kaartlaag → [startMap] wordt aangeroepen (vereist
 *      `mapLayer="true"` in extension_info.xml).
 *   2. Consumeer [OnNavigationState] → bij een actieve, geladen route
 *      (`NavigatingRoute`) decodeer de `routePolyline` (Google-encoded, precisie 1e5).
 *   3. POST die coords naar [RouteHotspotsApi] (`/api/poi/query`) → hotspots.
 *   4. `emitter.onNext(ShowSymbols(...))` → teken de hotspots als POI-iconen; wis de
 *      vorige markers eerst (`HideSymbols`) zodra de route wisselt of wegvalt.
 */
class RouteHotspotsExtension : KarooExtension("routehotspots", "1") {

    private val karooSystem by lazy { KarooSystemService(applicationContext) }

    // Onthoud de laatst-geïnjecteerde route (+ diens symbol-ID's) zodat we niet
    // elke identieke tick opnieuw queryen, en de vorige markers kunnen wissen.
    private var lastRouteKey: String? = null
    private var lastSymbolIds: List<String> = emptyList()

    // Per route maximaal één keer dezelfde waarschuwing tonen — de Karoo emit elke ~5 s
    // een vers NavigatingRoute-event, en zonder deze grendel zou de rijder elke 5 s
    // dezelfde "zet wifi aan"-melding krijgen.
    private var alertedForRoute: Pair<String, AlertKind>? = null

    // Na een mislukte poging (geen netwerk, timeout, Overpass 504) minstens zo lang
    // wachten vóór de volgende: de Karoo emit elke ~5 s een route-event en elke poging
    // houdt de backend (en Overpass) tot een minuut bezig.
    private var retryNotBefore: Long = 0L

    // Eigen retry-timer: de Karoo stuurt alléén een nieuw NavigatingRoute-event als er
    // iets wijzigt, dus na "zet wifi aan" kun je niet op een event wachten — gezien
    // 9 okt: wifi ging aan, maar er kwam geen event meer en dus ook geen nieuwe poging.
    private var mapScope: CoroutineScope? = null
    private var retryJob: Job? = null

    private enum class AlertKind { NO_NETWORK, SERVER }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate — verbinden met Karoo System")
        karooSystem.connect { connected -> Log.i(TAG, "karooSystem.connect: connected=$connected") }
    }

    override fun startMap(emitter: Emitter<MapEffect>) {
        Log.i(TAG, "startMap aangeroepen — Karoo System heeft de kaartlaag gebonden")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        mapScope = scope
        val job: Job = scope.launch {
            karooSystem.consumerFlow<OnNavigationState>()
                .map { it.state }
                .distinctUntilChanged()
                .collect { state ->
                    Log.i(TAG, "OnNavigationState: ${state::class.simpleName}")
                    handleNavigationState(state, emitter)
                }
        }
        emitter.setCancellable {
            Log.i(TAG, "startMap gestopt (kaartlaag losgekoppeld)")
            retryJob?.cancel()
            retryJob = null
            mapScope = null
            job.cancel()
            lastRouteKey = null
            lastSymbolIds = emptyList()
        }
    }

    private suspend fun handleNavigationState(
        state: OnNavigationState.NavigationState,
        emitter: Emitter<MapEffect>,
    ) {
        if (!FeaturePrefs.isEnabled(applicationContext)) {
            Log.i(TAG, "hoofdschakelaar uit — geen hotspots injecteren")
            clearHotspots(emitter)
            return
        }

        // Alleen een vooraf geladen/geactiveerde route (NavigatingRoute) telt als
        // "injectie-bron" — zie plan: géén ad-hoc "navigeer naar X" (NavigatingToDestination).
        val route = state as? OnNavigationState.NavigationState.NavigatingRoute
        if (route == null) {
            clearHotspots(emitter)
            return
        }

        Log.i(TAG, "NavigatingRoute: name=${route.name} polyline-lengte=${route.routePolyline.length}")
        val routeCoords = GooglePolyline.decode(route.routePolyline)
        Log.i(TAG, "polyline gedecodeerd naar ${routeCoords.size} coords")
        if (routeCoords.size < 2) {
            clearHotspots(emitter)
            return
        }

        val categories = CategoryPrefs.loadEnabled(applicationContext).toList()
        val maxDetourM = DetourPrefs.maxDetourMeters(applicationContext)
        // Sleutel = route + instellingen: wijzig je categorieën of omrij-afstand terwijl
        // de route actief is, dan volgt bij het eerstvolgende event een verse set.
        val key = HotspotCache.key(route.routePolyline, categories, maxDetourM)
        if (key == lastRouteKey) {
            Log.i(TAG, "zelfde route+instellingen als vorige keer — niet opnieuw queryen")
            return
        }

        if (categories.isEmpty()) {
            // Alles uitgezet in de instellingen — geen zinvolle call te maken, en niet
            // elke ~5s de backend lastigvallen met een gegarandeerde 400.
            Log.i(TAG, "geen categorieën aan — hotspots wissen, niet queryen")
            clearHotspots(emitter)
            return
        }

        // Cache eerst: thuis (met wifi) één keer geactiveerd = onderweg geen netwerk nodig.
        HotspotCache.load(applicationContext, key)?.let { cached ->
            retryJob?.cancel(); retryJob = null
            lastRouteKey = key
            Log.i(TAG, "${cached.hotspots.size} hotspots uit cache (opgeslagen ${(System.currentTimeMillis() - cached.savedAt) / 60_000} min geleden)")
            injectHotspots(cached.hotspots, emitter)
            showLoaded(cached.hotspots.size, fromCache = true)
            return
        }

        if (System.currentTimeMillis() < retryNotBefore) {
            Log.i(TAG, "wachten tot volgende poging (${(retryNotBefore - System.currentTimeMillis()) / 1000}s)")
            return
        }

        // De Karoo-firmware zet wifi zelf UIT zodra een rit/route actief wordt
        // (HxStateManagerService: "handleUpdateWifi - disabling for ACTIVE state",
        // gezien in logcat 9 okt 2026). Zonder netwerk heeft de call geen zin:
        // meld het één keer en laat de retry-lus doorlopen tot de rijder wifi aanzet.
        if (!hasNetwork()) {
            Log.i(TAG, "geen netwerk — melding tonen, call overslaan, volgende poging over ${RETRY_AFTER_FAILURE_MS / 1000}s")
            showAlertOnce(key, AlertKind.NO_NETWORK)
            scheduleRetry(state, emitter)
            return
        }

        // Backend verwacht [lon, lat]-paren; de polyline-decoder geeft [lat, lon].
        val lonLatCoords = routeCoords.map { listOf(it[1], it[0]) }
        Log.i(TAG, "POST /api/poi/query — categorieën=$categories max-omrijden=${maxDetourM}m eerste coord=${lonLatCoords.first()} laatste=${lonLatCoords.last()}")
        val result = runCatching {
            RouteHotspotsApi.queryAlongRoute(
                routeCoords = lonLatCoords,
                categories = categories,
                bufferMeters = maxDetourM,
            )
        }.onFailure {
            Log.e(TAG, "queryAlongRoute faalde: ${it.javaClass.name}: ${it.message}")
            Log.e(TAG, "queryAlongRoute faalde (met stacktrace)", it)
        }

        // De HTTP-call blokkeert en is niet te onderbreken: als de kaartlaag intussen is
        // losgekoppeld (scope gecancelled), is dit antwoord verlaten — niets meer tekenen
        // of melden. Gezien 9 okt: een verlaat 504-antwoord toonde "niet beschikbaar"
        // terwijl de nieuwe binding al 26 hotspots op de kaart had.
        if (!currentCoroutineContext().isActive) {
            Log.i(TAG, "antwoord genegeerd — kaartlaag is intussen losgekoppeld")
            return
        }

        // Alleen bij een geslaagde call (ongeacht of er hotspots in zaten) onthouden we
        // de route als "afgehandeld" — bij een mislukte poging (wifi-hapering, timeout)
        // blijft lastRouteKey ongewijzigd, zodat een volgende state-wisseling voor
        // DEZELFDE route alsnog een nieuwe poging waagt i.p.v. stil overgeslagen te worden.
        val query = result.getOrNull()
        if (query == null) {
            val kind = if (hasNetwork()) AlertKind.SERVER else AlertKind.NO_NETWORK
            showAlertOnce(key, kind)
            scheduleRetry(state, emitter)
            Log.i(TAG, "geen retry-blokkade gezet — volgende poging voor deze route over ${RETRY_AFTER_FAILURE_MS / 1000}s")
            return
        }
        if (query.errorsByCategory.isNotEmpty()) {
            Log.w(TAG, "backend meldde fouten per categorie: ${query.errorsByCategory}")
        }
        // HTTP 200 met nul hotspots óf nul omdat élke categorie faalde (Overpass 504 e.d.)?
        // Dat laatste is geen resultaat maar een mislukking: niet onthouden, wel melden.
        if (query.isCompleteFailure(categories)) {
            showAlertOnce(key, AlertKind.SERVER)
            scheduleRetry(state, emitter)
            Log.i(TAG, "alle categorieën faalden op de backend — volgende poging over ${RETRY_AFTER_FAILURE_MS / 1000}s")
            return
        }
        retryJob?.cancel()
        retryJob = null
        val hotspots = query.hotspots
        lastRouteKey = key
        Log.i(TAG, "${hotspots.size} hotspots ontvangen")
        HotspotCache.save(
            applicationContext, key,
            HotspotCache.Entry(route.name, categories, maxDetourM, System.currentTimeMillis(), hotspots),
        )

        injectHotspots(hotspots, emitter)
        showLoaded(hotspots.size, fromCache = false)
    }

    private fun showLoaded(count: Int, fromCache: Boolean) {
        if (count == 0) return
        karooSystem.dispatch(
            InRideAlert(
                id = "routehotspots-loaded",
                icon = R.drawable.ic_launcher_foreground,
                title = getString(if (fromCache) R.string.alert_loaded_cached_title else R.string.alert_loaded_title, count),
                detail = null,
                autoDismissMs = 4_000,
                backgroundColor = R.color.rh_alert_info_bg,
                textColor = R.color.rh_alert_text,
            ),
        )
    }

    /** Plan over [RETRY_AFTER_FAILURE_MS] zelf een nieuwe poging voor [state], los van Karoo-events. */
    private fun scheduleRetry(state: OnNavigationState.NavigationState, emitter: Emitter<MapEffect>) {
        retryNotBefore = System.currentTimeMillis() + RETRY_AFTER_FAILURE_MS
        retryJob?.cancel()
        retryJob = mapScope?.launch {
            delay(RETRY_AFTER_FAILURE_MS)
            retryNotBefore = 0L
            Log.i(TAG, "eigen retry-timer afgelopen — opnieuw proberen")
            handleNavigationState(state, emitter)
        }
    }

    /** Is er een netwerk met internet-capability actief (wifi; de Karoo heeft geen mobiel)? */
    private fun hasNetwork(): Boolean {
        val cm = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true // onbekend → niet blokkeren, de call zelf geeft dan uitsluitsel
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** Toon [kind] hooguit één keer per route; wordt gereset bij route-wissel ([clearHotspots]). */
    private fun showAlertOnce(routeKey: String, kind: AlertKind) {
        if (alertedForRoute == routeKey to kind) return
        alertedForRoute = routeKey to kind
        val (title, detail) = when (kind) {
            AlertKind.NO_NETWORK -> getString(R.string.alert_no_network_title) to getString(R.string.alert_no_network_detail)
            AlertKind.SERVER -> getString(R.string.alert_server_title) to getString(R.string.alert_server_detail)
        }
        Log.i(TAG, "InRideAlert $kind: $title")
        val ok = karooSystem.dispatch(
            InRideAlert(
                id = "routehotspots-${kind.name.lowercase()}",
                icon = R.drawable.ic_launcher_foreground,
                title = title,
                detail = detail,
                autoDismissMs = 10_000,
                backgroundColor = R.color.rh_alert_warn_bg,
                textColor = R.color.rh_alert_text,
            ),
        )
        if (!ok) Log.w(TAG, "InRideAlert dispatch geweigerd (Karoo System niet verbonden?)")
    }

    private fun injectHotspots(hotspots: List<RouteHotspotsApi.Hotspot>, emitter: Emitter<MapEffect>) {
        if (lastSymbolIds.isNotEmpty()) {
            emitter.onNext(HideSymbols(lastSymbolIds))
        }
        val symbols = hotspots.map { h ->
            Symbol.POI(
                id = h.id ?: "${h.lat},${h.lon}",
                lat = h.lat,
                lng = h.lon,
                type = categoryToPoiType(h.category),
                name = h.name,
            )
        }
        lastSymbolIds = symbols.map { it.id }
        if (symbols.isNotEmpty()) {
            Log.i(TAG, "ShowSymbols: ${symbols.size} POI's getekend")
            emitter.onNext(ShowSymbols(symbols))
        }
    }

    private fun clearHotspots(emitter: Emitter<MapEffect>) {
        if (lastSymbolIds.isNotEmpty()) {
            emitter.onNext(HideSymbols(lastSymbolIds))
        }
        lastRouteKey = null
        lastSymbolIds = emptyList()
        alertedForRoute = null
        retryNotBefore = 0L
        retryJob?.cancel()
        retryJob = null
    }

    override fun onDestroy() {
        karooSystem.disconnect()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "RouteHotspots"
        private const val RETRY_AFTER_FAILURE_MS = 30_000L

        /** Backend-categorie (zie `POI_CATEGORIES` in routehotspots.py) → Karoo POI-icoontype. */
        internal fun categoryToPoiType(category: String?): String = when (category) {
            "coffee" -> Symbol.POI.Types.COFFEE
            "lunch" -> Symbol.POI.Types.FOOD
            "viewpoint" -> Symbol.POI.Types.VIEWPOINT
            "monument" -> Symbol.POI.Types.MONUMENT
            "drinkwater" -> Symbol.POI.Types.WATER
            "bicycle" -> Symbol.POI.Types.BIKE_SHOP
            "camping" -> Symbol.POI.Types.CAMPING
            "charging" -> Symbol.POI.Types.GAS_STATION
            "toilet" -> Symbol.POI.Types.RESTROOM
            "supermarket" -> Symbol.POI.Types.SHOPPING
            else -> Symbol.POI.Types.GENERIC
        }
    }
}
