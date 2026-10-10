package com.routehotspots.karoo

import androidx.annotation.StringRes

/**
 * One hotspot category as the backend knows it (key + localized label + emoji) — see
 * `POI_CATEGORIES` in `integration_dashboard/app/routes/routehotspots.py`. Keys must
 * match exactly; the backend silently skips unknown keys (no error), so a typo here
 * simply yields 0 results for that category. Labels live in strings.xml (English
 * default, Dutch in values-nl) so the Karoo's language setting decides.
 */
data class PoiCategory(val key: String, @StringRes val labelRes: Int, val emoji: String)

val ALL_POI_CATEGORIES = listOf(
    PoiCategory("coffee", R.string.cat_coffee, "☕"),
    PoiCategory("lunch", R.string.cat_lunch, "🍴"),
    PoiCategory("viewpoint", R.string.cat_viewpoint, "🌅"),
    PoiCategory("monument", R.string.cat_monument, "🏛️"),
    PoiCategory("drinkwater", R.string.cat_drinkwater, "💧"),
    PoiCategory("bicycle", R.string.cat_bicycle, "🚲"),
    PoiCategory("camping", R.string.cat_camping, "🏕️"),
    PoiCategory("charging", R.string.cat_charging, "⚡"),
    PoiCategory("toilet", R.string.cat_toilet, "🚻"),
    PoiCategory("supermarket", R.string.cat_supermarket, "🛒"),
)

/** On by default — the original F1 set, with the real `drinkwater` key. */
val DEFAULT_ENABLED_CATEGORY_KEYS = setOf("coffee", "lunch", "viewpoint", "monument", "drinkwater")
