package app.simplifymy.launcher.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

@Immutable
data class SimplifyColors(
    val background: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val hairline: Color,
    val text: Color,
    val textMuted: Color,
    val textFaint: Color,
    val accent: Color,
    val onAccent: Color,
    val danger: Color,
    val isLight: Boolean,
)

enum class ThemeMode { System, Light, Dark, Black }

enum class Contrast(val shift: Float) { Soft(-0.12f), Normal(0f), High(0.16f) }

enum class AccentPreset(val darkFill: Color, val lightFill: Color) {
    Indigo(Color(0xFF6E8BFF), Color(0xFF4A5FE0)),
    Amber(Color(0xFFE0A458), Color(0xFF8F5A12)),
    Jade(Color(0xFF5FBF8F), Color(0xFF12764E)),
    Rose(Color(0xFFD96A8B), Color(0xFFA62F55)),
    Violet(Color(0xFFA98BFF), Color(0xFF6541D6)),
}

private val DarkInk = Color(0xFF0B0B0C)
private val LightInk = Color(0xFFFFFFFF)

private val DangerDark = Color(0xFFFF7A7A)
private val DangerLight = Color(0xFFC22B2B)

private val Dark = SimplifyColors(
    background = Color(0xFF0B0B0C),
    surface = Color(0xFF141416),
    surfaceAlt = Color(0xFF1C1C1F),
    hairline = Color(0xFF2A2A2E),
    text = Color(0xFFEDEDEF),
    textMuted = Color(0xFF8A8A92),
    textFaint = Color(0xFF5A5A62),
    accent = AccentPreset.Indigo.darkFill,
    onAccent = DarkInk,
    danger = DangerDark,
    isLight = false,
)

private val Black = Dark.copy(
    background = Color(0xFF000000),
    surface = Color(0xFF0C0C0D),
    surfaceAlt = Color(0xFF161618),
    hairline = Color(0xFF232326),
)

private val Light = SimplifyColors(
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFF5F5F6),
    surfaceAlt = Color(0xFFEDEDEF),
    hairline = Color(0xFFE3E3E6),
    text = Color(0xFF131315),
    textMuted = Color(0xFF5E5E66),
    textFaint = Color(0xFF74747C),
    accent = AccentPreset.Indigo.lightFill,
    onAccent = LightInk,
    danger = DangerLight,
    isLight = true,
)

val FullscreenBackground = Color(0xFF000000)
val FullscreenPanel = Color(0xFF1C1C1F)
val FullscreenMuted = Color(0xFF8A8A92)

private fun shift(role: Color, colors: SimplifyColors, amount: Float): Color = when {
    amount == 0f -> role
    amount < 0f -> lerp(role, colors.background, -amount)
    else -> lerp(role, if (colors.isLight) Color.Black else Color.White, amount)
}

fun simplifyColors(dark: Boolean, black: Boolean, accent: AccentPreset, custom: Color?, contrast: Contrast): SimplifyColors {
    val base = if (!dark) Light else if (black) Black else Dark
    val fill = custom ?: if (base.isLight) accent.lightFill else accent.darkFill
    val withAccent = base.copy(accent = fill, onAccent = if (base.isLight) LightInk else DarkInk)
    if (contrast == Contrast.Normal) return withAccent
    return withAccent.copy(
        text = shift(withAccent.text, withAccent, contrast.shift),
        textMuted = shift(withAccent.textMuted, withAccent, contrast.shift),
        textFaint = shift(withAccent.textFaint, withAccent, contrast.shift),
    )
}
