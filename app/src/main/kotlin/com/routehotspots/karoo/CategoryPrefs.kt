package com.routehotspots.karoo

import android.content.Context

/**
 * Welke hotspot-categorieën de gebruiker wil zien — ingesteld via het schermpje in
 * [MainActivity], gelezen door [RouteHotspotsExtension] bij elke nieuwe route-query.
 *
 * SharedPreferences i.p.v. een in-memory singleton: de extensie (Service) en de UI
 * (Activity) draaien in hetzelfde proces, maar de Service kan door Android herstart
 * worden terwijl de Activity niet open staat — de keuze moet dat overleven.
 */
object CategoryPrefs {
    private const val PREFS_NAME = "route_hotspots_prefs"
    private const val KEY_ENABLED_CATEGORIES = "enabled_categories"

    fun loadEnabled(context: Context): Set<String> {
        val stored = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getStringSet(KEY_ENABLED_CATEGORIES, null)
        return if (stored.isNullOrEmpty()) DEFAULT_ENABLED_CATEGORY_KEYS else stored.toSet()
    }

    fun setEnabled(context: Context, categoryKeys: Set<String>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_ENABLED_CATEGORIES, categoryKeys)
            .apply()
    }
}
