package io.github.offlinemobility.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Colour tokens from docs/UI_DESIGN.md. Colour only carries meaning; everything else is neutral. */
@Immutable
data class AppColors(
    val page: Color,
    val canvas: Color,
    val surface: Color,
    val line: Color,
    val ink: Color,
    val body: Color,
    val mute: Color,
    val accent: Color,
    val accentSoft: Color,
    val urgent: Color,
    val urgentSoft: Color,
    val help: Color,
    val helpSoft: Color,
    val safe: Color,
    val safeSoft: Color,
    val onStrong: Color,
)

/*
 * Follows the Pleos Connect system screens (settings, app drawer): white and black, square corners,
 * thin grey outlines, black fill for what is selected or primary. The only colour is red, kept for
 * danger and warnings (urgent requests received, lost connection, low energy, "하지 마세요").
 * accent / safe / help-soft tokens stay as names so call sites read by meaning, but map to greys.
 */
private val Light = AppColors(
    page = Color(0xFFFFFFFF),
    canvas = Color(0xFFFFFFFF),
    surface = Color(0xFFF2F2F2),
    line = Color(0xFFC8C8C8),
    ink = Color(0xFF000000),
    body = Color(0xFF424242),
    mute = Color(0xFF767676),
    accent = Color(0xFF000000),
    accentSoft = Color(0xFFF2F2F2),
    urgent = Color(0xFFD7282F),
    urgentSoft = Color(0xFFF2F2F2),
    help = Color(0xFFD7282F),
    helpSoft = Color(0xFFF2F2F2),
    safe = Color(0xFF000000),
    safeSoft = Color(0xFFF2F2F2),
    onStrong = Color(0xFFFFFFFF),
)

private val Dark = AppColors(
    page = Color(0xFF000000),
    canvas = Color(0xFF000000),
    surface = Color(0xFF1C1C1C),
    line = Color(0xFF4A4A4A),
    ink = Color(0xFFFFFFFF),
    body = Color(0xFFD0D0D0),
    mute = Color(0xFF9E9E9E),
    accent = Color(0xFFFFFFFF),
    accentSoft = Color(0xFF1C1C1C),
    urgent = Color(0xFFFF5A5F),
    urgentSoft = Color(0xFF1C1C1C),
    help = Color(0xFFFF5A5F),
    helpSoft = Color(0xFF1C1C1C),
    safe = Color(0xFFFFFFFF),
    safeSoft = Color(0xFF1C1C1C),
    onStrong = Color(0xFF000000),
)

val LocalAppColors = staticCompositionLocalOf { Light }

/** Sizes chosen for a 160 dpi car display and drivers in their 40s–60s (title ≈ 5 mm tall). */
object Type {
    val hero = 46.sp
    val display = 36.sp
    val title = 30.sp
    val tileTitle = 32.sp
    val body = 24.sp
    val meta = 20.sp
    val button = 26.sp

    /**
     * Line height for text that can wrap. Material's default (24 sp) is shorter than most of these
     * sizes, so wrapped lines would touch or overlap.
     */
    fun lines(size: TextUnit): TextUnit = (size.value * 1.35f).sp
}

object Size {
    val primaryButton = 96.dp
    val secondaryButton = 72.dp
    val tile = 132.dp
    val sosTile = 168.dp
    /** Square corners, as on the Pleos system screens. */
    val radius = 0.dp
    val gap = 16.dp
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) Dark else Light
    val scheme = if (dark) {
        darkColorScheme(primary = colors.accent, background = colors.page, surface = colors.canvas, onSurface = colors.ink)
    } else {
        lightColorScheme(primary = colors.accent, background = colors.page, surface = colors.canvas, onSurface = colors.ink)
    }
    CompositionLocalProvider(LocalAppColors provides colors) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
