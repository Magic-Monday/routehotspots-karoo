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
        // "drinkwater", niet "water" — dat was de bug (RouteHotspotsExtension.kt-geschiedenis):
        // de backend-sleutel is drinkwater, "water" matchte nooit en gaf 0 resultaten.
        assertEquals(Symbol.POI.Types.WATER, RouteHotspotsExtension.categoryToPoiType("drinkwater"))
        assertEquals(Symbol.POI.Types.BIKE_SHOP, RouteHotspotsExtension.categoryToPoiType("bicycle"))
        assertEquals(Symbol.POI.Types.CAMPING, RouteHotspotsExtension.categoryToPoiType("camping"))
        assertEquals(Symbol.POI.Types.GAS_STATION, RouteHotspotsExtension.categoryToPoiType("charging"))
        assertEquals(Symbol.POI.Types.RESTROOM, RouteHotspotsExtension.categoryToPoiType("toilet"))
        assertEquals(Symbol.POI.Types.SHOPPING, RouteHotspotsExtension.categoryToPoiType("supermarket"))
    }

    @Test
    fun `categoryToPoiType - onbekende of ontbrekende categorie valt terug op GENERIC`() {
        assertEquals(Symbol.POI.Types.GENERIC, RouteHotspotsExtension.categoryToPoiType("onbekend"))
        assertEquals(Symbol.POI.Types.GENERIC, RouteHotspotsExtension.categoryToPoiType(null))
    }
}
