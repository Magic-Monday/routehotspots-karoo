package com.routehotspots.karoo

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Client voor de RouteHotspots-backend: hotspots langs een route ophalen.
 *
 * Endpoint (publiek, geen auth — zie routes/routehotspots.py:api_poi_query):
 *   POST https://app.routehotspots.com/api/poi/query
 *   body: { coords: [[lon,lat],...], categories: ["coffee","lunch",...], buffer_m: 3000,
 *           spread_m: 1000 }
 * `spread_m` is géén no-op als je 'm weglaat — de backend spreidt pas als je 'm expliciet
 * meestuurt (default 0 = alle ruwe Overpass-resultaten, kan voor een lange route in de
 * honderden lopen; zie README "Live-test — bevindingen").
 *
 * De backend zoekt via Overpass POI's binnen een buffer rond de route (auto-expand
 * 3→5→10→25 km) en groepeert ze per categorie. Dit is de "injectie"-bron: geef de
 * ACTIEVE Karoo-route mee → krijg hotspots terug.
 *
 * Deze laag is zuiver Kotlin/JVM (geen karoo-ext nodig) en dus los testbaar.
 */
object RouteHotspotsApi {
    private const val BASE = "https://app.routehotspots.com"
    private val json = Json { ignoreUnknownKeys = true }

    /** Eén hotspot zoals de backend 'm teruggeeft. */
    @Serializable
    data class Hotspot(
        val id: String? = null,
        val lat: Double,
        val lon: Double,
        val name: String? = null,
        val category: String? = null,
        val tags: Map<String, String>? = null,
    )

    @Serializable
    private data class QueryBody(
        val coords: List<List<Double>>,
        val categories: List<String>,
        val buffer_m: Int,
        val spread_m: Int,
    )

    // De backend geeft POI's gegroepeerd per categorie terug. We houden het ruim
    // (ignoreUnknownKeys) zodat extra velden de parse niet breken.
    @Serializable
    private data class QueryResponse(
        val pois_by_category: Map<String, List<Hotspot>>? = null,
        val pois: List<Hotspot>? = null,
        // Per categorie de Overpass-fout (bv. "http 504: …") als die categorie niets
        // opleverde. De backend antwoordt dan tóch HTTP 200 — zonder dit veld zie je
        // "0 hotspots" en weet je niet of de route leeg is of de server overbelast.
        val errors_by_category: Map<String, String>? = null,
    )

    /** Antwoord van [queryAlongRoute]: de hotspots plus eventuele fouten per categorie. */
    data class QueryResult(
        val hotspots: List<Hotspot>,
        val errorsByCategory: Map<String, String>,
    ) {
        /** Alle gevraagde categorieën faalden en er kwam niets terug → geen echt resultaat. */
        fun isCompleteFailure(requested: List<String>): Boolean =
            hotspots.isEmpty() && requested.isNotEmpty() && requested.all { it in errorsByCategory }
    }

    /**
     * Haal hotspots op langs [routeCoords] (lijst van [lon, lat]-paren, bv. uit de
     * actieve Karoo-route). [categories] zijn de backend-categorie-keys
     * (coffee/lunch/viewpoint/...). Gooit bij netwerk-/parsefout.
     */
    fun hotspotsAlongRoute(
        routeCoords: List<List<Double>>,
        categories: List<String>,
        bufferMeters: Int = 3000,
        spreadMeters: Int = 1000,
    ): List<Hotspot> = queryAlongRoute(routeCoords, categories, bufferMeters, spreadMeters).hotspots

    /** Als [hotspotsAlongRoute], maar mét de fouten per categorie uit het antwoord. */
    fun queryAlongRoute(
        routeCoords: List<List<Double>>,
        categories: List<String>,
        bufferMeters: Int = 3000,
        spreadMeters: Int = 1000,
    ): QueryResult {
        val payload = json.encodeToString(
            QueryBody.serializer(),
            QueryBody(routeCoords, categories, bufferMeters, spreadMeters),
        )
        val conn = (URL("$BASE/api/poi/query").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 60_000 // Overpass + auto-expand kan even duren
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "RouteHotspots-Karoo/0.1")
        }
        try {
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                throw RuntimeException("poi/query HTTP $code: ${text.take(200)}")
            }
            val parsed = json.decodeFromString(QueryResponse.serializer(), text)
            // Eén platte, ge-dedupeerde lijst (op id, anders op afgeronde coördinaat).
            val flat = buildList {
                parsed.pois_by_category?.values?.forEach { addAll(it) }
                parsed.pois?.let { addAll(it) }
            }
            return QueryResult(
                hotspots = flat.distinctBy { it.id ?: "%.5f,%.5f".format(it.lat, it.lon) },
                errorsByCategory = parsed.errors_by_category.orEmpty(),
            )
        } finally {
            conn.disconnect()
        }
    }

    /** Standaard set hotspot-categorieën (backend-keys). Pas aan n.a.v. POI_CATEGORIES. */
    val DEFAULT_CATEGORIES = listOf("viewpoint", "coffee", "lunch", "monument", "water")
}
