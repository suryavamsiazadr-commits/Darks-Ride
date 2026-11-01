package com.darksoul.darksride.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shown once after a crash: what went wrong, with Copy / Share to send it for a fix. */
@Composable
fun CrashReportScreen(report: String, onContinue: () -> Unit) {
    val context = LocalContext.current
    RideColumn {
        Title("Dark's Ride stopped")
        Body("It crashed the last time it ran. Copy or share this report so it can be fixed, " +
            "then tap Continue.", dim = true)
        BigButton("Copy report", onClick = {
            context.getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText("Dark's Ride crash", report))
            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
        })
        BigButton("Share report", secondary = true, onClick = {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, "Dark's Ride crash")
                .putExtra(Intent.EXTRA_TEXT, report)
            context.startActivity(Intent.createChooser(send, "Share crash report"))
        })
        BigButton("Continue", secondary = true, onClick = onContinue)
        Card {
            Text(
                report, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.heightIn(max = 2000.dp).horizontalScroll(rememberScrollState())
            )
        }
    }
}
