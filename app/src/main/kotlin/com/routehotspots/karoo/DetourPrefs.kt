package com.routehotspots.karoo

import android.content.Context

/**
 * "Maximaal omrijden": hoe ver een hotspot van de route mag liggen. Gaat als
 * `buffer_m` + `expand:false` naar de backend — de backend verbreedt dan NIET
 * automatisch naar 3/5/10/25 km als een categorie niets oplevert.
 *
 * Gemeten 9 okt 2026 (Nijmegen→Utrecht, buffer 3 km): mediaan ~1 km, een kwart op
 * 2–3 km van de route. Voor een kop koffie rijdt niemand 3 km om → default 500 m.
 */
object DetourPrefs {
    private const val PREFS_NAME = "route_hotspots_prefs"
    private const val KEY_MAX_DETOUR_M = "max_detour_m"

    const val DEFAULT_MAX_DETOUR_M = 500

    /** Keuzes in de instellingen (meters, label). */
    val OPTIONS: List<Pair<Int, String>> = listOf(
        250 to "250 m",
        500 to "500 m",
        1000 to "1 km",
        3000 to "3 km",
    )

    fun maxDetourMeters(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_MAX_DETOUR_M, DEFAULT_MAX_DETOUR_M)

    fun setMaxDetourMeters(context: Context, meters: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_MAX_DETOUR_M, meters)
            .apply()
    }
}
