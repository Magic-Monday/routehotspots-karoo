package com.routehotspots.karoo

/**
 * Eén hotspot-categorie zoals de backend 'm kent (sleutel, label, emoji) — zie
 * `POI_CATEGORIES` in `integration_dashboard/app/routes/routehotspots.py`. Sleutels
 * moeten exact overeenkomen; de backend skipt onbekende sleutels stilletjes (geen
 * foutmelding), dus een typefout hier levert gewoon 0 resultaten op voor die categorie.
 */
data class PoiCategory(val key: String, val label: String, val emoji: String)

val ALL_POI_CATEGORIES = listOf(
    PoiCategory("coffee", "Koffie", "☕"),
    PoiCategory("lunch", "Lunch", "🍴"),
    PoiCategory("viewpoint", "Uitzicht", "🌅"),
    PoiCategory("monument", "Monument", "🏛️"),
    PoiCategory("drinkwater", "Drinkwater", "💧"),
    PoiCategory("bicycle", "Fietsenmaker", "🚲"),
    PoiCategory("camping", "Camping", "🏕️"),
    PoiCategory("charging", "Oplaadpunt", "⚡"),
    PoiCategory("toilet", "Toilet", "🚻"),
    PoiCategory("supermarket", "Supermarkt", "🛒"),
)

/** Standaard aan — de oorspronkelijke F1-set, nu met de echte `drinkwater`-sleutel. */
val DEFAULT_ENABLED_CATEGORY_KEYS = setOf("coffee", "lunch", "viewpoint", "monument", "drinkwater")
