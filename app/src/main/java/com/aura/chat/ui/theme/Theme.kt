package com.aura.chat.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.aura.chat.data.AppSettings

data class Accent(val name: String, val a: Color, val b: Color)

val Accents = listOf(
    Accent("Aurora", Color(0xFF7C5CFF), Color(0xFF22D3EE)),
    Accent("Ocean", Color(0xFF3B82F6), Color(0xFF2DD4BF)),
    Accent("Sunset", Color(0xFFFF7A45), Color(0xFFEC4899)),
    Accent("Emerald", Color(0xFF10B981), Color(0xFFA3E635)),
    Accent("Orchid", Color(0xFFD946EF), Color(0xFF6366F1)),
    Accent("Gold", Color(0xFFF59E0B), Color(0xFFF43F5E)),
)

data class AuraColors(
    val dark: Boolean,
    val bg: Color,
    val bgAlt: Color,
    val surface: Color,
    val text: Color,
    val textDim: Color,
    val glassTint: Color,
    val border: Color,
    val accent: Color,
    val accent2: Color,
    val danger: Color,
) {
    val gradient: Brush get() = Brush.linearGradient(listOf(accent, accent2))
}

val LocalAura = staticCompositionLocalOf<AuraColors> { error("AuraTheme missing") }
val LocalGlass = compositionLocalOf { 0.6f }

private val AuraTypography = Typography().let { t ->
    t.copy(
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold, textDirection = TextDirection.Content),
        bodyLarge = t.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp, textDirection = TextDirection.Content),
        bodyMedium = t.bodyMedium.copy(lineHeight = 21.sp, textDirection = TextDirection.Content),
    )
}

@Composable
fun AuraTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val dark = when (settings.theme) {
        1 -> true
        2 -> false
        else -> isSystemInDarkTheme()
    }
    val acc = Accents[settings.accent.coerceIn(0, Accents.lastIndex)]
    val colors = remember(dark, acc) {
        if (dark) AuraColors(
            dark = true, bg = Color(0xFF07070F), bgAlt = Color(0xFF0F0E22), surface = Color(0xFF15142B),
            text = Color(0xFFF5F5FC), textDim = Color(0xFFA6A6C4), glassTint = Color.White, border = Color.White,
            accent = acc.a, accent2 = acc.b, danger = Color(0xFFFF6B7A),
        ) else AuraColors(
            dark = false, bg = Color(0xFFF1F0FF), bgAlt = Color(0xFFE4E8FF), surface = Color(0xFFF8F8FF),
            text = Color(0xFF14142B), textDim = Color(0xFF5F5F82), glassTint = Color.White, border = Color(0xFF4B4B7C),
            accent = acc.a, accent2 = acc.b, danger = Color(0xFFD92D45),
        )
    }
    val scheme = if (dark) {
        darkColorScheme(primary = acc.a, secondary = acc.b, background = colors.bg, surface = colors.surface, onSurface = colors.text, onBackground = colors.text)
    } else {
        lightColorScheme(primary = acc.a, secondary = acc.b, background = colors.bg, surface = colors.surface, onSurface = colors.text, onBackground = colors.text)
    }

    val view = LocalView.current
    SideEffect {
        val window = view.context.findActivity()?.window ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !dark
        controller.isAppearanceLightNavigationBars = !dark
    }

    CompositionLocalProvider(
        LocalAura provides colors,
        LocalGlass provides settings.glass,
        LocalContentColor provides colors.text,
    ) {
        MaterialTheme(colorScheme = scheme, typography = AuraTypography) {
            ProvideTextStyle(MaterialTheme.typography.bodyLarge) { content() }
        }
    }
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Lets a full-screen tool with its own palette choose dark or light status/navigation bar icons. */
@Composable
fun SystemBarIcons(lightBackground: Boolean) {
    val view = LocalView.current
    val appDark = LocalAura.current.dark
    DisposableEffect(lightBackground, appDark) {
        val window = view.context.findActivity()?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = lightBackground
            controller.isAppearanceLightNavigationBars = lightBackground
        }
        onDispose {
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !appDark
                controller.isAppearanceLightNavigationBars = !appDark
            }
        }
    }
}
