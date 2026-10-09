package com.routehotspots.karoo

import android.content.Context

/** Hoofdschakelaar: hotspots wel/niet automatisch injecteren op elke geactiveerde route. */
object FeaturePrefs {
    private const val PREFS_NAME = "route_hotspots_prefs"
    private const val KEY_ENABLED = "feature_enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }
}
