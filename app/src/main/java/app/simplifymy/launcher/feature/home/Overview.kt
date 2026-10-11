package app.simplifymy.launcher.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.simplifymy.launcher.R
import app.simplifymy.launcher.core.workspace.ItemEntity
import app.simplifymy.launcher.core.workspace.Occupancy
import app.simplifymy.launcher.ui.component.ButtonKind
import app.simplifymy.launcher.ui.component.IconAction
import app.simplifymy.launcher.ui.component.Icons
import app.simplifymy.launcher.ui.component.SimplifyButton
import app.simplifymy.launcher.ui.component.pressable
import app.simplifymy.launcher.ui.launcher.pageLabel
import app.simplifymy.launcher.ui.theme.Radius
import app.simplifymy.launcher.ui.theme.RoleText
import app.simplifymy.launcher.ui.theme.Simplify
import app.simplifymy.launcher.ui.theme.Space
import kotlin.math.roundToInt

@Composable
fun OverviewControls(
    page: Int,
    pages: Int,
    isDefault: Boolean,
    onAddPage: () -> Unit,
    onRemovePage: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onSetDefault: () -> Unit,
    onWidgets: () -> Unit,
    onWallpaper: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Space.screen, vertical = Space.base),
        verticalArrangement = Arrangement.spacedBy(Space.base),
    ) {
        Row(
            Modifier.fillMaxWidth().clip(Radius.control).background(Simplify.colors.surfaceAlt).padding(horizontal = Space.gutter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconAction(Icons.Back, onMoveLeft, enabled = page > 0, label = stringResource(R.string.page_move_left))
            RoleText(
                pageLabel(page, pages), Simplify.type.value, color = Simplify.colors.textMuted,
                modifier = Modifier.weight(1f).padding(horizontal = Space.base),
            )
            IconAction(
                Icons.Home, onSetDefault,
                tint = if (isDefault) Simplify.colors.accent else Simplify.colors.text,
                label = stringResource(R.string.page_set_default),
            )
            IconAction(Icons.Trash, onRemovePage, enabled = pages > 1, tint = Simplify.colors.danger, label = stringResource(R.string.page_remove))
            IconAction(Icons.Plus, onAddPage, label = stringResource(R.string.page_add))
            IconAction(Icons.ChevronRight, onMoveRight, enabled = page < pages - 1, label = stringResource(R.string.page_move_right))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.base)) {
            SimplifyButton(stringResource(R.string.overview_widgets), onWidgets, Modifier.weight(1f))
            SimplifyButton(stringResource(R.string.overview_wallpaper), onWallpaper, Modifier.weight(1f))
            SimplifyButton(stringResource(R.string.overview_settings), onSettings, Modifier.weight(1f), ButtonKind.Primary)
        }
    }
}

private enum class Edge { Left, Top, Right, Bottom }

@Composable
fun ResizeFrame(
    item: ItemEntity,
    geometry: Geometry,
    neighbours: List<ItemEntity>,
    onChange: (ItemEntity) -> Unit,
    onCommit: (ItemEntity) -> Unit,
) {
    var frame by remember(item.id) { mutableStateOf(item) }
    val density = LocalDensity.current
    val occupancy = remember(neighbours, item.id) {
        Occupancy.of(neighbours, geometry.columns, geometry.rows, except = item.id)
    }
    Box(
        Modifier
            .fillMaxSize()
            .pressable({ onCommit(frame) }, shape = RoundedCornerShape(0.dp), role = null)
    ) {
        val rect = geometry.cellRect(frame.x, frame.y, frame.spanX, frame.spanY)
        with(density) {
            Box(
                Modifier
                    .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
                    .size(rect.width.toDp(), rect.height.toDp())
                    .border(2.dp, Simplify.colors.accent, Radius.control)
            )
        }
        Edge.entries.forEach { edge ->
            val anchor = when (edge) {
                Edge.Left -> Offset(rect.left, rect.center.y)
                Edge.Right -> Offset(rect.right, rect.center.y)
                Edge.Top -> Offset(rect.center.x, rect.top)
                Edge.Bottom -> Offset(rect.center.x, rect.bottom)
            }
            val handle = with(density) { Space.hitTarget.toPx() }
            var accumulated by remember(edge, item.id) { mutableStateOf(0f) }
            Box(
                Modifier
                    .offset { IntOffset((anchor.x - handle / 2).roundToInt(), (anchor.y - handle / 2).roundToInt()) }
                    .size(Space.hitTarget)
                    .pointerInput(edge) {
                        detectDragGestures(onDragEnd = { accumulated = 0f }) { change, drag ->
                            change.consume()
                            accumulated += if (edge == Edge.Left || edge == Edge.Right) drag.x else drag.y
                            val cell = if (edge == Edge.Left || edge == Edge.Right) geometry.cellWidth else geometry.cellHeight
                            val steps = (accumulated / cell).roundToInt()
                            if (steps == 0) return@detectDragGestures
                            val next = resized(frame, edge, steps)
                            if (next.spanX >= 1 && next.spanY >= 1 && occupancy.fits(next.x, next.y, next.spanX, next.spanY)) {
                                frame = next
                                onChange(next)
                                accumulated -= steps * cell
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(14.dp).clip(Radius.pill).background(Simplify.colors.accent))
            }
        }
    }
}

private fun resized(item: ItemEntity, edge: Edge, steps: Int): ItemEntity = when (edge) {
    Edge.Left -> item.copy(x = item.x + steps, spanX = item.spanX - steps)
    Edge.Right -> item.copy(spanX = item.spanX + steps)
    Edge.Top -> item.copy(y = item.y + steps, spanY = item.spanY - steps)
    Edge.Bottom -> item.copy(spanY = item.spanY + steps)
}
