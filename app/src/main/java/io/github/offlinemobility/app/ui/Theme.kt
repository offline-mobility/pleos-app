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

private val Light = AppColors(
    page = Color(0xFFF7F8FA),
    canvas = Color(0xFFFFFFFF),
    surface = Color(0xFFF1F3F5),
    line = Color(0xFFE3E6EB),
    ink = Color(0xFF17191E),
    body = Color(0xFF474C55),
    mute = Color(0xFF8B919C),
    accent = Color(0xFF2B5CE6),
    accentSoft = Color(0xFFEBF0FD),
    urgent = Color(0xFFE5484D),
    urgentSoft = Color(0xFFFDECEC),
    help = Color(0xFFE8890C),
    helpSoft = Color(0xFFFDF1E1),
    safe = Color(0xFF2F9A6A),
    safeSoft = Color(0xFFE7F4ED),
    onStrong = Color(0xFFFFFFFF),
)

private val Dark = AppColors(
    page = Color(0xFF111317),
    canvas = Color(0xFF1B1E24),
    surface = Color(0xFF252931),
    line = Color(0xFF323741),
    ink = Color(0xFFF2F4F7),
    body = Color(0xFFC9CED6),
    mute = Color(0xFF8B919C),
    accent = Color(0xFF5B83F0),
    accentSoft = Color(0xFF1E2A4A),
    urgent = Color(0xFFF2555A),
    urgentSoft = Color(0xFF3A1F22),
    help = Color(0xFFF09A2A),
    helpSoft = Color(0xFF3A2A16),
    safe = Color(0xFF3DB07D),
    safeSoft = Color(0xFF1A3228),
    onStrong = Color(0xFFFFFFFF),
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
}

object Size {
    val primaryButton = 96.dp
    val secondaryButton = 72.dp
    val tile = 132.dp
    val sosTile = 168.dp
    val radius = 16.dp
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
