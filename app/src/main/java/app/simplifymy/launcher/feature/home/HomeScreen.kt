package app.simplifymy.launcher.feature.home

import android.app.ActivityOptions
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.simplifymy.launcher.R
import app.simplifymy.launcher.core.calendar.CalendarPanel
import app.simplifymy.launcher.feature.calendar.CalendarPanels
import app.simplifymy.launcher.core.actions.Action
import app.simplifymy.launcher.core.actions.UiAction
import app.simplifymy.launcher.core.apps.AppInfo
import app.simplifymy.launcher.core.apps.AppKey
import app.simplifymy.launcher.core.settings.DrawerPosition
import app.simplifymy.launcher.core.settings.HomeAction
import app.simplifymy.launcher.core.settings.SearchBarPlacement
import app.simplifymy.launcher.core.workspace.Container
import app.simplifymy.launcher.core.workspace.DrawerGroup
import app.simplifymy.launcher.core.workspace.ItemEntity
import app.simplifymy.launcher.core.workspace.ItemFlags
import app.simplifymy.launcher.core.workspace.ItemType
import app.simplifymy.launcher.feature.drawer.AppGrid
import app.simplifymy.launcher.feature.drawer.Drawer
import app.simplifymy.launcher.feature.drawer.DrawerHost
import app.simplifymy.launcher.feature.drawer.SearchPanel
import app.simplifymy.launcher.feature.drawer.toAndroid
import app.simplifymy.launcher.feature.settings.CityPicker
import app.simplifymy.launcher.ui.SystemBars
import app.simplifymy.launcher.ui.component.Icons
import app.simplifymy.launcher.ui.launcher.IconImage
import app.simplifymy.launcher.ui.launcher.LocalLabelColor
import app.simplifymy.launcher.ui.launcher.rootRect
import app.simplifymy.launcher.ui.theme.Motion
import app.simplifymy.launcher.ui.theme.Radius
import app.simplifymy.launcher.ui.theme.RoleText
import app.simplifymy.launcher.ui.theme.Simplify
import app.simplifymy.launcher.ui.theme.Space
import app.simplifymy.launcher.ui.theme.States
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val VIRTUAL_LOOPS = 400

fun interface WidgetRequester {
    fun request(info: AppWidgetProviderInfo, page: Int, spanX: Int, spanY: Int)
}

