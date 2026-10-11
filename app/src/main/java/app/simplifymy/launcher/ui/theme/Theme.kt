package app.simplifymy.launcher.ui.theme

import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

val LocalSimplifyColors = compositionLocalOf<SimplifyColors> { error("No SimplifyColors") }
val LocalSimplifyTypography = compositionLocalOf<SimplifyTypography> { error("No SimplifyTypography") }
val LocalCornerRadius = staticCompositionLocalOf { CornerRadius.M }
val LocalReduceMotion: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { false }

object Simplify {
    val colors: SimplifyColors
        @Composable @ReadOnlyComposable get() = LocalSimplifyColors.current
    val type: SimplifyTypography
        @Composable @ReadOnlyComposable get() = LocalSimplifyTypography.current
    val radius: CornerRadius
        @Composable @ReadOnlyComposable get() = LocalCornerRadius.current
}

@Composable
fun SimplifyTheme(
    themeMode: ThemeMode = ThemeMode.System,
    accent: AccentPreset = AccentPreset.Indigo,
    customAccent: Color? = null,
    contrast: Contrast = Contrast.Normal,
    family: InterfaceFamily = InterfaceFamily.SpaceGrotesk,
    textScale: TextScale = TextScale.M,
    cornerRadius: CornerRadius = CornerRadius.M,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) {
        ThemeMode.System -> systemDark
        ThemeMode.Light -> false
        ThemeMode.Dark, ThemeMode.Black -> true
    }
    val target = simplifyColors(dark, themeMode == ThemeMode.Black, accent, customAccent, contrast)
    val reduceMotion = rememberReduceMotion()

    val colors = target.animated(reduceMotion)
    val typography = remember(family, textScale) { simplifyTypography(family, textScale) }

    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.accent, onPrimary = colors.onAccent,
            background = colors.background, onBackground = colors.text,
            surface = colors.surface, onSurface = colors.text,
            surfaceVariant = colors.surfaceAlt, onSurfaceVariant = colors.textMuted,
            outline = colors.hairline, outlineVariant = colors.hairline,
            error = colors.danger, onError = colors.onAccent,
            scrim = Color.Black,
        )
    } else {
        lightColorScheme(
            primary = colors.accent, onPrimary = colors.onAccent,
            background = colors.background, onBackground = colors.text,
            surface = colors.surface, onSurface = colors.text,
            surfaceVariant = colors.surfaceAlt, onSurfaceVariant = colors.textMuted,
            outline = colors.hairline, outlineVariant = colors.hairline,
            error = colors.danger, onError = colors.onAccent,
            scrim = Color.Black,
        )
    }

    CompositionLocalProvider(
        LocalSimplifyColors provides colors,
        LocalSimplifyTypography provides typography,
        LocalCornerRadius provides cornerRadius,
        LocalReduceMotion provides reduceMotion,
        LocalTextStyle provides typography.body.copy(color = colors.text),
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

@Composable
private fun SimplifyColors.animated(reduceMotion: Boolean): SimplifyColors {
    if (reduceMotion) return this
    val spec = tween<Color>(Motion.TOKEN, easing = Motion.enter)
    @Composable
    fun a(c: Color) = animateColorAsState(c, spec, label = "token").value
    return copy(
        background = a(background), surface = a(surface), surfaceAlt = a(surfaceAlt),
        hairline = a(hairline), text = a(text), textMuted = a(textMuted),
        textFaint = a(textFaint), accent = a(accent), onAccent = a(onAccent), danger = a(danger),
    )
}

@Composable
private fun rememberReduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

@Composable
fun RoleText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Simplify.colors.text,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    textAlign: TextAlign? = null,
) = Text(text, modifier, color, style = style, maxLines = maxLines, overflow = overflow, textAlign = textAlign)
