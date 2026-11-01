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
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
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
    scroll: ScrollState? = null,
    background: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(if (background) Modifier.rideBackground() else Modifier)
            .windowInsetsPadding(insets),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                // Wide screens (landscape): keep pages a comfortable reading width, centred.
                .widthIn(max = 720.dp)
                .fillMaxSize()
                .verticalScroll(scroll ?: rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content
        )
    }
}

/** The page background: gradient, soft glow and stars (dark) or dust (light). */
@Composable
internal fun Modifier.rideBackground(): Modifier {
    val glass = LocalGlass.current
    if (glass.soft) return this.background(glass.card) // neumorphism: one calm colour
    return this
        .background(Brush.verticalGradient(listOf(glass.backgroundTop, glass.backgroundBottom)))
        .ambientGlow(glass.ambient)
        .starfield(glass.isDark, glass.speck)
}

// ---------- Surfaces: glass or soft (neumorphism) ----------

/**
 * Neumorphic lift: the shape in the page colour with a light shadow to the top left and a
 * dark one to the bottom right, so it looks pressed up out of the page.
 */
internal fun Modifier.neu(
    corner: Dp,
    elevation: Dp,
    base: Color,
    light: Color,
    dark: Color,
    circle: Boolean = false
): Modifier = drawBehind {
    val e = elevation.toPx()
    val r = if (circle) minOf(size.width, size.height) / 2f else corner.toPx()
    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = base.toArgb()
        }
        paint.setShadowLayer(e * 1.6f, -e * 0.7f, -e * 0.7f, light.toArgb())
        canvas.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, r, r, paint)
        paint.setShadowLayer(e * 1.6f, e * 0.7f, e * 0.7f, dark.toArgb())
        canvas.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, r, r, paint)
    }
}

/**
 * A card's surface in the current style.
 * Glass: see-through with a bright top edge. Soft: raised out of the page.
 * [tint] washes the card in a colour (green for calls, theme colour for new messages),
 * [edge] draws a coloured outline (something needs attention).
 */
@Composable
internal fun Modifier.rideSurface(corner: Dp = 22.dp, tint: Color? = null, edge: Color? = null): Modifier {
    val glass = LocalGlass.current
    val shape = RoundedCornerShape(corner)
    return if (glass.soft) {
        this
            .neu(corner, 7.dp, glass.card, glass.softLight, glass.softDark)
            .clip(shape)
            .background(glass.card)
            .then(if (tint != null) Modifier.background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.16f), Color.Transparent))) else Modifier)
            .then(if (edge != null) Modifier.border(2.dp, edge, shape) else Modifier)
    } else {
        this
            .then(
                if (glass.cardShadow.alpha > 0f) Modifier.shadow(10.dp, shape, ambientColor = glass.cardShadow, spotColor = glass.cardShadow)
                else Modifier
            )
            .clip(shape)
            .background(glass.card)
            .then(if (tint != null) Modifier.background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.2f), Color.Transparent))) else Modifier)
            .background(Brush.verticalGradient(0f to glass.cardHighlight, 0.45f to Color.Transparent))
            .then(
                if (edge != null) Modifier.border(if (edge.alpha >= 0.9f) 2.dp else 1.dp, edge, shape)
                else Modifier.border(1.dp, Brush.verticalGradient(listOf(glass.cardBorderTop, glass.cardBorderBottom)), shape)
            )
    }
}

/** A round or pill button's body: frosted (glass) or raised (soft). [fill] for coloured ones. */
@Composable
internal fun Modifier.rideButtonSurface(circle: Boolean, fill: Brush? = null, corner: Dp = 50.dp): Modifier {
    val glass = LocalGlass.current
    val colors = MaterialTheme.colorScheme
    val shape = if (circle) CircleShape else RoundedCornerShape(corner)
    return if (glass.soft) {
        this
            .neu(corner, 5.dp, glass.card, glass.softLight, glass.softDark, circle = circle)
            .clip(shape)
            .background(fill ?: Brush.linearGradient(listOf(glass.card, glass.card)))
    } else if (fill != null) {
        this.clip(shape).background(fill)
    } else {
        this.clip(shape)
            .background(colors.onSurface.copy(alpha = 0.09f))
            .border(1.dp, colors.onSurface.copy(alpha = 0.12f), shape)
    }
}

