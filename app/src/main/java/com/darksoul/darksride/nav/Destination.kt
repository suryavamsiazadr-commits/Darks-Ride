package com.darksoul.darksride.nav

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Main phone: turns text shared from Google Maps (or a location link from WhatsApp) into a
 * destination the ride screen can navigate to.
 *
 * Google Maps shares "Place name / address / https://maps.app.goo.gl/...". The short link is
 * followed (needs internet) to find the exact coordinates; if that fails, the place name and
 * address are used as a search, and as a last resort the link itself is opened.
 *
 * Must run off the main thread.
 */
object Destination {
    private val URL_RE = Regex("""https?://\S+""")

    fun build(sharedText: String): JSONObject {
        val url = URL_RE.find(sharedText)?.value
        val lines = sharedText.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !URL_RE.containsMatchIn(it) }
            .distinct()
        val name = lines.firstOrNull() ?: "Destination"
        val query = lines.joinToString(", ")

        val coords = coordinatesIn(sharedText)
            ?: url?.let { followLink(it) }?.let { coordinatesIn(it) }

        return JSONObject().put("t", "nav").put("name", name).apply {
            if (query.isNotEmpty()) put("query", query)
            if (url != null) put("url", url)
            if (coords != null) {
                put("lat", coords.first)
                put("lng", coords.second)
            }
        }
    }

    /** Finds latitude/longitude in a Google Maps URL or text. */
    fun coordinatesIn(s: String): Pair<Double, Double>? {
        // Place pages: ...!3d12.9716!4d77.5946
        Regex("""!3d(-?\d+\.\d+)!4d(-?\d+\.\d+)""").findAll(s).lastOrNull()?.let {
            return it.groupValues[1].toDouble() to it.groupValues[2].toDouble()
        }
        // Directions: the last stop is the destination, written ...!1d<lng>!2d<lat>
        Regex("""!1d(-?\d+\.\d+)!2d(-?\d+\.\d+)""").findAll(s).lastOrNull()?.let {
            return it.groupValues[2].toDouble() to it.groupValues[1].toDouble()
        }
        // Links like ?q=12.97,77.59 or &destination=12.97,77.59
        Regex("""[?&](?:q|query|destination|daddr|ll)=(-?\d+\.\d+),\s*(-?\d+\.\d+)""").find(s)?.let {
            return it.groupValues[1].toDouble() to it.groupValues[2].toDouble()
        }
        // geo:12.97,77.59 links (0,0 means "search the text instead")
        Regex("""geo:(-?\d+\.\d+),(-?\d+\.\d+)""").find(s)?.let {
            val lat = it.groupValues[1].toDouble()
            val lng = it.groupValues[2].toDouble()
            if (lat != 0.0 || lng != 0.0) return lat to lng
        }
        // Map view centre: .../@12.97,77.59,15z (least precise, used last)
        Regex("""@(-?\d+\.\d+),(-?\d+\.\d+)""").find(s)?.let {
            return it.groupValues[1].toDouble() to it.groupValues[2].toDouble()
        }
        return null
    }

    /** Follows a short link's redirects and returns the full URL (null if offline). */
    private fun followLink(start: String): String? {
        var current = start
        repeat(6) {
            val next = runCatching {
                val conn = URL(current).openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 5_000
                conn.readTimeout = 5_000
                conn.requestMethod = "GET"
                try {
                    if (conn.responseCode in 300..399) conn.getHeaderField("Location") else null
                } finally {
                    conn.disconnect()
                }
            }.getOrNull() ?: return current
            current = if (next.startsWith("/")) URL(URL(current), next).toString() else next
            if (coordinatesIn(current) != null) return current
        }
        return current
    }
}
