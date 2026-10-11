package app.simplifymy.launcher.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

val LocalLocalizedContext = staticCompositionLocalOf<Context?> { null }

@Composable
fun Localized(content: @Composable () -> Unit) {
    val localized = LocalLocalizedContext.current
    if (localized == null) {
        content()
    } else {
        CompositionLocalProvider(
            LocalContext provides localized,
            LocalConfiguration provides localized.resources.configuration,
            content = content,
        )
    }
}
