package app.simplifymy.launcher.feature.home

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.simplifymy.launcher.R
import app.simplifymy.launcher.container
import app.simplifymy.launcher.core.settings.DockBackground
import app.simplifymy.launcher.core.settings.PageIndicator
import app.simplifymy.launcher.core.settings.ScrollEffect
import app.simplifymy.launcher.core.widgets.LauncherWidgetHostView
import app.simplifymy.launcher.core.workspace.Container
import app.simplifymy.launcher.core.workspace.ItemEntity
import app.simplifymy.launcher.core.workspace.ItemFlags
import app.simplifymy.launcher.ui.component.Icon
import app.simplifymy.launcher.ui.component.Icons
import app.simplifymy.launcher.ui.component.pressable
import app.simplifymy.launcher.ui.launcher.BoundsRef
import app.simplifymy.launcher.ui.launcher.FolderIcon
import app.simplifymy.launcher.ui.launcher.IconCell
import app.simplifymy.launcher.ui.launcher.InkOnDark
import app.simplifymy.launcher.ui.launcher.InkOnLight
import app.simplifymy.launcher.ui.launcher.ItemIcon
import app.simplifymy.launcher.ui.launcher.LocalLabelColor
import app.simplifymy.launcher.ui.launcher.iconSize
import app.simplifymy.launcher.ui.launcher.pageLabel
import app.simplifymy.launcher.ui.launcher.rememberItemIcon
import app.simplifymy.launcher.ui.launcher.rootRect
import app.simplifymy.launcher.ui.launcher.trackBounds
import app.simplifymy.launcher.ui.theme.Motion
import app.simplifymy.launcher.ui.theme.Radius
import app.simplifymy.launcher.ui.theme.RoleText
import app.simplifymy.launcher.ui.theme.Simplify
import app.simplifymy.launcher.ui.theme.Space
import app.simplifymy.launcher.ui.theme.States
import app.simplifymy.launcher.ui.theme.enterTween
import kotlin.math.abs

interface ItemHost {
    val drag: DragController
    fun launch(item: ItemEntity, bounds: Rect)
    fun openFolder(item: ItemEntity, bounds: Rect)
    fun startDrag(item: ItemEntity, origin: Origin, icon: ImageBitmap?, bounds: Rect, at: Offset)
    fun dragTo(at: Offset)
    fun endDrag(cancelled: Boolean)
}

@Composable
fun CellGrid(
    columns: Int,
    rows: Int,
    items: List<ItemEntity>,
    modifier: Modifier = Modifier,
    content: @Composable (ItemEntity) -> Unit,
) {
    BoxWithConstraints(modifier) {
        val cw = maxWidth / columns
        val ch = maxHeight / rows
        items.forEach { item ->
            key(item.id) {
                Box(
                    Modifier
                        .offset(cw * item.x, ch * item.y)
                        .size(cw * item.spanX, ch * item.spanY),
                    contentAlignment = Alignment.Center,
                ) { content(item) }
            }
        }
    }
}

private fun Modifier.ghostIf(hidden: Boolean) = alpha(if (hidden) 0f else 1f)

@Composable
fun HomeItem(
    item: ItemEntity,
    state: HomeState,
    host: ItemHost,
    origin: Origin,
    iconPercent: Int,
    showLabel: Boolean,
    highlighted: Boolean = false,
) {
    val size = iconSize(iconPercent)
    val hidden = host.drag.active && host.drag.session?.item?.id == item.id && item.id != 0L
    val bounds = remember { BoundsRef() }
    val locked = state.settings.lockDesktop && origin != Origin.Folder
    when {
        item.isWidget -> BuiltinWidget.of(item.provider)
            ?.let { BuiltinWidgetItem(it, item, host, Modifier.ghostIf(hidden), locked) }
            ?: WidgetItem(item, host, Modifier.ghostIf(hidden), locked)
        item.isFolder -> {
            val children = state.folders[item.id].orEmpty()
            val cover = item.has(ItemFlags.SWIPE_TO_OPEN)
            IconCell(
                state.label(item),
                showLabel,
                state.settings.twoLineLabels,
                Modifier
                    .ghostIf(hidden)
                    .trackBounds(bounds)
                    .itemGestures(
                        key = item.id,
                        onTap = {
                            if (cover && children.isNotEmpty()) host.launch(children.first(), bounds.rect)
                            else host.openFolder(item, bounds.rect)
                        },
                        onLongPress = { at -> if (!locked) host.startDrag(item, origin, null, bounds.rect, at) },
                        onDrag = host::dragTo,
                        onDragEnd = host::endDrag,
                    ),
            ) {
                FolderIcon(children, state, size, cover, highlighted)
            }
        }
        else -> {
            val icon by rememberItemIcon(item, state.iconRef(item))
            IconCell(
                state.label(item),
                showLabel,
                state.settings.twoLineLabels,
                Modifier
                    .ghostIf(hidden)
                    .graphicsLayer { if (highlighted) { scaleX = 1.12f; scaleY = 1.12f } }
                    .trackBounds(bounds)
                    .itemGestures(
                        key = item.id,
                        onTap = { host.launch(item, bounds.rect) },
                        onLongPress = { at -> if (!locked || origin == Origin.Folder) host.startDrag(item, origin, icon, bounds.rect, at) },
                        onDrag = host::dragTo,
                        onDragEnd = host::endDrag,
                    ),
            ) {
                ItemIcon(item, state, size)
            }
        }
    }
}

