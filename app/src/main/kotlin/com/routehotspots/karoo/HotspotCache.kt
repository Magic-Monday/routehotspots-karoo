package com.routehotspots.karoo

import android.content.Context
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

/**
 * Hotspots per route op schijf, zodat een route die je thuis (met wifi) één keer hebt
 * geactiveerd onderweg géén netwerk meer nodig heeft. De Karoo-firmware zet wifi
 * zelf uit zodra een rit actief wordt (gezien 9 okt 2026), dus zonder cache werkt
 * "live ophalen tijdens de rit" alleen als de rijder wifi handmatig aanzet.
 *
 * Sleutel = SHA-1 van de polyline + de gekozen categorieën + de omrij-afstand: wijzig
 * je een instelling, dan hoort er een verse set bij. Bewaartermijn 30 dagen.
 */
object HotspotCache {
    private const val DIR = "hotspot-cache"
    private const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class Entry(
        val routeName: String,
        val categories: List<String>,
        val bufferMeters: Int,
        val savedAt: Long,
        val hotspots: List<RouteHotspotsApi.Hotspot>,
    )

    fun key(routePolyline: String, categories: List<String>, bufferMeters: Int): String {
        val md = MessageDigest.getInstance("SHA-1")
        md.update(routePolyline.toByteArray())
        md.update(categories.sorted().joinToString(",").toByteArray())
        md.update(bufferMeters.toString().toByteArray())
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun load(context: Context, key: String): Entry? {
        val f = file(context, key)
        if (!f.exists()) return null
        return runCatching { json.decodeFromString(Entry.serializer(), f.readText()) }
            .onFailure { Log.w(TAG, "cache $key onleesbaar, weggooien: ${it.message}"); f.delete() }
            .getOrNull()
            ?.takeIf { System.currentTimeMillis() - it.savedAt <= MAX_AGE_MS }
    }

    fun save(context: Context, key: String, entry: Entry) {
        runCatching {
            val f = file(context, key)
            f.parentFile?.mkdirs()
            val tmp = File(f.path + ".tmp")
            tmp.writeText(json.encodeToString(Entry.serializer(), entry))
            if (!tmp.renameTo(f)) f.writeText(tmp.readText()).also { tmp.delete() }
        }.onFailure { Log.w(TAG, "cache $key niet opgeslagen: ${it.message}") }
    }

    private fun file(context: Context, key: String) = File(File(context.filesDir, DIR), "$key.json")

    private const val TAG = "RouteHotspots"
}
