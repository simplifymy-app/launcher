package app.simplifymy.launcher.ui

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.simplifymy.launcher.container
import app.simplifymy.launcher.core.settings.Settings
import app.simplifymy.launcher.ui.theme.SimplifyTheme
import app.simplifymy.launcher.ui.theme.forScript
import app.simplifymy.launcher.util.Language
import app.simplifymy.launcher.util.Locales
import app.simplifymy.launcher.util.usesCyrillic

@Composable
fun Themed(content: @Composable (Settings) -> Unit) {
    val container = LocalContext.current.container
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val localized = rememberLocalized(settings.language)
    SimplifyTheme(
        themeMode = settings.themeMode,
        accent = settings.accent,
        customAccent = settings.customAccent?.let { Color(it) },
        contrast = settings.contrast,
        family = settings.family.forScript(localized.resources.configuration.locales[0].usesCyrillic()),
        textScale = settings.textScale,
        cornerRadius = settings.cornerRadius,
    ) {
        CompositionLocalProvider(
            LocalContext provides localized,
            LocalLocalizedContext provides localized,
            LocalConfiguration provides localized.resources.configuration,
        ) {
            content(settings)
        }
    }
}

@Composable
private fun rememberLocalized(language: Language): Context {
    val context = LocalContext.current
    return remember(context, language) { Locales.wrap(context, language) }
}

@Composable
fun SystemBars(light: Boolean, hideStatus: Boolean = false) {
    val view = LocalView.current
    val window = (view.context as? Activity)?.window ?: return
    SideEffect {
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
            if (hideStatus) hide(WindowInsetsCompat.Type.statusBars()) else show(WindowInsetsCompat.Type.statusBars())
        }
    }
}