@Composable
fun HomeScreen(viewModel: HomeViewModel, widgets: WidgetRequester) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val s = state.settings
    val container = viewModel.container
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val density = LocalDensity.current
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    val drag = remember { DragController() }.also { it.slop = touchSlop * 2 }
    val drawer = remember { Animatable(0f) }
    var drawerHeight by remember { mutableStateOf(1f) }
    var drawerDirection by remember { mutableStateOf(0f) }
    var searching by remember { mutableStateOf(false) }
    var folder by remember { mutableStateOf<ItemEntity?>(null) }
    var folderAnchor by remember { mutableStateOf(Rect.Zero) }
    var drawerFolder by remember { mutableStateOf<DrawerGroup?>(null) }
    var overview by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf<MenuRequest?>(null) }
    var edit by remember { mutableStateOf<EditRequest?>(null) }
    var picker by remember { mutableStateOf(false) }
    var choosingCity by remember { mutableStateOf(false) }
    var resize by remember { mutableStateOf<ItemEntity?>(null) }
    var dockHidden by remember { mutableStateOf(false) }

    val pages = state.pageCount
    val position = s.drawerPosition
    val infinite = s.infiniteScroll && pages > 1 && !position.isPage
    val appsPage = when (position) {
        DrawerPosition.FirstPage -> 0
        DrawerPosition.LastPage -> pages
        else -> null
    }
    val pageOffset = if (position == DrawerPosition.FirstPage) 1 else 0
    val homeVirtual = if (infinite) VIRTUAL_LOOPS / 2 * pages + s.defaultPage else s.defaultPage + pageOffset
    val pager = rememberPagerState(initialPage = homeVirtual) {
        if (infinite) pages * VIRTUAL_LOOPS else pages + if (appsPage != null) 1 else 0
    }
    LaunchedEffect(infinite, appsPage == null, pageOffset) { pager.scrollToPage(homeVirtual) }
    val realPage: (Int) -> Int = { v -> if (infinite) Math.floorMod(v, pages) else (v - pageOffset).coerceIn(0, pages - 1) }
    val onApps = appsPage != null && pager.currentPage == appsPage
    val currentPage = realPage(pager.currentPage)
    val dockPager = rememberPagerState { s.dockPages.coerceAtLeast(1) }
    val latestState by rememberUpdatedState(state)

    fun goToPage(page: Int, animate: Boolean = true) {
        val st = latestState.settings
        val n = latestState.pageCount
        val looping = st.infiniteScroll && n > 1 && !st.drawerPosition.isPage
        val offset = if (st.drawerPosition == DrawerPosition.FirstPage) 1 else 0
        val target = if (looping) pager.currentPage - Math.floorMod(pager.currentPage, n) + page.coerceIn(0, n - 1) else page.coerceIn(0, n - 1) + offset
        scope.launch { if (animate) pager.animateScrollToPage(target) else pager.scrollToPage(target) }
    }

    fun openDrawer() {
        val apps = when (latestState.settings.drawerPosition) {
            DrawerPosition.FirstPage -> 0
            DrawerPosition.LastPage -> latestState.pageCount
            else -> null
        }
        scope.launch {
            if (apps != null) pager.animateScrollToPage(apps) else drawer.animateTo(1f, tween(Motion.CHROME, easing = Motion.enter))
        }
    }

    fun closeDrawer(animate: Boolean = true) {
        drawerFolder = null
        scope.launch { if (animate) drawer.animateTo(0f, tween(Motion.CHROME, easing = Motion.exit)) else drawer.snapTo(0f) }
    }

    fun along(v: Offset): Float = v.y * latestState.settings.drawerPosition.openY

    fun dragDrawerBy(delta: Offset) {
        val opening = along(delta)
        if (opening != 0f) drawerDirection = opening
        scope.launch { drawer.snapTo((drawer.value + opening / drawerHeight).coerceIn(0f, 1f)) }
    }

    fun settleDrawer(velocity: Offset) {
        val speed = along(velocity)
        val open = when {
            speed > 1200f -> true
            speed < -1200f -> false
            drawerDirection > 0f && drawer.value > 0.15f -> true
            drawerDirection < 0f && drawer.value < 0.85f -> false
            else -> drawer.value > 0.5f
        }
        if (open) openDrawer() else closeDrawer()
    }

    fun closeAll(): Boolean {
        val any = folder != null || drawer.value > 0f || overview || menu != null || picker || resize != null || searching
        folder = null; menu = null; overview = false; picker = false; searching = false
        resize?.let { viewModel.update(it) }; resize = null
        if (drawer.value > 0f) closeDrawer()
        return any
    }

    fun launchOptions(bounds: Rect): Bundle? {
        if (!s.revealAnimation || bounds == Rect.Zero) return null
        return ActivityOptions.makeClipRevealAnimation(
            view, bounds.left.roundToInt(), bounds.top.roundToInt(), bounds.width.roundToInt(), bounds.height.roundToInt(),
        ).toBundle()
    }

    fun screenRect(bounds: Rect): android.graphics.Rect {
        val loc = IntArray(2).also(view::getLocationOnScreen)
        return bounds.translate(Offset(loc[0].toFloat(), loc[1].toFloat())).toAndroid()
    }

    fun runAction(action: Action, bounds: Rect = Rect.Zero) =
        container.actions.run(action, screenRect(bounds), launchOptions(bounds))

    fun launchItem(item: ItemEntity, bounds: Rect) {
        when (item.type) {
            ItemType.APP -> item.app?.let(AppKey::parse)?.let {
                container.actions.launch(it, screenRect(bounds), launchOptions(bounds))
            }
            ItemType.SHORTCUT -> container.actions.launchShortcut(
                item.packageName.orEmpty(), item.shortcutId.orEmpty(), item.user, screenRect(bounds), launchOptions(bounds),
            )
        }
    }

    fun shortcutsInto(request: MenuRequest, packageName: String, user: Long) {
        scope.launch {
            val shortcuts = withContext(Dispatchers.IO) {
                container.apps.shortcuts(packageName, user).map { info ->
                    MenuShortcut((info.shortLabel ?: info.longLabel ?: "").toString(), container.icons.shortcut(info)) {
                        container.actions.launchShortcut(info.`package`, info.id, user, screenRect(request.anchor), launchOptions(request.anchor))
                    }
                }
            }
            if (menu === request && shortcuts.isNotEmpty()) menu = request.copy(shortcuts = shortcuts)
        }
    }

    fun menuFor(item: ItemEntity, origin: Origin, bounds: Rect): MenuRequest {
        val key = item.app?.let(AppKey::parse)
        val actions = buildList {
            if (origin == Origin.Drawer && key != null) {
                add(MenuAction(Icons.Plus, { viewModel.addToDesktop(item.copy(id = 0, container = Container.DESKTOP), currentPage) }))
                latestState.apps[item.app]?.let { app -> add(MenuAction(Icons.Edit, { edit = EditRequest.App(app) })) }
                add(MenuAction(Icons.Hide, { viewModel.setHidden(key, true) }))
            } else {
                if (item.isWidget) add(MenuAction(Icons.Resize, { resize = item }))
                else add(MenuAction(Icons.Edit, { edit = EditRequest.Item(item) }))
                add(MenuAction(Icons.Remove, { viewModel.remove(item) }))
            }
            if (key != null) add(MenuAction(Icons.Info, { container.actions.appInfo(key) }))
            if (key != null && origin != Origin.Folder) add(MenuAction(Icons.Trash, { container.actions.uninstall(key.component.packageName) }, danger = true))
        }
        val title = if (item.isWidget) null else (latestState.label(item).ifEmpty { null })
        val request = MenuRequest(bounds, title, emptyList(), actions)
        if (item.type == ItemType.APP && key != null) shortcutsInto(request, key.component.packageName, key.user)
        return request
    }

    fun applyDrop(session: DragSession, target: DropTarget) {
        val item = session.item
        val key = item.app?.let(AppKey::parse)
        when (target) {
            DropTarget.None -> Unit
            is DropTarget.Cell -> viewModel.move(item, target.container, target.page, target.x, target.y)
            is DropTarget.NewFolder -> viewModel.createFolder(target.target, item, container.app.getString(R.string.folder_default_name))
            is DropTarget.IntoFolder -> viewModel.addToFolder(target.folder, item)
            is DropTarget.Bar -> when (target.target) {
                BarTarget.Remove -> if (item.id != 0L) viewModel.remove(item)
                BarTarget.Info -> key?.let(container.actions::appInfo)
                BarTarget.Uninstall -> (key?.component?.packageName ?: item.packageName)?.let(container.actions::uninstall)
            }
            is DropTarget.Reorder -> folder?.let { f ->
                viewModel.reorderFolder(f.id, reordered(latestState.folders[f.id].orEmpty(), item, target.index))
            }
        }
        if (session.origin == Origin.Folder && target !is DropTarget.Reorder && target != DropTarget.None) folder = null
        if (session.origin == Origin.Drawer && target != DropTarget.None) {
            closeDrawer(animate = false)
            searching = false
        }
    }

    val host = object : ItemHost {
        override val drag = drag
        override fun launch(item: ItemEntity, bounds: Rect) {
            folder = null
            launchItem(item, bounds)
        }
        override fun openFolder(item: ItemEntity, bounds: Rect) {
            if (overview) {
                overview = false
            } else {
                folderAnchor = bounds
                folder = item
            }
        }
        override fun startDrag(item: ItemEntity, origin: Origin, icon: ImageBitmap?, bounds: Rect, at: Offset) {
            drag.start(DragSession(item, origin, icon, bounds.size, at - bounds.topLeft), at)
            menu = menuFor(item, origin, bounds)
        }
        override fun dragTo(at: Offset) {
            drag.move(at, latestState, if (onApps) -1 else realPage(pager.currentPage), dockPager.currentPage)
            if (drag.moved && menu != null) menu = null
        }
        override fun endDrag(cancelled: Boolean) {
            val (session, target) = drag.end() ?: return
            if (!cancelled) applyDrop(session, target)
        }
    }

    val drawerHost = object : DrawerHost {
        override fun launch(app: AppInfo, bounds: Rect) {
            searching = false
            container.actions.launch(app.key, screenRect(bounds), launchOptions(bounds))
        }
        override fun startDrag(app: AppInfo, icon: ImageBitmap?, bounds: Rect, at: Offset) {
            focusManager.clearFocus()
            keyboard?.hide()
            host.startDrag(ItemEntity(container = -1, type = ItemType.APP, app = app.key.flat), Origin.Drawer, icon, bounds, at)
        }
        override fun dragTo(at: Offset) = host.dragTo(at)
        override fun endDrag(cancelled: Boolean) = host.endDrag(cancelled)
        override fun openDrawerFolder(group: DrawerGroup) { drawerFolder = group }
        override fun dragBy(delta: Offset) = dragDrawerBy(delta)
        override fun settle(velocity: Offset) = settleDrawer(velocity)
        override fun openSettings() = runAction(Action.LauncherSettings)
        override fun launchShortcut(info: ShortcutInfo, bounds: Rect) {
            searching = false
            container.actions.launchShortcut(info.`package`, info.id, container.apps.serial(info.userHandle))
        }
    }

    val edge = drag.desktop?.let { geo ->
        val margin = with(density) { 22.dp.toPx() }
        when {
            !drag.active || drag.insideFolder -> 0
            drag.position.x < geo.bounds.left + margin -> -1
            drag.position.x > geo.bounds.right - margin -> 1
            else -> 0
        }
    } ?: 0
    LaunchedEffect(edge) {
        while (edge != 0) {
            delay(650)
            val atEnd = !infinite && pager.currentPage == pager.pageCount - 1 && edge > 0 && !onApps
            if (atEnd) {
                viewModel.addPage()
                delay(80)
            }
            if (!infinite && pager.currentPage == 0 && edge < 0) return@LaunchedEffect
            if (!infinite && pager.currentPage == pager.pageCount - 1 && edge > 0 && onApps) return@LaunchedEffect
            pager.animateScrollToPage(pager.currentPage + edge)
        }
    }

    LaunchedEffect(Unit) {
        container.actions.ui.collect { action ->
            when (action) {
                UiAction.Drawer -> openDrawer()
                UiAction.Search -> searching = true
                UiAction.Overview -> overview = !overview
                UiAction.DefaultPage -> goToPage(latestState.settings.defaultPage)
                UiAction.ToggleDock -> dockHidden = !dockHidden
            }
        }
    }
    LaunchedEffect(Unit) {
        container.homePressed.collect {
            if (closeAll()) return@collect
            when (latestState.settings.homeAction) {
                HomeAction.DefaultPage -> goToPage(latestState.settings.defaultPage)
                HomeAction.Drawer -> openDrawer()
                HomeAction.Overview -> overview = true
                HomeAction.Nothing -> Unit
            }
        }
    }
    BackHandler(enabled = folder != null || drawer.value > 0f || overview || menu != null || picker || resize != null || edit != null || searching) {
        when {
            menu != null -> menu = null
            drawerFolder != null -> drawerFolder = null
            else -> closeAll()
        }
    }

    WallpaperScroll(s.wallpaperScroll, pager, if (infinite) pages else pager.pageCount) { v -> if (infinite) realPage(v) else v }

    fun swipeTargetAt(p: Offset): (() -> Unit)? {
        if (onApps) return null
        val hit = { geo: Geometry?, items: List<ItemEntity> ->
            geo?.takeIf { it.contains(p) }?.let { g ->
                val cx = ((p.x - g.bounds.left) / g.cellWidth).toInt()
                val cy = ((p.y - g.bounds.top) / g.cellHeight).toInt()
                items.firstOrNull { cx in it.x until it.x + it.spanX && cy in it.y until it.y + it.spanY }
            }
        }
        val item = hit(drag.dock, state.dock.filter { it.page == dockPager.currentPage }) ?: hit(drag.desktop, state.desktop[currentPage].orEmpty())
            ?: return null
        if (item.isFolder && item.has(ItemFlags.SWIPE_TO_OPEN)) {
            val geo = if (item.container == Container.DOCK) drag.dock else drag.desktop
            return {
                folderAnchor = geo?.cellRect(item.x, item.y) ?: Rect.Zero
                folder = item
            }
        }
        val action = item.swipeAction()?.takeIf { it != Action.None } ?: return null
        return { runAction(action) }
    }

    val runLatest by rememberUpdatedState<(Action) -> Unit> { runAction(it) }
    val onAppsLatest by rememberUpdatedState(onApps)
    val swipeLatest by rememberUpdatedState<(Offset) -> (() -> Unit)?> { swipeTargetAt(it) }
    val gestures = remember {
        fun settings() = latestState.settings
        DesktopGestureCallbacks(
            active = { !drag.dragging && folder == null && drawer.value == 0f && resize == null && !picker && !searching && !onAppsLatest },
            onSwipeStart = { swipe, p ->
                val opens = when (settings().drawerPosition) {
                    DrawerPosition.Bottom -> swipe == Swipe.Up
                    DrawerPosition.Top -> swipe == Swipe.Down
                    else -> false
                }
                when {
                    overview -> { if (swipe == Swipe.Down) overview = false; false }
                    swipe == Swipe.Up -> {
                        val iconAction = swipeLatest(p)
                        when {
                            iconAction != null -> { iconAction(); false }
                            opens && settings().swipeUp == Action.Drawer -> true
                            else -> { runLatest(settings().swipeUp); false }
                        }
                    }
                    else -> opens || run { runLatest(settings().swipeDown); false }
                }
            },
            onSwipeDrag = { dragDrawerBy(it) },
            onSwipeEnd = { settleDrawer(it) },
            onPinchIn = { runLatest(settings().pinchIn) },
            onPinchOut = { if (overview) overview = false else runLatest(settings().pinchOut) },
            onTwoFingerUp = { runLatest(settings().twoFingerUp) },
            onTwoFingerDown = { runLatest(settings().twoFingerDown) },
        )
    }

    val labelColor = WallpaperLabelColor()
    CompositionLocalProvider(LocalLabelColor provides labelColor) {
        val overTheme = drawer.value > 0.5f || picker || searching || onApps
        SystemBars(
            light = if (overTheme) Simplify.colors.isLight else labelColor.red < 0.5f,
            hideStatus = s.hideStatusBar && !overTheme,
        )
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = s.wallpaperDim / 100f))
        ) {
            drawerHeight = with(density) { maxHeight.toPx() }
            val fromDrawer = drag.active && drag.session?.origin == Origin.Drawer
            val desktopAlpha = if (fromDrawer) 1f else 1f - drawer.value
            Column(
                Modifier
                    .fillMaxSize()
                    .alpha(desktopAlpha)
                    .desktopGestures(gestures)
                    .pointerInput(s.doubleTap, overview) {
                        detectTapGestures(
                            onDoubleTap = if (s.doubleTap != Action.None) { _ -> runAction(s.doubleTap) } else null,
                            onLongPress = {
                                scope.launch {
                                    delay(60)
                                    if (!drag.dragging && !latestState.settings.lockDesktop) overview = true
                                }
                            },
                            onTap = { if (overview) overview = false },
                        )
                    }
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .windowInsetsPadding(WindowInsets.navigationBars),
            ) {
                val desktopSearch = s.searchBar
                if (desktopSearch == SearchBarPlacement.Top) {
                    SearchBar({ searching = true }, Modifier.padding(vertical = Space.base))
                }
                val scale = if (overview) 0.8f else 1f
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .padding(horizontal = Space.gutter)
                        .onGloballyPositioned {
                            drag.desktop = Geometry(it.rootRect(), s.desktopColumns, s.desktopRows)
                        }
                ) {
                    DesktopPager(state, pager, host, realPage, appsPage, resize, Modifier.fillMaxSize()) {
                        Drawer(state, drawerHost, open = onApps, position = position, ownSearch = desktopSearch == SearchBarPlacement.None)
                    }
                }
                if (overview) {
                    OverviewControls(
                        page = currentPage,
                        pages = pages,
                        isDefault = currentPage == s.defaultPage,
                        onAddPage = { viewModel.addPage(); scope.launch { delay(80); goToPage(pages) } },
                        onRemovePage = { viewModel.removePage(currentPage) },
                        onMoveLeft = { viewModel.movePage(currentPage, currentPage - 1); goToPage(currentPage - 1, animate = false) },
                        onMoveRight = { viewModel.movePage(currentPage, currentPage + 1); goToPage(currentPage + 1, animate = false) },
                        onSetDefault = { viewModel.settings { it.copy(defaultPage = currentPage) } },
                        onWidgets = { picker = true },
                        onWallpaper = {
                            container.actions.start(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), container.app.getString(R.string.overview_wallpaper)))
                        },
                        onSettings = { runAction(Action.LauncherSettings) },
                    )
                } else {
                    if (!onApps) PageIndicatorView(s.pageIndicator, currentPage, pages, Modifier.align(Alignment.CenterHorizontally).padding(vertical = Space.gutter))
                }
                if (desktopSearch == SearchBarPlacement.AboveDock) SearchBar({ searching = true }, Modifier.padding(vertical = Space.base))
                if (s.dockEnabled && !dockHidden && !overview) {
                    Box(Modifier.onGloballyPositioned {
                        drag.dock = Geometry(it.rootRect().let { r ->
                            val inset = with(density) { Space.toolbarInset.toPx() }
                            Rect(r.left + inset, r.top, r.right - inset, r.bottom)
                        }, s.dockColumns, 1)
                    }) {
                        Dock(state, host, dockPager)
                    }
                } else {
                    drag.dock = null
                }
                if (desktopSearch == SearchBarPlacement.BelowDock) SearchBar({ searching = true }, Modifier.padding(vertical = Space.base))
            }

            resize?.let { item ->
                drag.desktop?.let { geo ->
                    val pageItems = state.desktop[item.page].orEmpty()
                    ResizeFrame(item, geo, pageItems, onChange = { resize = it }) { result ->
                        viewModel.update(result)
                        resize = null
                    }
                }
            }

            FolderPanel(folder, folderAnchor, state, host, onRename = { edit = EditRequest.Item(it) }, onDismiss = { folder = null })

            if (!position.isPage) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { translationY = -position.openY * (1f - drawer.value) * size.height }
                        .alpha(if (fromDrawer) 0f else 1f)
                ) {
                    Drawer(state, drawerHost, open = drawer.value > 0f, position = position)
                }
            }
            DrawerFolderPanel(drawerFolder, state, drawerHost) { drawerFolder = null }

            if (searching) {
                SearchPanel(state, drawerHost, onClose = { searching = false }, modifier = Modifier.alpha(if (fromDrawer) 0f else 1f))
            }

            ItemMenu(menu) { menu = null }
            DropBar(drag, barTargets(drag.session), Modifier.windowInsetsPadding(WindowInsets.statusBars))
            DragLayer(drag)

            if (picker) {
                val geo = drag.desktop
                WidgetPicker(
                    cellWidthPx = geo?.cellWidth ?: 1f,
                    cellHeightPx = geo?.cellHeight ?: 1f,
                    columns = s.desktopColumns,
                    rows = s.desktopRows,
                    onPick = { info ->
                        picker = false
                        overview = false
                        val (sx, sy) = widgetSpan(info, geo?.cellWidth ?: 1f, geo?.cellHeight ?: 1f, s.desktopColumns, s.desktopRows)
                        widgets.request(info, currentPage, sx, sy)
                    },
                    onPickBuiltin = { widget ->
                        picker = false
                        overview = false
                        if (widget.calendar && !container.calendar.granted) container.calendarPanel.value = CalendarPanel.Access
                        if (widget.weather && !s.weather) {
                            scope.launch { container.settings.update { it.copy(weather = true) } }
                            if (s.weatherCity == null) choosingCity = true
                        }
                        viewModel.place(
                            ItemEntity(container = Container.DESKTOP, type = ItemType.WIDGET, spanX = widget.spanX, spanY = widget.spanY, provider = widget.provider),
                            currentPage,
                        )
                    },
                    onBack = { picker = false },
                )
            }
            EditSheet(edit, state, viewModel) { edit = null }
            CalendarPanels()
            CityPicker(
                choosingCity,
                onPick = { city -> scope.launch { container.settings.update { it.addCity(city) } } },
                onDismiss = { choosingCity = false },
            )
        }
    }
}