@Composable
private fun WidgetItem(item: ItemEntity, host: ItemHost, modifier: Modifier, locked: Boolean) {
    val container = LocalContext.current.container
    val composeView = LocalView.current
    val density = LocalDensity.current
    val bounds = remember { BoundsRef() }
    fun rootOf(rawX: Float, rawY: Float): Offset {
        val loc = IntArray(2).also(composeView::getLocationOnScreen)
        return Offset(rawX - loc[0], rawY - loc[1])
    }
    AndroidView(
        factory = { context ->
            container.widgets.view(context, item.widgetId)
        },
        update = { view ->
            if (view is LauncherWidgetHostView) {
                view.editable = !locked
                view.onLongPress = { x, y -> host.startDrag(item, Origin.Desktop, null, bounds.rect, rootOf(x, y)) }
                view.onDragMove = { x, y -> host.dragTo(rootOf(x, y)) }
                view.onDragEnd = { cancelled -> host.endDrag(cancelled) }
            }
        },
        modifier = modifier
            .fillMaxSize()
            .padding(4.dp)
            .onGloballyPositioned {
                bounds.rect = it.rootRect()
                container.widgets.resized(item.widgetId, with(density) { it.size.width.toDp().value.toInt() }, with(density) { it.size.height.toDp().value.toInt() })
            },
    )
}

private fun Modifier.scrollEffect(effect: ScrollEffect, offset: Float): Modifier = graphicsLayer {
    val o = offset.coerceIn(-1f, 1f)
    when (effect) {
        ScrollEffect.Simple -> Unit
        ScrollEffect.Cube -> {
            cameraDistance = 12f * density
            transformOrigin = TransformOrigin(if (o < 0) 0f else 1f, 0.5f)
            rotationY = -o * 90f
        }
        ScrollEffect.Stack -> if (o > 0) {
            translationX = o * size.width
            val s = 1f - 0.2f * o
            scaleX = s; scaleY = s
            alpha = 1f - o
        }
        ScrollEffect.Zoom -> {
            val s = 1f - 0.15f * abs(o)
            scaleX = s; scaleY = s
            alpha = 1f - 0.5f * abs(o)
        }
        ScrollEffect.Fade -> alpha = 1f - abs(o)
        ScrollEffect.Tablet -> {
            cameraDistance = 12f * density
            rotationY = -o * 30f
        }
    }
}

@Composable
fun DesktopPager(
    state: HomeState,
    pager: PagerState,
    host: ItemHost,
    realPage: (Int) -> Int,
    appsPage: Int?,
    resizing: ItemEntity?,
    modifier: Modifier = Modifier,
    apps: @Composable () -> Unit,
) {
    val s = state.settings
    val target = host.drag.target
    HorizontalPager(
        state = pager,
        modifier = modifier,
        beyondViewportPageCount = if (host.drag.dragging) state.pageCount else 1,
        key = { it },
    ) { virtual ->
        if (virtual == appsPage) {
            apps()
            return@HorizontalPager
        }
        val page = realPage(virtual)
        val offset = (pager.currentPage - virtual) + pager.currentPageOffsetFraction
        Box(Modifier.fillMaxSize().scrollEffect(s.scrollEffect, offset)) {
            DropPreview(target, Container.DESKTOP, page, s.desktopColumns, s.desktopRows, host.drag.session?.item)
            val items = state.desktop[page].orEmpty().map { if (it.id == resizing?.id) resizing else it }
            CellGrid(s.desktopColumns, s.desktopRows, items, Modifier.fillMaxSize()) { item ->
                HomeItem(
                    item, state, host, Origin.Desktop, s.desktopIconSize, s.desktopLabels,
                    highlighted = target.highlights(item),
                )
            }
        }
    }
}

fun DropTarget.highlights(item: ItemEntity) =
    (this is DropTarget.NewFolder && target.id == item.id) || (this is DropTarget.IntoFolder && folder.id == item.id)

