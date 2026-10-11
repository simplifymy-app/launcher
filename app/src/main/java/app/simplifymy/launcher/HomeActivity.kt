package app.simplifymy.launcher

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import app.simplifymy.launcher.core.settings.Orientation
import app.simplifymy.launcher.core.workspace.Container
import app.simplifymy.launcher.core.workspace.ItemEntity
import app.simplifymy.launcher.core.workspace.ItemType
import app.simplifymy.launcher.feature.home.HomeScreen
import app.simplifymy.launcher.feature.home.HomeViewModel
import app.simplifymy.launcher.feature.home.WidgetRequester
import app.simplifymy.launcher.ui.Themed
import app.simplifymy.launcher.ui.containerViewModel

class HomeActivity : ComponentActivity() {
    private val container get() = applicationContext.container
    private var pending: PendingWidget? = null
    private var viewModel: HomeViewModel? = null

    private data class PendingWidget(val id: Int, val info: AppWidgetProviderInfo, val page: Int, val spanX: Int, val spanY: Int)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Themed { settings ->
                val vm = containerViewModel { HomeViewModel(it) }
                viewModel = vm
                LaunchedEffect(settings.orientation) {
                    requestedOrientation = when (settings.orientation) {
                        Orientation.Auto -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        Orientation.Portrait -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        Orientation.Landscape -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    }
                }
                HomeScreen(vm, WidgetRequester(::requestWidget))
            }
        }
    }

    override fun onStart() {
        super.onStart()
        runCatching { container.widgets.host.startListening() }
    }

    override fun onStop() {
        super.onStop()
        runCatching { container.widgets.host.stopListening() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            container.homePressed.tryEmit(Unit)
        }
    }

    private fun requestWidget(info: AppWidgetProviderInfo, page: Int, spanX: Int, spanY: Int) {
        val widgets = container.widgets
        val id = widgets.allocate()
        pending = PendingWidget(id, info, page, spanX, spanY)
        if (widgets.bind(id, info)) {
            configure()
        } else {
            @Suppress("DEPRECATION")
            startActivityForResult(
                Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile),
                REQUEST_BIND,
            )
        }
    }

    private fun configure() {
        val widget = pending ?: return
        if (widget.info.configure == null) return place()
        runCatching {
            container.widgets.host.startAppWidgetConfigureActivityForResult(this, widget.id, 0, REQUEST_CONFIGURE, null)
        }.onFailure { place() }
    }

    private fun place() {
        val widget = pending ?: return
        pending = null
        val vm = viewModel ?: return
        val item = ItemEntity(
            container = Container.DESKTOP, type = ItemType.WIDGET, spanX = widget.spanX, spanY = widget.spanY,
            widgetId = widget.id, provider = widget.info.provider.flattenToString(),
        )
        vm.place(item, widget.page)
    }

    private fun abandon() {
        pending?.let { container.widgets.delete(it.id) }
        pending = null
    }

    @Deprecated("The widget host API still reports through onActivityResult.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_BIND -> if (resultCode == RESULT_OK) configure() else abandon()
            REQUEST_CONFIGURE -> if (resultCode == RESULT_OK) place() else abandon()
        }
    }

    private companion object {
        const val REQUEST_BIND = 11
        const val REQUEST_CONFIGURE = 12
    }
}
