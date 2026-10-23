package app.simplifymy.launcher

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.simplifymy.launcher.feature.weather.WeatherScreen
import app.simplifymy.launcher.feature.weather.WeatherView
import app.simplifymy.launcher.ui.SystemBars
import app.simplifymy.launcher.ui.Themed
import app.simplifymy.launcher.ui.theme.Simplify

class WeatherActivity : ComponentActivity() {
    companion object {
        private const val EXTRA_VIEW = "view"

        fun open(context: Context, view: WeatherView = WeatherView.Today): Intent =
            Intent(context, WeatherActivity::class.java)
                .putExtra(EXTRA_VIEW, view.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val initial = intent.getStringExtra(EXTRA_VIEW)?.let { runCatching { WeatherView.valueOf(it) }.getOrNull() } ?: WeatherView.Today
        setContent {
            Themed {
                SystemBars(Simplify.colors.isLight)
                WeatherScreen(initial, onExit = ::finish)
            }
        }
    }
}
