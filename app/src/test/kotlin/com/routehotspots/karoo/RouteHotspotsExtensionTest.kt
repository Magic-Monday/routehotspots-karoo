package com.routehotspots.karoo

import io.hammerhead.karooext.models.Symbol
import org.junit.Assert.assertEquals
import org.junit.Test

class RouteHotspotsExtensionTest {

    @Test
    fun `categoryToPoiType - mapt backend-categorieen naar Karoo POI-types`() {
        assertEquals(Symbol.POI.Types.COFFEE, RouteHotspotsExtension.categoryToPoiType("coffee"))
        assertEquals(Symbol.POI.Types.FOOD, RouteHotspotsExtension.categoryToPoiType("lunch"))
        assertEquals(Symbol.POI.Types.VIEWPOINT, RouteHotspotsExtension.categoryToPoiType("viewpoint"))
        assertEquals(Symbol.POI.Types.MONUMENT, RouteHotspotsExtension.categoryToPoiType("monument"))
        assertEquals(Symbol.POI.Types.WATER, RouteHotspotsExtension.categoryToPoiType("water"))
    }

    @Test
    fun `categoryToPoiType - onbekende of ontbrekende categorie valt terug op GENERIC`() {
        assertEquals(Symbol.POI.Types.GENERIC, RouteHotspotsExtension.categoryToPoiType("onbekend"))
        assertEquals(Symbol.POI.Types.GENERIC, RouteHotspotsExtension.categoryToPoiType(null))
    }
}