@Composable
fun DropPreview(target: DropTarget, container: Long, page: Int, columns: Int, rows: Int, item: ItemEntity?) {
    if (target !is DropTarget.Cell || target.container != container || target.page != page || item == null) return
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cw = maxWidth / columns
        val ch = maxHeight / rows
        val x by animateDpAsState(cw * target.x, enterTween(Motion.STATE), label = "dropX")
        val y by animateDpAsState(ch * target.y, enterTween(Motion.STATE), label = "dropY")
        Box(
            Modifier
                .offset(x, y)
                .size(cw * item.spanX, ch * item.spanY)
                .padding(4.dp)
                .clip(Radius.control)
                .background(Simplify.colors.accent.copy(alpha = States.SELECTED))
        )
    }
}

@Composable
fun Dock(state: HomeState, host: ItemHost, dockPager: PagerState, modifier: Modifier = Modifier) {
    val s = state.settings
    val labelHeight = if (s.dockLabels) 22.dp else 0.dp
    val height = iconSize(s.dockIconSize) + labelHeight + Space.rowGap * 2
    val background = when (s.dockBackground) {
        DockBackground.None -> Color.Transparent
        DockBackground.Surface -> Simplify.colors.surface.copy(alpha = s.dockOpacity / 100f)
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .padding(horizontal = Space.toolbarInset)
            .clip(Radius.card)
            .background(background)
    ) {
        HorizontalPager(dockPager, Modifier.fillMaxSize(), userScrollEnabled = s.dockPages > 1) { page ->
            Box(Modifier.fillMaxSize()) {
                DropPreview(host.drag.target, Container.DOCK, page, s.dockColumns, 1, host.drag.session?.item)
                CellGrid(s.dockColumns, 1, state.dock.filter { it.page == page }, Modifier.fillMaxSize()) { item ->
                    HomeItem(item, state, host, Origin.Dock, s.dockIconSize, s.dockLabels, highlighted = host.drag.target.highlights(item))
                }
            }
        }
    }
}

@Composable
fun PageIndicatorView(style: PageIndicator, current: Int, count: Int, modifier: Modifier = Modifier) {
    if (count <= 1 || style == PageIndicator.None) return
    val color = LocalLabelColor.current
    Row(
        modifier.height(20.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (style) {
            PageIndicator.Dots -> repeat(count) { i ->
                Box(Modifier.size(if (i == current) 7.dp else 5.dp).clip(Radius.pill).background(color.copy(alpha = if (i == current) 1f else 0.45f)))
            }
            PageIndicator.Line -> repeat(count) { i ->
                Box(Modifier.width(if (i == current) 18.dp else 10.dp).height(2.dp).clip(Radius.pill).background(color.copy(alpha = if (i == current) 1f else 0.4f)))
            }
            PageIndicator.Numeric -> RoleText(
                pageLabel(current, count), Simplify.type.meta, color = color,
            )
            PageIndicator.None -> Unit
        }
    }
}

@Composable
fun SearchBar(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Space.screen)
            .height(Space.hitTarget)
            .pressable(onClick, shape = Radius.control, base = Simplify.colors.surface.copy(alpha = 0.92f))
            .padding(horizontal = Space.toolbarInset),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.base),
    ) {
        Icon(Icons.Search, tint = Simplify.colors.textMuted, decorative = true)
        RoleText(stringResource(R.string.search_hint), Simplify.type.body, color = Simplify.colors.textMuted, maxLines = 1)
    }
}

@Composable
fun WallpaperScroll(enabled: Boolean, pager: PagerState, pages: Int, realPage: (Int) -> Int) {
    val view = LocalView.current
    val manager = WallpaperManager.getInstance(LocalContext.current)
    LaunchedEffect(enabled, pages) {
        if (!enabled || pages <= 1) {
            view.windowToken?.let { manager.setWallpaperOffsets(it, 0.5f, 0.5f) }
            return@LaunchedEffect
        }
        manager.setWallpaperOffsetSteps(1f / (pages - 1), 1f)
        snapshotFlow { realPage(pager.currentPage) + pager.currentPageOffsetFraction }.collect { position ->
            val token = view.windowToken ?: return@collect
            manager.setWallpaperOffsets(token, (position / (pages - 1)).coerceIn(0f, 1f), 0.5f)
        }
    }
}

@Composable
fun WallpaperLabelColor(): Color {
    val context = LocalContext.current
    val manager = androidx.compose.runtime.remember(context) { WallpaperManager.getInstance(context) }
    var darkText by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(supportsDarkText(manager)) }
    DisposableEffect(manager) {
        val listener = WallpaperManager.OnColorsChangedListener { _, _ -> darkText = supportsDarkText(manager) }
        manager.addOnColorsChangedListener(listener, Handler(Looper.getMainLooper()))
        onDispose { manager.removeOnColorsChangedListener(listener) }
    }
    return if (darkText) InkOnLight else InkOnDark
}

private fun supportsDarkText(manager: WallpaperManager): Boolean =
    runCatching {
        val colors = manager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM) ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            colors.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT != 0
        } else {
            colors.primaryColor.luminance() > 0.6f
        }
    }.getOrDefault(false)
