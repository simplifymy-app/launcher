package app.simplifymy.launcher

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.simplifymy.launcher.feature.settings.Page
import app.simplifymy.launcher.feature.settings.SettingsScreen
import app.simplifymy.launcher.ui.SystemBars
import app.simplifymy.launcher.ui.Themed
import app.simplifymy.launcher.ui.theme.Simplify
import app.simplifymy.launcher.util.AppIcon
import app.simplifymy.launcher.util.AppIcons
import app.simplifymy.launcher.util.IconTarget

class SettingsActivity : ComponentActivity() {
    companion object {
        private const val EXTRA_PAGE = "page"

        fun open(context: Context, page: Page): Intent =
            Intent(context, SettingsActivity::class.java).putExtra(EXTRA_PAGE, page.name)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Themed { settings ->
                SystemBars(Simplify.colors.isLight)
                ApplyIconsOnLeave(settings.appIcon, settings.weatherIcon)
                SettingsScreen(initial = intent.getStringExtra(EXTRA_PAGE)?.let { runCatching { Page.valueOf(it) }.getOrNull() } ?: Page.Root, onExit = ::finish)
            }
        }
    }
}

@Composable
private fun ApplyIconsOnLeave(launcher: AppIcon, weather: AppIcon) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, launcher, weather) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                AppIcons.apply(context, IconTarget.Launcher, launcher)
                AppIcons.apply(context, IconTarget.Weather, weather)
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}
