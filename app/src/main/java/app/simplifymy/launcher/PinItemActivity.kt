package app.simplifymy.launcher

import android.appwidget.AppWidgetManager
import android.content.pm.LauncherApps
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.lifecycleScope
import app.simplifymy.launcher.core.workspace.Container
import app.simplifymy.launcher.core.workspace.ItemEntity
import app.simplifymy.launcher.core.workspace.ItemType
import app.simplifymy.launcher.feature.home.widgetSpan
import app.simplifymy.launcher.ui.Themed
import app.simplifymy.launcher.ui.component.SheetButtons
import app.simplifymy.launcher.ui.component.SheetKicker
import app.simplifymy.launcher.ui.component.tapCatcher
import app.simplifymy.launcher.ui.launcher.IconImage
import app.simplifymy.launcher.ui.theme.Radius
import app.simplifymy.launcher.ui.theme.RoleText
import app.simplifymy.launcher.ui.theme.Simplify
import app.simplifymy.launcher.ui.theme.Space
import app.simplifymy.launcher.ui.theme.States
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PinItemActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val launcherApps = getSystemService(LauncherApps::class.java)
        val request = launcherApps.getPinItemRequest(intent)
        if (request == null || !request.isValid) { finish(); return }
        val container = applicationContext.container
        enableEdgeToEdge()

        val label = when (request.requestType) {
            LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT ->
                (request.shortcutInfo?.shortLabel ?: request.shortcutInfo?.longLabel)?.toString().orEmpty()
            else -> request.getAppWidgetProviderInfo(this)?.loadLabel(packageManager).orEmpty()
        }

        setContent {
            Themed {
                val icon by produceState<ImageBitmap?>(null) {
                    value = withContext(Dispatchers.IO) {
                        request.shortcutInfo?.let { container.icons.shortcut(it) }
                            ?: request.getAppWidgetProviderInfo(this@PinItemActivity)
                                ?.loadPreviewImage(this@PinItemActivity, resources.displayMetrics.densityDpi)
                                ?.toBitmap()?.asImageBitmap()
                    }
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = States.SCRIM))
                        .tapCatcher { finish() },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(Radius.sheet)
                            .background(Simplify.colors.surfaceAlt)
                            .tapCatcher()
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(start = Space.screen, end = Space.screen, top = 10.dp, bottom = 22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Space.base),
                    ) {
                        Box(Modifier.width(36.dp).height(4.dp).clip(Radius.pill).background(Simplify.colors.hairline))
                        SheetKicker(stringResource(R.string.pin_kicker), Modifier)
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Space.rowGap),
                        ) {
                            IconImage(icon, 52.dp)
                            RoleText(label, Simplify.type.title, maxLines = 2)
                        }
                        SheetButtons(stringResource(R.string.action_add), ::finish, Modifier.padding(top = Space.base)) { accept(request) }
                    }
                }
            }
        }
    }

    private fun accept(request: LauncherApps.PinItemRequest) {
        val container = applicationContext.container
        val settings = container.settings.settings.value
        lifecycleScope.launch {
            val item = when (request.requestType) {
                LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT -> {
                    val info = request.shortcutInfo ?: return@launch finish()
                    if (!request.accept()) return@launch finish()
                    ItemEntity(
                        container = Container.DESKTOP, type = ItemType.SHORTCUT, packageName = info.`package`,
                        shortcutId = info.id, user = container.apps.serial(info.userHandle),
                        title = (info.shortLabel ?: info.longLabel)?.toString(),
                    )
                }
                else -> {
                    val info = request.getAppWidgetProviderInfo(this@PinItemActivity) ?: return@launch finish()
                    val id = container.widgets.allocate()
                    if (!container.widgets.bind(id, info) ||
                        !request.accept(Bundle().apply { putInt(AppWidgetManager.EXTRA_APPWIDGET_ID, id) })
                    ) {
                        container.widgets.delete(id)
                        Toast.makeText(this@PinItemActivity, R.string.error_widget, Toast.LENGTH_SHORT).show()
                        return@launch finish()
                    }
                    val metrics = resources.displayMetrics
                    val (sx, sy) = widgetSpan(
                        info, metrics.widthPixels / settings.desktopColumns.toFloat(),
                        metrics.heightPixels * 0.72f / settings.desktopRows, settings.desktopColumns, settings.desktopRows,
                    )
                    ItemEntity(
                        container = Container.DESKTOP, type = ItemType.WIDGET, spanX = sx, spanY = sy,
                        widgetId = id, provider = info.provider.flattenToString(),
                    )
                }
            }
            val pages = maxOf(settings.desktopPages, 1)
            val page = container.workspace.addToDesktop(item, settings.defaultPage, pages, settings.desktopColumns, settings.desktopRows)
            if (page >= settings.desktopPages) container.settings.update { it.copy(desktopPages = page + 1) }
            Toast.makeText(this@PinItemActivity, R.string.pin_added, Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
