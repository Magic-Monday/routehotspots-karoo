package com.routehotspots.karoo

/**
 * Decoder voor Google's "Encoded Polyline Algorithm Format" (precisie 1e5) — het
 * formaat dat de Karoo SDK gebruikt voor `NavigationState.routePolyline` (en voor
 * `ShowPolyline.encodedPolyline`, zie de Mapbox `PolylineUtils.encode(_, 5)`-aanroep
 * in de karoo-ext sample-extensie: zelfde precisie, symmetrisch formaat).
 *
 * Eigen implementatie i.p.v. de Mapbox-geojson-dependency erbij te halen: die vereist
 * een los Mapbox-maven-repo (en mogelijk een download-token) voor één decodeerfunctie.
 */
object GooglePolyline {

    /** Decodeer naar een lijst van `[lat, lon]`-paren, in volgorde van de polyline. */
    fun decode(encoded: String): List<DoubleArray> {
        val points = ArrayList<DoubleArray>()
        var index = 0
        var lat = 0
        var lon = 0
        while (index < encoded.length) {
            lat += decodeSignedValue(encoded, index) { index = it }
            lon += decodeSignedValue(encoded, index) { index = it }
            points.add(doubleArrayOf(lat / 1e5, lon / 1e5))
        }
        return points
    }

    /** Decodeert één var-length, zigzag-encoded waarde vanaf [startIndex]; meldt de nieuwe index via [advance]. */
    private inline fun decodeSignedValue(encoded: String, startIndex: Int, advance: (Int) -> Unit): Int {
        var index = startIndex
        var shift = 0
        var result = 0
        var b: Int
        do {
            b = encoded[index++].code - 63
            result = result or ((b and 0x1f) shl shift)
            shift += 5
        } while (b >= 0x20)
        advance(index)
        return if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
    }
}
