package com.darksoul.darksride.nav

import android.content.Context
import android.location.Geocoder
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.Locale

/**
 * Main phone: turns text shared from Google Maps (or a location link from WhatsApp) into a
 * destination the ride screen can navigate to.
 *
 * What Google Maps shares:
 *  - a place:  "Place name / address / https://maps.app.goo.gl/..."
 *  - a route:  "Shared route / From My location to Nandi Hills via NH 44. / steps... / link"
 *
 * The real destination name comes from the "to ..." part, then the place lines, then the full
 * link (followed over the internet: /place/<name>/ or the last stop of /dir/...), and as a last
 * resort the phone's address lookup on the coordinates.
 *
 * Must run off the main thread.
 */
object Destination {
    private val URL_RE = Regex("""https?://\S+""")
    private val ROUTE_RE = Regex("""(?i)^from\s+(.+?)\s+to\s+(.+?)(?:\s+via\s+(.+?))?\.?$""")

    /** Lines Google Maps adds that are not a place name. */
    private fun isNoise(line: String): Boolean {
        val l = line.lowercase(Locale.ROOT)
        return l == "shared route" || l == "directions" || l == "route" ||
            l.startsWith("for the best route") || l.startsWith("check traffic") ||
            l.startsWith("shared from") || l.startsWith("see ") ||
            Regex("""^\d+\.\s""").containsMatchIn(line) // turn-by-turn steps
    }

    fun build(sharedText: String, context: Context? = null): JSONObject {
        val url = URL_RE.find(sharedText)?.value
        val rawLines = sharedText.lines().map { it.trim().trim('"') }.filter { it.isNotEmpty() }

        // A shared route: "From A to B via C."
        val route = rawLines.firstNotNullOfOrNull { ROUTE_RE.find(it) }
        val routeTo = route?.groupValues?.get(2)?.trim()
        val routeFrom = route?.groupValues?.get(1)?.trim()
        val routeVia = route?.groupValues?.get(3)?.trim()?.trimEnd('.')?.ifEmpty { null }

        val placeLines = rawLines
            .filter { !URL_RE.containsMatchIn(it) && !isNoise(it) && ROUTE_RE.find(it) == null }
            .distinct()

        val fullUrl = url?.let { followLink(it) }
        val coords = coordinatesIn(sharedText) ?: fullUrl?.let { coordinatesIn(it) }

        var name: String? = routeTo ?: placeLines.firstOrNull() ?: fullUrl?.let { nameInUrl(it) }
        var address: String? = if (routeTo == null) placeLines.drop(1).joinToString(", ").ifEmpty { null } else null

        // Still missing a name or an area: ask the phone what's at those coordinates.
        if ((name == null || address == null) && coords != null && context != null) {
            lookUp(context, coords.first, coords.second)?.let { (place, area) ->
                if (name == null) name = place
                if (address == null) address = area
            }
        }
        val finalName = name ?: "Destination"
        val query = listOfNotNull(finalName, address).joinToString(", ")

        return JSONObject().put("t", "nav").put("name", finalName).apply {
            put("query", query)
            address?.let { put("address", it) }
            routeVia?.let { put("via", it) }
            routeFrom?.let { put("from", it) }
            if (url != null) put("url", url)
            if (coords != null) {
                put("lat", coords.first)
                put("lng", coords.second)
            }
        }
    }

    /** Name from a full Google Maps link: /place/<name>/..., the last stop of /dir/..., or ?q=<text>. */
    internal fun nameInUrl(u: String): String? {
        fun clean(s: String) = runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)
            .replace('+', ' ').trim().takeIf { it.isNotEmpty() && !it.looksLikeCoordinates() && !it.isMyLocation() }

        Regex("""/maps/place/([^/@?]+)""").find(u)?.let { m -> clean(m.groupValues[1])?.let { return it } }
        Regex("""/maps/dir/([^@?]+)""").find(u)?.let { m ->
            m.groupValues[1].split('/').filter { it.isNotBlank() && !it.startsWith("data=") }
                .lastOrNull()?.let { s -> clean(s)?.let { return it } }
        }
        Regex("""[?&](?:q|query|destination|daddr)=([^&]+)""").find(u)?.let { m ->
            clean(m.groupValues[1])?.let { return it }
        }
        return null
    }

    private fun String.looksLikeCoordinates() = Regex("""^-?\d+\.\d+,\s*-?\d+\.\d+$""").matches(this)
    private fun String.isMyLocation() = lowercase(Locale.ROOT).let { it == "my location" || it == "current location" }

    /** The phone's own address lookup: (place name, area). Null if offline or nothing found. */
    @Suppress("DEPRECATION") // the blocking call is fine here: we're already off the main thread
    private fun lookUp(c: Context, lat: Double, lng: Double): Pair<String?, String?>? = runCatching {
        if (!Geocoder.isPresent()) return null
        val a = Geocoder(c, Locale.getDefault()).getFromLocation(lat, lng, 1)?.firstOrNull() ?: return null
        val place = a.featureName?.takeUnless { it.matches(Regex("""^[\d\s,+\-A-Z]{1,12}$""")) } // skip plus codes / numbers
            ?: a.thoroughfare ?: a.subLocality
        val area = listOfNotNull(a.subLocality, a.locality, a.adminArea).distinct()
            .filterNot { it == place }.joinToString(", ").ifEmpty { null }
        place to area
    }.getOrNull()

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
            // Stop once we have both a place and its coordinates.
            if (coordinatesIn(current) != null && nameInUrl(current) != null) return current
        }
        return current
    }
}
