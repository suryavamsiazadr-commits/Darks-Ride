package com.darksoul.darksride.phone

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject

/** One message shown on the ride screen. */
data class RideNotif(
    val id: String,
    val app: String,
    val title: String,
    val text: String,
    val time: Long
)

/** Ride screen side of notifications: recent messages and the current pop-up. */
object RideNotifications {
    private const val KEEP = 8

    private val _items = MutableStateFlow<List<RideNotif>>(emptyList())
    val items: StateFlow<List<RideNotif>> = _items.asStateFlow()

    private val _popup = MutableStateFlow<RideNotif?>(null)
    val popup: StateFlow<RideNotif?> = _popup.asStateFlow()

    fun add(msg: JSONObject) {
        val n = RideNotif(
            id = msg.optString("id"),
            app = msg.optString("app"),
            title = msg.optString("title"),
            text = msg.optString("text"),
            time = msg.optLong("time", System.currentTimeMillis())
        )
        _items.update { list -> (listOf(n) + list.filterNot { it.id == n.id }).take(KEEP) }
        _popup.value = n
    }

    /** Cleared on the main phone (read or swiped away). */
    fun remove(id: String) {
        _items.update { list -> list.filterNot { it.id == id } }
        if (_popup.value?.id == id) _popup.value = null
    }

    fun dismissPopup(id: String) {
        if (_popup.value?.id == id) _popup.value = null
    }

    fun clear() {
        _items.value = emptyList()
        _popup.value = null
    }
}