/** Which cards are open, for this visit only: every card starts closed when the app opens. */
object CardMemory {
    private val open = mutableMapOf<String, Boolean>()
    fun get(key: String) = open[key] ?: false
    fun set(key: String, value: Boolean) { open[key] = value }
    /** The app was opened afresh: close everything. */
    fun reset() = open.clear()
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

/** Stars at night; in daylight the same specks, darker and fainter, like dust on glass. */
internal fun Modifier.starfield(isDark: Boolean, color: Color = Color.White): Modifier = drawBehind {
    val strength = if (isDark) 1f else 0.45f
    STARS.forEach { s ->
        drawCircle(
            color = color.copy(alpha = s.alpha * strength),
            radius = s.radiusDp * density,
            center = Offset(s.x * size.width, s.y * size.height)
        )
    }
}

/** A soft warm light from the top corner (light theme), and a cooler echo at the bottom. */
private fun Modifier.ambientGlow(color: Color): Modifier =
    if (color.alpha == 0f) this else drawBehind {
        drawRect(
            Brush.radialGradient(
                listOf(color, Color.Transparent),
                center = Offset(size.width * 0.1f, 0f),
                radius = size.width * 1.1f
            )
        )
        drawRect(
            Brush.radialGradient(
                listOf(color.copy(alpha = color.alpha * 0.5f), Color.Transparent),
                center = Offset(size.width, size.height),
                radius = size.width * 0.9f
            )
        )
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
        LinkStatus.WAITING -> (if (mode == AppMode.RIDE) "Waiting" else "Looking") to colors.primary
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
    val glass = LocalGlass.current
    if (glass.soft) {
        // Soft: raised pill in the page colour; main buttons filled with the theme colour.
        val colors = MaterialTheme.colorScheme
        Box(
            modifier = modifier
                .rideButtonSurface(circle = false, corner = 16.dp,
                    fill = if (secondary) null else Brush.verticalGradient(listOf(colors.primary, glass.accent2)))
                .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Text(text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                color = when {
                    !enabled -> colors.onSurface.copy(alpha = 0.35f)
                    secondary -> colors.primary
                    else -> colors.onPrimary
                })
        }
        return
    }
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
internal fun Card(
    onClick: (() -> Unit)? = null,
    accent: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    var modifier = Modifier.fillMaxWidth().rideSurface(22.dp, edge = accent)
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
    /** Shown open with a coloured edge whatever the saved state (e.g. a new message). */
    highlight: Boolean = false,
    /** Something here needs a look: a small dot beside the title while closed. */
    attention: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    // Every card starts closed when the app opens; it stays as you leave it until then.
    var open by remember(stateKey) { mutableStateOf(CardMemory.get(stateKey)) }
    val colors = MaterialTheme.colorScheme
    val shown = open || highlight
    Card(accent = if (highlight) colors.primary else null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    open = !open
                    CardMemory.set(stateKey, open)
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                        color = titleColor ?: colors.onSurface
                    )
                    if (attention && !shown) {
                        Box(Modifier.padding(start = 8.dp).size(9.dp).clip(CircleShape).background(colors.primary))
                    }
                }
                if (!shown && !summary.isNullOrEmpty()) {
                    Text(
                        summary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = colors.onSurface.copy(alpha = 0.65f)
                    )
                }
            }
            trailing?.invoke(shown)
            Text(
                if (shown) "▾" else "▸", fontSize = 24.sp, color = colors.primary,
                modifier = Modifier.padding(start = 10.dp)
            )
        }
        AnimatedVisibility(visible = shown) {
            Column(
                modifier = Modifier.padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                content()
            }
        }
    }
}
