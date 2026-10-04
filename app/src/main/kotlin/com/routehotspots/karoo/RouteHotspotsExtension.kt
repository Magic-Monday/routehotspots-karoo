package com.routehotspots.karoo

import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.HideSymbols
import io.hammerhead.karooext.models.MapEffect
import io.hammerhead.karooext.models.OnNavigationState
import io.hammerhead.karooext.models.ShowSymbols
import io.hammerhead.karooext.models.Symbol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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

    override fun onCreate() {
        super.onCreate()
        karooSystem.connect()
    }

    override fun startMap(emitter: Emitter<MapEffect>) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val job: Job = scope.launch {
            karooSystem.consumerFlow<OnNavigationState>()
                .map { it.state }
                .distinctUntilChanged()
                .collect { state -> handleNavigationState(state, emitter) }
        }
        emitter.setCancellable {
            job.cancel()
            lastRouteKey = null
            lastSymbolIds = emptyList()
        }
    }

    private suspend fun handleNavigationState(
        state: OnNavigationState.NavigationState,
        emitter: Emitter<MapEffect>,
    ) {
        // Alleen een vooraf geladen/geactiveerde route (NavigatingRoute) telt als
        // "injectie-bron" — zie plan: géén ad-hoc "navigeer naar X" (NavigatingToDestination).
        val route = state as? OnNavigationState.NavigationState.NavigatingRoute
        if (route == null) {
            clearHotspots(emitter)
            return
        }

        val routeCoords = GooglePolyline.decode(route.routePolyline)
        if (routeCoords.size < 2) {
            clearHotspots(emitter)
            return
        }

        val key = "${route.name}:${routeCoords.size}"
        if (key == lastRouteKey) return
        lastRouteKey = key

        // Backend verwacht [lon, lat]-paren; de polyline-decoder geeft [lat, lon].
        val lonLatCoords = routeCoords.map { listOf(it[1], it[0]) }
        val hotspots = runCatching {
            RouteHotspotsApi.hotspotsAlongRoute(
                routeCoords = lonLatCoords,
                categories = RouteHotspotsApi.DEFAULT_CATEGORIES,
                bufferMeters = 3000,
            )
        }.getOrElse {
            // Fail-soft: geen hotspots i.p.v. een crash tijdens het fietsen.
            emptyList()
        }

        injectHotspots(hotspots, emitter)
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
            emitter.onNext(ShowSymbols(symbols))
        }
    }

    private fun clearHotspots(emitter: Emitter<MapEffect>) {
        if (lastSymbolIds.isNotEmpty()) {
            emitter.onNext(HideSymbols(lastSymbolIds))
        }
        lastRouteKey = null
        lastSymbolIds = emptyList()
    }

    override fun onDestroy() {
        karooSystem.disconnect()
        super.onDestroy()
    }

    companion object {
        /** Backend-categorie (zie `POI_CATEGORIES` in routehotspots.py) → Karoo POI-icoontype. */
        internal fun categoryToPoiType(category: String?): String = when (category) {
            "coffee" -> Symbol.POI.Types.COFFEE
            "lunch" -> Symbol.POI.Types.FOOD
            "viewpoint" -> Symbol.POI.Types.VIEWPOINT
            "monument" -> Symbol.POI.Types.MONUMENT
            "water" -> Symbol.POI.Types.WATER
            else -> Symbol.POI.Types.GENERIC
        }
    }
}
