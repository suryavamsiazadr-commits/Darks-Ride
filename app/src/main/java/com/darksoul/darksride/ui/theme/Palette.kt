package com.darksoul.darksride.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * The four ride themes (Settings → Appearance). Each has a bright accent for the dark theme,
 * a deeper one that stays readable on white in daylight, and a second colour for gradients.
 */
enum class RidePalette(
    val label: String,
    val tagline: String,
    val dark: Color,
    val light: Color,
    val second: Color
) {
    EMBER("Ember", "Warm orange, the original", Color(0xFFFF7A1A), Color(0xFFD9570A), Color(0xFFFFC14D)),
    REDLINE("Redline", "Deep red, kindest to night eyes", Color(0xFFFF3B3B), Color(0xFFC81E1E), Color(0xFFFF8A5C)),
    GLACIER("Glacier", "Ice blue, crisp and calm", Color(0xFF3EC6FF), Color(0xFF0072B5), Color(0xFF7C8CFF)),
    VOLT("Volt", "Hi-vis lime, pops in bright sun", Color(0xFFC6FF1A), Color(0xFF4C7D00), Color(0xFF1AFFB2));

    /** As an Android colour int (for the floating cards). */
    fun argb(isDark: Boolean): Int = (if (isDark) dark else light).toArgb()
    fun secondArgb(): Int = second.toArgb()
}
