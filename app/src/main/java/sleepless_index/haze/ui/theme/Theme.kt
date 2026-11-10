package sleepless_index.haze.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import sleepless_index.haze.data.Prefs
import sleepless_index.haze.data.ThemeMode
import sleepless_index.haze.util.findActivity

/** Keeps the accent readable: lighter on dark themes, darker on the light theme. */
fun adjustAccent(c: Color, dark: Boolean): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(c.toArgb(), hsl)
    hsl[2] = if (dark) hsl[2].coerceAtLeast(0.55f) else hsl[2].coerceAtMost(0.42f)
    return Color(ColorUtils.HSLToColor(hsl))
}

@Composable
private fun animated(c: Color): Color =
    animateColorAsState(targetValue = c, animationSpec = tween(300), label = "themeColor").value

@Composable
fun GalleryTheme(prefs: Prefs, content: @Composable () -> Unit) {
    val dark = prefs.theme != ThemeMode.LIGHT
    val amoled = prefs.theme == ThemeMode.AMOLED

    val accentTarget = remember(prefs.accent, dark) { adjustAccent(Color(prefs.accent), dark) }
    val accent = animated(accentTarget)
    val onAccent = if (accent.luminance() > 0.45f) Color(0xFF0A0A0A) else Color.White

    val bg = animated(
        when {
            amoled -> Color.Black
            dark -> Color(0xFF121212)
            else -> Color(0xFFF7F7F8)
        }
    )
    val surface = animated(
        when {
            amoled -> Color(0xFF0F0F0F)
            dark -> Color(0xFF1C1C1E)
            else -> Color.White
        }
    )
    val surfaceVariant = animated(
        when {
            amoled -> Color(0xFF1A1A1A)
            dark -> Color(0xFF2A2A2C)
            else -> Color(0xFFECECEE)
        }
    )
    val onBg = animated(if (dark) Color(0xFFF4F4F5) else Color(0xFF111113))
    val muted = animated(if (dark) Color(0xFF8A8A8E) else Color(0xFF6B6B70))
    val outline = animated(if (dark) Color(0xFF2A2A2A) else Color(0xFFDADADE))

    val scheme = if (dark) {
        darkColorScheme(
            primary = accent,
            onPrimary = onAccent,
            background = bg,
            onBackground = onBg,
            surface = surface,
            onSurface = onBg,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = muted,
            outline = outline,
            outlineVariant = outline,
            surfaceContainerLowest = bg,
            surfaceContainerLow = surface,
            surfaceContainer = surface,
            surfaceContainerHigh = surfaceVariant,
            surfaceContainerHighest = surfaceVariant,
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = onAccent,
            background = bg,
            onBackground = onBg,
            surface = surface,
            onSurface = onBg,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = muted,
            outline = outline,
            outlineVariant = outline,
            surfaceContainerLowest = bg,
            surfaceContainerLow = surface,
            surfaceContainer = surface,
            surfaceContainerHigh = surfaceVariant,
            surfaceContainerHighest = surfaceVariant,
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
            }
        }
    }

    // Fixed-width digits everywhere, so sizes, counts and timers don't jitter as they change.
    MaterialTheme(colorScheme = scheme) {
        androidx.compose.material3.ProvideTextStyle(
            androidx.compose.material3.LocalTextStyle.current.merge(
                androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
            ),
            content = content,
        )
    }
}
