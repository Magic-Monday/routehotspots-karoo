package com.routehotspots.karoo

import org.junit.Assert.assertEquals
import org.junit.Test

class GooglePolylineTest {

    @Test
    fun `decode - referentievoorbeeld uit Google's eigen documentatie`() {
        // Zie https://developers.google.com/maps/documentation/utilities/polylinealgorithm
        val decoded = GooglePolyline.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@")

        assertEquals(3, decoded.size)
        assertPoint(38.5, -120.2, decoded[0])
        assertPoint(40.7, -120.95, decoded[1])
        assertPoint(43.252, -126.453, decoded[2])
    }

    @Test
    fun `decode - lege string geeft lege lijst`() {
        assertEquals(0, GooglePolyline.decode("").size)
    }

    private fun assertPoint(expectedLat: Double, expectedLon: Double, actual: DoubleArray) {
        assertEquals(expectedLat, actual[0], 1e-5)
        assertEquals(expectedLon, actual[1], 1e-5)
    }
}
