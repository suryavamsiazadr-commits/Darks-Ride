package com.darksoul.darksride

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * If Dark's Ride ever crashes, the reason is saved on the phone and shown the next time the
 * app opens (with Copy / Share), so it can be fixed without a cable or a computer.
 */
object CrashCatcher {
    private const val FILE = "last_crash.txt"

    fun install(c: Context) {
        val app = c.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
                val version = runCatching {
                    app.packageManager.getPackageInfo(app.packageName, 0).versionName
                }.getOrNull()
                val head = "Dark's Ride $version · ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}\n" +
                    SimpleDateFormat("dd MMM yyyy HH:mm:ss", Locale.US).format(Date()) + " · thread ${thread.name}\n\n"
                File(app.filesDir, FILE).writeText(head + trace)
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** The saved report from the last crash, if any. */
    fun lastCrash(c: Context): String? =
        File(c.filesDir, FILE).takeIf { it.exists() }?.readText()

    fun clear(c: Context) {
        File(c.filesDir, FILE).delete()
    }
}
