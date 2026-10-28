package com.darksoul.darksride.link

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LinkStatus { STOPPED, WAITING, CONNECTING, CONNECTED }

data class LogLine(val time: String, val text: String)

/**
 * Live state of the phone-to-phone link. The service writes it, the screens read it.
 */
object LinkState {
    private val _status = MutableStateFlow(LinkStatus.STOPPED)
    val status: StateFlow<LinkStatus> = _status.asStateFlow()

    private val _peerName = MutableStateFlow<String?>(null)
    val peerName: StateFlow<String?> = _peerName.asStateFlow()

    private val _reconnects = MutableStateFlow(0)
    val reconnects: StateFlow<Int> = _reconnects.asStateFlow()

    private val _log = MutableStateFlow<List<LogLine>>(emptyList())
    val log: StateFlow<List<LogLine>> = _log.asStateFlow()

    fun setStatus(status: LinkStatus, peer: String? = null) {
        _status.value = status
        _peerName.value = if (status == LinkStatus.CONNECTED) peer else null
    }

    fun countReconnect() = _reconnects.update { it + 1 }

    fun resetReconnects() {
        _reconnects.value = 0
    }

    /** Adds a line to the on-screen activity log (newest first, last 60 kept). */
    fun log(text: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        _log.update { (listOf(LogLine(time, text)) + it).take(60) }
    }

    fun clear() {
        setStatus(LinkStatus.STOPPED)
        _reconnects.value = 0
        _log.value = emptyList()
    }
}
