package app.simplifymy.launcher.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import app.simplifymy.launcher.core.settings.FolderStyle
import app.simplifymy.launcher.core.workspace.ItemEntity
import app.simplifymy.launcher.ui.Localized
import app.simplifymy.launcher.ui.component.DetailBar
import app.simplifymy.launcher.ui.component.IconAction
import app.simplifymy.launcher.ui.component.Icons
import app.simplifymy.launcher.ui.component.pressable
import app.simplifymy.launcher.ui.component.tapCatcher
import app.simplifymy.launcher.ui.launcher.LocalLabelColor
import app.simplifymy.launcher.ui.launcher.rootRect
import app.simplifymy.launcher.ui.theme.RoleText
import app.simplifymy.launcher.ui.theme.Simplify
import app.simplifymy.launcher.ui.theme.Space

private val PanelShape = RoundedCornerShape(10.dp)
private val PopupMaxWidth = 360.dp
private val CellHeight = 92.dp

@Composable
fun FolderPanel(
    folder: ItemEntity?,
    anchor: Rect,
    state: HomeState,
    host: ItemHost,
    onRename: (ItemEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    val drag = host.drag
    val draggingOut = drag.active && drag.session?.origin == Origin.Folder && !drag.insideFolder
    if (folder != null) {
        val children = state.folders[folder.id].orEmpty()
        val columns = state.settings.folderColumns
        FolderWindow(
            title = state.label(folder),
            count = children.size,
            style = state.settings.folderStyle,
            anchor = anchor,
            onDismiss = onDismiss,
            onTitle = { onRename(folder) },
            modifier = Modifier.alpha(if (draggingOut) 0f else 1f),
        ) {
            val rows = ((children.size + columns - 1) / columns).coerceAtLeast(1)
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .height(CellHeight * rows)
                    .padding(horizontal = Space.base)
                    .onGloballyPositioned {
                        drag.folderBounds = it.rootRect()
                        drag.folderGrid = Geometry(it.rootRect(), columns, rows)
                    }
            ) {
                val target = drag.target
                val dragged = drag.session?.item
                val order = if (target is DropTarget.Reorder && dragged != null && children.any { it.id == dragged.id }) {
                    reordered(children, dragged, target.index)
                } else children
                val placed = order.mapIndexed { i, c -> c.copy(x = i % columns, y = i / columns) }
                CompositionLocalProvider(LocalLabelColor provides Simplify.colors.text) {
                    Localized {
                        CellGrid(columns, rows, placed, Modifier.width(maxWidth).height(CellHeight * rows)) { child ->
                            HomeItem(child, state, host, Origin.Folder, state.settings.desktopIconSize, true)
                        }
                    }
                }
            }
        }
    }
    if (folder == null) {
        drag.folderBounds = null
        drag.folderGrid = null
    }
}

fun reordered(children: List<ItemEntity>, dragged: ItemEntity, index: Int): List<ItemEntity> {
    val list = children.filter { it.id != dragged.id }.toMutableList()
    list.add(index.coerceIn(0, list.size), dragged)
    return list
}

@Composable
fun FolderWindow(
    title: String,
    count: Int,
    style: FolderStyle,
    anchor: Rect,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onTitle: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().tapCatcher(onDismiss))
        when (style) {
            FolderStyle.FullScreen -> Column(
                Modifier
                    .fillMaxSize()
                    .background(Simplify.colors.surface)
                    .tapCatcher()
                    .windowInsetsPadding(WindowInsets.safeDrawing),
            ) {
                DetailBar(title, onDismiss, meta = "$count") {
                    if (onTitle != null) IconAction(Icons.Edit, onTitle)
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), content = content)
            }
            FolderStyle.Popup -> NearAnchor(anchor) {
                Column(
                    Modifier
                        .widthIn(max = PopupMaxWidth)
                        .fillMaxWidth()
                        .clip(PanelShape)
                        .background(Simplify.colors.surfaceAlt)
                        .tapCatcher()
                        .padding(vertical = Space.rowGap),
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .then(if (onTitle != null) Modifier.pressable(onTitle, shape = RoundedCornerShape(0.dp)) else Modifier)
                            .padding(horizontal = Space.screen, vertical = Space.base),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RoleText(title, Simplify.type.title, modifier = Modifier.weight(1f), maxLines = 1)
                        RoleText("$count", Simplify.type.meta, color = Simplify.colors.textFaint)
                    }
                    content()
                }
            }
        }
    }
}

@Composable
private fun NearAnchor(anchor: Rect, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val insets = WindowInsets.safeDrawing
    val margin = with(density) { Space.screen.roundToPx() }
    val gap = with(density) { Space.base.roundToPx() }
    Layout(content, Modifier.fillMaxSize()) { measurables, constraints ->
        val top = insets.getTop(density) + margin
        val bottom = constraints.maxHeight - insets.getBottom(density) - margin
        val panel = measurables.first().measure(
            Constraints(maxWidth = (constraints.maxWidth - 2 * margin).coerceAtLeast(0), maxHeight = (bottom - top).coerceAtLeast(0))
        )
        layout(constraints.maxWidth, constraints.maxHeight) {
            val maxX = (constraints.maxWidth - panel.width - margin).coerceAtLeast(margin)
            val maxY = (bottom - panel.height).coerceAtLeast(top)
            if (anchor == Rect.Zero) {
                panel.place((constraints.maxWidth - panel.width) / 2, (top + bottom - panel.height) / 2)
            } else {
                val x = (anchor.center.x - panel.width / 2f).toInt().coerceIn(margin, maxX)
                val above = anchor.top.toInt() - panel.height - gap
                val y = if (above >= top) above else (anchor.bottom.toInt() + gap).coerceIn(top, maxY)
                panel.place(x, y)
            }
        }
    }
}
