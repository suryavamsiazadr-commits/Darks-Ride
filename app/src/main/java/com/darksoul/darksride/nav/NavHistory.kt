package com.darksoul.darksride.nav

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Recent destinations, kept on each phone: on the bike phone the ones received (tap to
 * navigate again), on the main phone the ones sent (tap to send again). Newest first.
 */
object NavHistory {
    private const val FILE = "nav_history.json"
    private const val MAX = 20

    private val _items = MutableStateFlow<List<RideDestination>>(emptyList())
    val items: StateFlow<List<RideDestination>> = _items.asStateFlow()
    private var loaded = false

    fun load(c: Context) {
        if (loaded) return
        loaded = true
        val f = File(c.filesDir, FILE)
        if (!f.exists()) return
        _items.value = runCatching {
            val a = JSONArray(f.readText())
            (0 until a.length()).map { RideDestination.fromJson(a.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun add(c: Context, d: RideDestination) {
        load(c)
        // The same place again moves to the top instead of being listed twice.
        val rest = _items.value.filterNot { it.samePlace(d) }
        _items.value = (listOf(d) + rest).take(MAX)
        save(c)
    }

    fun clear(c: Context) {
        _items.value = emptyList()
        loaded = true
        save(c)
    }

    private fun save(c: Context) {
        File(c.filesDir, FILE).writeText(JSONArray(_items.value.map { it.toJson() }).toString())
    }
}

/** A destination: from "Send to bike" on the main phone, or picked again from history. */
data class RideDestination(
    val name: String,
    val query: String?,
    val url: String?,
    val lat: Double?,
    val lng: Double?,
    /** When it was sent (millis). */
    val sentAt: Long = System.currentTimeMillis(),
    /** Area or street, when known. */
    val area: String? = null,
    /** For a shared route: the road Google Maps chose ("via NH 44") and where it started. */
    val via: String? = null,
    val from: String? = null
) {
    /** The message the bike understands ({"t":"nav", ...}). */
    fun toJson(): JSONObject = JSONObject().put("t", "nav").put("name", name).put("sentAt", sentAt).apply {
        query?.let { put("query", it) }
        url?.let { put("url", it) }
        area?.let { put("address", it) }
        via?.let { put("via", it) }
        from?.let { put("from", it) }
        if (lat != null && lng != null) {
            put("lat", lat)
            put("lng", lng)
        }
    }

    /** The area or address, without the repeated place name. */
    val address: String?
        get() = area ?: query?.removePrefix(name)?.trim(',', ' ')?.ifEmpty { null }

    fun samePlace(other: RideDestination): Boolean =
        name == other.name && query == other.query && lat == other.lat && lng == other.lng

    companion object {
        fun fromJson(o: JSONObject) = RideDestination(
            name = o.optString("name").ifEmpty { "Destination" },
            query = o.optString("query").ifEmpty { null },
            url = o.optString("url").ifEmpty { null },
            lat = if (o.has("lat")) o.optDouble("lat") else null,
            lng = if (o.has("lng")) o.optDouble("lng") else null,
            sentAt = o.optLong("sentAt", System.currentTimeMillis()),
            area = o.optString("address").ifEmpty { null },
            via = o.optString("via").ifEmpty { null },
            from = o.optString("from").ifEmpty { null }
        )
    }
}
