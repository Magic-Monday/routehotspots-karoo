package com.routehotspots.karoo

import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.KarooExtension
// TODO(F1): bevestig de exacte model-klassen tegen de docs:
//   https://hammerheadnav.github.io/karoo-ext/karoo-ext/io.hammerhead.karooext.models/
//   - Navigatie-event met de actieve route (naam, polyline, POI's) — vermoedelijk
//     `OnNavigationState` (consume via karooSystem.addConsumer { ... }).
//   - Map-effect om iconen/symbolen op coördinaten te tekenen in een eigen kaartlaag —
//     dispatch via karooSystem.dispatch(...). (Zie "show map items: polylines, icons at
//     locations" in de SDK.)
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * RouteHotspots Karoo-extensie — KERNCONCEPT (Elio, 3 okt 2026):
 * zodra er een route op de Karoo actief is, injecteren we automatisch de hotspots
 * langs die route als POI-markers op de kaart. Geen route vooraf bouwen.
 *
 * Flow:
 *   1. Verbind met KarooSystemService.
 *   2. Consumeer het navigatie-event → lees de polyline van de ACTIEVE route.
 *   3. POST die coords naar RouteHotspotsApi (/api/poi/query) → hotspots.
 *   4. Dispatch map-effects → teken de hotspots als iconen op de kaart.
 *
 * Dit bestand is het skelet: de karoo-ext-specifieke klassen (nav-event + map-effect)
 * moeten in Android Studio tegen de live SDK worden bevestigd — zie de TODO's. De
 * netwerk-/datalaag (RouteHotspotsApi) is compleet en los testbaar.
 */
class RouteHotspotsExtension : KarooExtension("routehotspots", "1") {

    private val karooSystem by lazy { KarooSystemService(applicationContext) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Onthoud de laatst-geïnjecteerde route zodat we niet elke tick opnieuw queryen.
    private var lastRouteKey: String? = null

    override fun onCreate() {
        super.onCreate()
        karooSystem.connect { connected ->
            if (connected) subscribeToActiveRoute()
        }
    }

    private fun subscribeToActiveRoute() {
        // TODO(F1): vervang `navConsumer` door de echte consumer-signatuur van het
        //   navigatie-event (bv. addConsumer { state: OnNavigationState -> ... }).
        //   Haal uit `state` de route-naam + polyline (lijst van lat/lon-punten).
        //
        // Pseudostructuur:
        // karooSystem.addConsumer { state: OnNavigationState ->
        //     val route = state.route ?: return@addConsumer clearHotspots()
        //     val coords = route.polyline.map { listOf(it.lng, it.lat) } // [lon,lat]!
        //     val key = route.name + ":" + coords.size
        //     if (key == lastRouteKey) return@addConsumer
        //     lastRouteKey = key
        //     injectHotspots(coords)
        // }
    }

    /** Haal hotspots langs de route op (achtergrond) en teken ze op de kaart. */
    private fun injectHotspots(routeCoords: List<List<Double>>) {
        scope.launch {
            val hotspots = runCatching {
                RouteHotspotsApi.hotspotsAlongRoute(
                    routeCoords = routeCoords,
                    categories = RouteHotspotsApi.DEFAULT_CATEGORIES,
                    bufferMeters = 3000,
                )
            }.getOrElse {
                // Fail-soft: geen hotspots i.p.v. een crash tijdens het fietsen.
                emptyList()
            }
            showHotspotsOnMap(hotspots)
        }
    }

    private fun showHotspotsOnMap(hotspots: List<RouteHotspotsApi.Hotspot>) {
        // TODO(F1): dispatch per hotspot (of in bulk) een map-effect dat een icoon op
        //   (lat, lon) tekent in de RouteHotspots-kaartlaag. Iets als:
        // hotspots.forEach { h ->
        //     karooSystem.dispatch(ShowSymbol(id = h.id ?: "${h.lat},${h.lon}",
        //         lat = h.lat, lng = h.lon, icon = iconFor(h.category), label = h.name))
        // }
        // Bevestig de effect-klasse (ShowSymbols/MapEffect) + icon-API tegen de docs.
    }

    private fun clearHotspots() {
        lastRouteKey = null
        // TODO(F1): dispatch het effect om de eigen kaartlaag/symbolen te wissen.
    }

    override fun onDestroy() {
        scope.cancel()
        karooSystem.disconnect()
        super.onDestroy()
    }
}
