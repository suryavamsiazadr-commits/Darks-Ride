package com.darksoul.darksride.nav

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.darksoul.darksride.AppMode
import com.darksoul.darksride.AppPrefs
import com.darksoul.darksride.link.LinkService
import com.darksoul.darksride.link.LinkState
import com.darksoul.darksride.link.LinkStatus

/**
 * "Send to bike" in Android's share menu (main phone).
 * Share a place or directions from Google Maps, or a location from WhatsApp, and the ride
 * screen starts navigation. No screen of its own: it hands the text over and closes.
 */
class ShareToBikeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = sharedText(intent)

        val message = when {
            text.isNullOrBlank() ->
                "Nothing to send. Share a place from Google Maps."
            AppPrefs.mode(this) != AppMode.MAIN ->
                "Use \"Send to bike\" on your main phone."
            LinkState.status.value == LinkStatus.CONNECTED -> {
                LinkService.sendDestination(this, text)
                "Sending to the bike…"
            }
            else -> {
                AppPrefs.setPendingNav(this, text)
                "Saved. It goes to the bike as soon as it connects."
            }
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }

    private fun sharedText(intent: Intent?): String? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(
                intent.getStringExtra(Intent.EXTRA_SUBJECT),
                intent.getStringExtra(Intent.EXTRA_TEXT)
            ).distinct().joinToString("\n")
            Intent.ACTION_VIEW -> intent.dataString // geo: and maps links
            else -> null
        }
    }
}
