package app.simplifymy.launcher.feature.home

import android.appwidget.AppWidgetProviderInfo
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import app.simplifymy.launcher.R
import app.simplifymy.launcher.container
import app.simplifymy.launcher.ui.component.DetailBar
import app.simplifymy.launcher.ui.component.SectionHeader
import app.simplifymy.launcher.ui.component.pressable
import app.simplifymy.launcher.ui.theme.Radius
import app.simplifymy.launcher.ui.theme.RoleText
import app.simplifymy.launcher.ui.theme.Simplify
import app.simplifymy.launcher.ui.theme.Space
import kotlin.math.ceil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val PREVIEW_WIDTH = 112.dp
private val PREVIEW_HEIGHT = 84.dp

fun widgetSpan(info: AppWidgetProviderInfo, cellWidthPx: Float, cellHeightPx: Float, columns: Int, rows: Int): Pair<Int, Int> {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && info.targetCellWidth > 0 && info.targetCellHeight > 0) {
        return info.targetCellWidth.coerceIn(1, columns) to info.targetCellHeight.coerceIn(1, rows)
    }
    val x = ceil(info.minWidth / cellWidthPx.coerceAtLeast(1f)).toInt().coerceIn(1, columns)
    val y = ceil(info.minHeight / cellHeightPx.coerceAtLeast(1f)).toInt().coerceIn(1, rows)
    return x to y
}

@Composable
fun WidgetPicker(
    cellWidthPx: Float,
    cellHeightPx: Float,
    columns: Int,
    rows: Int,
    onPick: (AppWidgetProviderInfo) -> Unit,
    onPickBuiltin: (BuiltinWidget) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val container = context.container
    val settings by container.settings.settings.collectAsState()
    val groups by produceState(emptyList<Pair<String, List<AppWidgetProviderInfo>>>()) {
        value = withContext(Dispatchers.IO) {
            container.widgets.providers()
                .groupBy { it.provider.packageName }
                .map { (pkg, list) ->
                    val label = runCatching {
                        context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString()
                    }.getOrDefault(pkg)
                    label to list
                }
                .sortedBy { it.first.lowercase() }
        }
    }
    Column(Modifier.fillMaxSize().background(Simplify.colors.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
        DetailBar(stringResource(R.string.widgets_title), onBack, meta = "${groups.sumOf { it.second.size } + BuiltinWidget.entries.size}")
        LazyColumn(Modifier.fillMaxSize()) {
            item(key = "h:builtin") { SectionHeader(stringResource(R.string.section_simplify_widgets), count = "${BuiltinWidget.entries.size}") }
            items(BuiltinWidget.entries, key = { it.provider }) { widget ->
                PickerRow(
                    preview = {
                        CompositionLocalProvider(LocalWidgetStyle provides widgetStyle(settings, Simplify.colors.text)) {
                            BuiltinPreview(widget, cellWidthPx, cellHeightPx)
                        }
                    },
                    label = stringResource(widget.label),
                    span = "${widget.spanX} × ${widget.spanY}",
                    onClick = { onPickBuiltin(widget) },
                )
            }
            groups.forEach { (app, list) ->
                item(key = "h:$app") { SectionHeader(app, count = "${list.size}") }
                items(list, key = { it.provider.flattenToString() }) { info ->
                    val (sx, sy) = widgetSpan(info, cellWidthPx, cellHeightPx, columns, rows)
                    WidgetRow(info, "$sx × $sy") { onPick(info) }
                }
            }
        }
    }
}

@Composable
private fun BuiltinPreview(widget: BuiltinWidget, cellWidthPx: Float, cellHeightPx: Float) {
    val density = LocalDensity.current
    val width = with(density) { (cellWidthPx.coerceAtLeast(1f) * widget.spanX).toDp() }
    val height = with(density) { (cellHeightPx.coerceAtLeast(1f) * widget.spanY).toDp() }
    val scale = minOf(PREVIEW_WIDTH / width, PREVIEW_HEIGHT / height, 1f)
    BuiltinWidgetContent(widget, Modifier.requiredSize(width, height).graphicsLayer { scaleX = scale; scaleY = scale })
}

@Composable
private fun WidgetRow(info: AppWidgetProviderInfo, span: String, onClick: () -> Unit) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.densityDpi
    val preview by produceState<ImageBitmap?>(null, info.provider) {
        value = withContext(Dispatchers.IO) {
            val drawable: Drawable? = runCatching { info.loadPreviewImage(context, density) }.getOrNull()
                ?: runCatching { info.loadIcon(context, density) }.getOrNull()
            drawable?.let { d ->
                val w = d.intrinsicWidth.takeIf { it > 0 } ?: 256
                val h = d.intrinsicHeight.takeIf { it > 0 } ?: 256
                val scale = minOf(1f, 512f / maxOf(w, h))
                d.toBitmap((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1)).asImageBitmap()
            }
        }
    }
    val label = remember(info) { info.loadLabel(context.packageManager) }
    PickerRow(
        preview = { preview?.let { Image(BitmapPainter(it), null, Modifier.padding(Space.base).fillMaxSize(), contentScale = ContentScale.Fit) } },
        label = label,
        span = span,
        onClick = onClick,
    )
}

@Composable
private fun PickerRow(preview: @Composable () -> Unit, label: String, span: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(onClick, shape = RoundedCornerShape(0.dp))
            .padding(horizontal = Space.screen, vertical = Space.base),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.rowGap),
    ) {
        Box(
            Modifier.size(PREVIEW_WIDTH, PREVIEW_HEIGHT).clip(Radius.card).background(Simplify.colors.surface),
            contentAlignment = Alignment.Center,
        ) { preview() }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            RoleText(label, Simplify.type.label, maxLines = 2)
            RoleText(span, Simplify.type.meta, color = Simplify.colors.textMuted)
        }
    }
}
