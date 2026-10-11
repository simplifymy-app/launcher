package app.simplifymy.launcher.feature.home

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import app.simplifymy.launcher.core.workspace.Container
import app.simplifymy.launcher.core.workspace.ItemEntity
import app.simplifymy.launcher.core.workspace.Occupancy
import kotlin.math.abs

enum class Origin { Desktop, Dock, Folder, Drawer }

data class Geometry(val bounds: Rect, val columns: Int, val rows: Int) {
    val cellWidth: Float get() = bounds.width / columns
    val cellHeight: Float get() = bounds.height / rows

    fun contains(p: Offset) = bounds.contains(p)

    fun cellRect(x: Int, y: Int, spanX: Int = 1, spanY: Int = 1) = Rect(
        bounds.left + x * cellWidth, bounds.top + y * cellHeight,
        bounds.left + (x + spanX) * cellWidth, bounds.top + (y + spanY) * cellHeight,
    )
}

enum class BarTarget { Remove, Info, Uninstall }

sealed interface DropTarget {
    data object None : DropTarget
    data class Cell(val container: Long, val page: Int, val x: Int, val y: Int) : DropTarget
    data class IntoFolder(val folder: ItemEntity) : DropTarget
    data class NewFolder(val target: ItemEntity) : DropTarget
    data class Bar(val target: BarTarget) : DropTarget
    data class Reorder(val index: Int) : DropTarget
}

class DragSession(
    val item: ItemEntity,
    val origin: Origin,
    val icon: ImageBitmap?,
    val size: Size,
    val grab: Offset,
)

@Stable
class DragController {
    var session by mutableStateOf<DragSession?>(null)
        private set
    var position by mutableStateOf(Offset.Zero)
        private set

    var moved by mutableStateOf(false)
        private set
    var target by mutableStateOf<DropTarget>(DropTarget.None)
        private set

    var desktop: Geometry? = null
    var dock: Geometry? = null
    var folderGrid: Geometry? = null
    var folderBounds: Rect? = null
    val bar = HashMap<BarTarget, Rect>()
    var slop = 24f

    private var start = Offset.Zero

    val dragging: Boolean get() = session != null
    val active: Boolean get() = session != null && moved

    fun start(session: DragSession, at: Offset) {
        this.session = session
        position = at
        start = at
        moved = false
        target = DropTarget.None
    }

    fun move(to: Offset, state: HomeState, desktopPage: Int, dockPage: Int) {
        position = to
        if (!moved && (to - start).getDistance() > slop) moved = true
        if (moved) target = resolve(state, desktopPage, dockPage)
    }

    fun end(): Pair<DragSession, DropTarget>? {
        val s = session ?: return null
        val result = s to (if (moved) target else DropTarget.None)
        session = null
        moved = false
        target = DropTarget.None
        return result
    }

    val insideFolder: Boolean get() = folderBounds?.contains(position) == true

    private fun resolve(state: HomeState, desktopPage: Int, dockPage: Int): DropTarget {
        val s = session ?: return DropTarget.None
        bar.entries.firstOrNull { it.value.contains(position) }?.let { return DropTarget.Bar(it.key) }
        if (s.origin == Origin.Folder && insideFolder) {
            val grid = folderGrid ?: return DropTarget.None
            val col = ((position.x - grid.bounds.left) / grid.cellWidth).toInt().coerceIn(0, grid.columns - 1)
            val row = ((position.y - grid.bounds.top) / grid.cellHeight).toInt().coerceAtLeast(0)
            return DropTarget.Reorder(row * grid.columns + col)
        }
        val center = position - s.grab + Offset(s.size.width / 2, s.size.height / 2)
        dock?.takeIf { it.contains(position) && state.settings.dockEnabled && !s.item.isWidget }?.let { geo ->
            return cellTarget(geo, Container.DOCK, dockPage, state.dock.filter { it.page == dockPage }, s.item, center)
        }
        desktop?.takeIf { it.contains(position) && desktopPage >= 0 }?.let { geo ->
            return cellTarget(geo, Container.DESKTOP, desktopPage, state.desktop[desktopPage].orEmpty(), s.item, center)
        }
        return DropTarget.None
    }

    private fun cellTarget(
        geo: Geometry,
        container: Long,
        page: Int,
        items: List<ItemEntity>,
        dragged: ItemEntity,
        center: Offset,
    ): DropTarget {
        val anchor = if (dragged.isLaunchable) position else center
        val fx = (anchor.x - geo.bounds.left) / geo.cellWidth
        val fy = (anchor.y - geo.bounds.top) / geo.cellHeight
        val x = (fx - dragged.spanX / 2f + 0.5f).toInt().coerceIn(0, geo.columns - dragged.spanX)
        val y = (fy - dragged.spanY / 2f + 0.5f).toInt().coerceIn(0, (geo.rows - dragged.spanY).coerceAtLeast(0))
        val others = items.filter { it.id != dragged.id }
        if (dragged.isLaunchable) {
            val cx = fx.toInt().coerceIn(0, geo.columns - 1)
            val cy = fy.toInt().coerceIn(0, geo.rows - 1)
            val occupant = others.firstOrNull { cx in it.x until it.x + it.spanX && cy in it.y until it.y + it.spanY }

            val nearMiddle = abs(fx - cx - 0.5f) < 0.22f && abs(fy - cy - 0.5f) < 0.22f
            if (occupant != null && nearMiddle) {
                if (occupant.isFolder) return DropTarget.IntoFolder(occupant)
                if (occupant.isLaunchable) return DropTarget.NewFolder(occupant)
            }
        }
        val occupancy = Occupancy.of(others, geo.columns, geo.rows)
        if (occupancy.fits(x, y, dragged.spanX, dragged.spanY)) return DropTarget.Cell(container, page, x, y)
        return occupancy.nearest(x, y, dragged.spanX, dragged.spanY)
            ?.let { (nx, ny) -> DropTarget.Cell(container, page, nx, ny) }
            ?: DropTarget.None
    }
}
