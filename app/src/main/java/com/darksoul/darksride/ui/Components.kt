package com.darksoul.darksride.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import kotlin.random.Random
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.darksoul.darksride.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darksoul.darksride.AppMode
import com.darksoul.darksride.AppPrefs
import com.darksoul.darksride.link.LinkStatus
import com.darksoul.darksride.ui.theme.Go
import com.darksoul.darksride.ui.theme.LocalGlass

// ---------- Page ----------

@Composable
fun RideColumn(
    insets: WindowInsets = WindowInsets.safeDrawing,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(LocalGlass.current.backgroundTop, LocalGlass.current.backgroundBottom))
            )
            .starfield(LocalGlass.current.isDark)
            .windowInsetsPadding(insets)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content
    )
}

/** A faint field of tiny stars behind the dark theme: there if you look, never in the way. */
private class Star(val x: Float, val y: Float, val radiusDp: Float, val alpha: Float)

private val STARS: List<Star> = Random(450).let { r ->
    List(150) {
        val bright = r.nextFloat() < 0.06f
        Star(
            x = r.nextFloat(),
            y = r.nextFloat(),
            radiusDp = if (bright) 1.1f + r.nextFloat() * 0.5f else 0.45f + r.nextFloat() * 0.5f,
            alpha = if (bright) 0.32f + r.nextFloat() * 0.15f else 0.08f + r.nextFloat() * 0.16f
        )
    }
}

internal fun Modifier.starfield(enabled: Boolean): Modifier =
    if (!enabled) this else drawBehind {
        STARS.forEach { s ->
            drawCircle(
                color = Color.White.copy(alpha = s.alpha),
                radius = s.radiusDp * density,
                center = Offset(s.x * size.width, s.y * size.height)
            )
        }
    }

/** Screen title with the connection status in a small box beside it. */
@Composable
internal fun Header(
    title: String,
    status: LinkStatus?,
    peer: String?,
    mode: AppMode,
    onSettings: (() -> Unit)? = null
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title, fontSize = 26.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (status != null) StatusPill(status, peer, mode)
        if (onSettings != null) {
            IconButton(onClick = onSettings) {
                Icon(
                    painterResource(R.drawable.ic_settings), contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }
        }
    }
}

/** App name and version, small and quiet. */
@Composable
internal fun VersionLabel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: ""
    }
    Text(
        "Dark's Ride v$version", fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
internal fun StatusPill(status: LinkStatus, peer: String?, mode: AppMode) {
    val colors = MaterialTheme.colorScheme
    val (label, dot) = when (status) {
        LinkStatus.CONNECTED -> (peer ?: "Connected") to Go
        LinkStatus.CONNECTING -> "Connecting…" to colors.primary
        LinkStatus.WAITING ->
            (if (mode == AppMode.RIDE) "Waiting for phone" else "Looking for bike") to colors.primary
        LinkStatus.STOPPED -> "Link off" to Color.Gray
    }
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .padding(start = 12.dp)
            .clip(shape)
            .background(LocalGlass.current.card)
            .border(1.dp, dot.copy(alpha = 0.6f), shape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
        Text(
            label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

// ---------- Buttons and text ----------

@Composable
internal fun BigButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    secondary: Boolean = false
) {
    val modifier = Modifier
        .fillMaxWidth()
        .height(56.dp)
    val shape = RoundedCornerShape(16.dp)
    if (secondary) {
        OutlinedButton(onClick = onClick, enabled = enabled, shape = shape, modifier = modifier) {
            Text(text, fontSize = 17.sp)
        }
    } else {
        // Main buttons get a soft orange glow underneath.
        val glow = LocalGlass.current.glow
        Button(
            onClick = onClick, enabled = enabled, shape = shape,
            modifier = modifier.shadow(
                elevation = if (enabled) 14.dp else 0.dp, shape = shape,
                ambientColor = glow, spotColor = glow
            )
        ) {
            Text(text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
internal fun Title(text: String) = Text(
    text, fontSize = 28.sp, fontWeight = FontWeight.Bold,
    color = MaterialTheme.colorScheme.primary
)

@Composable
internal fun Heading(text: String) = Text(
    text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
    color = MaterialTheme.colorScheme.onBackground
)

@Composable
internal fun Body(text: String, dim: Boolean = false) = Text(
    text, fontSize = 16.sp,
    color = MaterialTheme.colorScheme.onBackground.copy(alpha = if (dim) 0.65f else 1f)
)

// ---------- Cards ----------

@Composable
internal fun Card(onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    val glass = LocalGlass.current
    // Glass-lite: translucent body, light catching the top, a thin edge brighter at the top.
    var modifier = Modifier
        .fillMaxWidth()
        .clip(shape)
        .background(glass.card)
        .background(Brush.verticalGradient(0f to glass.cardHighlight, 0.45f to Color.Transparent))
        .border(1.dp, Brush.verticalGradient(listOf(glass.cardBorderTop, glass.cardBorderBottom)), shape)
    if (onClick != null) modifier = modifier.clickable(onClick = onClick)
    Column(
        modifier = modifier.padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content
    )
}

/**
 * A card that opens and closes when its title row is tapped.
 * By default it remembers whether it was open (per [stateKey]).
 *
 * @param summary one line shown under the title while the card is closed
 * @param trailing extra content at the end of the title row, told whether the card is open
 */
@Composable
internal fun CollapsibleCard(
    title: String,
    stateKey: String,
    defaultOpen: Boolean = true,
    persist: Boolean = true,
    titleColor: Color? = null,
    summary: String? = null,
    trailing: (@Composable (open: Boolean) -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val context = LocalContext.current
    var open by remember(stateKey, if (persist) Unit else defaultOpen) {
        mutableStateOf(if (persist) AppPrefs.cardOpen(context, stateKey, defaultOpen) else defaultOpen)
    }
    val colors = MaterialTheme.colorScheme
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    open = !open
                    if (persist) AppPrefs.setCardOpen(context, stateKey, open)
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                    color = titleColor ?: colors.onSurface
                )
                if (!open && !summary.isNullOrEmpty()) {
                    Text(
                        summary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = colors.onSurface.copy(alpha = 0.65f)
                    )
                }
            }
            trailing?.invoke(open)
            Text(
                if (open) "▾" else "▸", fontSize = 24.sp, color = colors.primary,
                modifier = Modifier.padding(start = 10.dp)
            )
        }
        AnimatedVisibility(visible = open) {
            Column(
                modifier = Modifier.padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                content()
            }
        }
    }
}