private fun barTargets(session: DragSession?): List<BarTarget> {
    val item = session?.item ?: return emptyList()
    return buildList {
        if (session.origin != Origin.Drawer) add(BarTarget.Remove)
        if (item.app != null) { add(BarTarget.Info); add(BarTarget.Uninstall) }
    }
}

@Composable
private fun DragLayer(drag: DragController) {
    val session = drag.session ?: return
    if (!drag.moved) return
    val density = LocalDensity.current
    val topLeft = drag.position - session.grab
    Box(
        Modifier
            .offset { IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()) }
            .size(with(density) { session.size.width.toDp() }, with(density) { session.size.height.toDp() })
            .graphicsLayer { scaleX = 1.08f; scaleY = 1.08f },
        contentAlignment = Alignment.TopCenter,
    ) {
        if (session.icon != null) {
            IconImage(session.icon, with(density) { minOf(session.size.width, 48.dp.toPx() * 1.3f).toDp() })
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(4.dp)
                    .background(Simplify.colors.accent.copy(alpha = States.SELECTED), Radius.control)
            )
        }
    }
}

@Composable
private fun DrawerFolderPanel(group: DrawerGroup?, state: HomeState, host: DrawerHost, onDismiss: () -> Unit) {
    if (group == null) return
    val apps = state.apps.values.filter { it.key.flat in group.memberKeys }.sortedBy { state.label(it).lowercase() }
    FolderWindow(group.name, apps.size, state.settings.folderStyle, Rect.Zero, onDismiss) {
        AppGrid(apps, state, host, state.settings.folderColumns, labels = true)
        if (apps.isEmpty()) {
            RoleText(stringResource(R.string.group_empty), Simplify.type.meta, color = Simplify.colors.textMuted, modifier = Modifier.padding(horizontal = Space.screen))
        }
    }
}
