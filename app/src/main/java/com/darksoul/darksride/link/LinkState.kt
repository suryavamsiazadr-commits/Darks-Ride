package com.darksoul.darksride.link

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.io.File
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

    // ---------- Activity log: permanent, kept in a file on this phone, never cleared ----------

    private const val LOG_FILE = "activity.log"
    /** Lines shown on screen (newest first). The file keeps more. */
    private const val SHOW = 500
    /** When the file grows past this it's trimmed to the newest SHOW lines... */
    private const val FILE_MAX = 5_000

    private var logFile: File? = null
    private val pending = mutableListOf<String>()

    /** Called once when the app starts: loads the saved log. */
    @Synchronized
    fun init(c: Context) {
        if (logFile != null) return
        val f = File(c.filesDir, LOG_FILE)
        logFile = f
        val saved = runCatching { if (f.exists()) f.readLines() else emptyList() }.getOrDefault(emptyList())
        // Lines logged before init (if any) go after the saved ones.
        runCatching { if (pending.isNotEmpty()) f.appendText(pending.joinToString("\n", postfix = "\n")) }
        val all = saved + pending
        pending.clear()
        if (all.size > FILE_MAX) runCatching { f.writeText(all.takeLast(FILE_MAX).joinToString("\n", postfix = "\n")) }
        _log.value = all.takeLast(SHOW).mapNotNull { parse(it) }.reversed()
    }

    /** Adds a line to the activity log (newest first on screen, saved to the file). */
    @Synchronized
    fun log(text: String) {
        val time = SimpleDateFormat("dd/MM HH:mm:ss", Locale.US).format(Date())
        val clean = text.replace('\n', ' ')
        _log.update { (listOf(LogLine(time, clean)) + it).take(SHOW) }
        val raw = "$time\t$clean"
        val f = logFile
        if (f == null) pending += raw else runCatching { f.appendText(raw + "\n") }
    }

    private fun parse(raw: String): LogLine? {
        val i = raw.indexOf('\t')
        return if (i <= 0) null else LogLine(raw.substring(0, i), raw.substring(i + 1))
    }

    /** Link reset (e.g. changing role). The activity log is kept. */
    fun clear() {
        setStatus(LinkStatus.STOPPED)
        _reconnects.value = 0
        log("Phone role reset")
    }
}
