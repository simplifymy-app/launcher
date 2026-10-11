package app.simplifymy.launcher.feature.drawer

import android.app.SearchManager
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.graphics.Rect as AndroidRect
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import app.simplifymy.launcher.R
import app.simplifymy.launcher.container
import app.simplifymy.launcher.core.apps.AppInfo
import app.simplifymy.launcher.core.search.SearchResults
import app.simplifymy.launcher.core.settings.DrawerPosition
import app.simplifymy.launcher.core.settings.DrawerSort
import app.simplifymy.launcher.core.settings.DrawerStyle
import app.simplifymy.launcher.core.settings.Settings
import app.simplifymy.launcher.core.workspace.DrawerGroup
import app.simplifymy.launcher.core.workspace.GroupKind
import app.simplifymy.launcher.core.workspace.ItemEntity
import app.simplifymy.launcher.core.workspace.ItemType
import app.simplifymy.launcher.feature.home.HomeState
import app.simplifymy.launcher.feature.home.itemGestures
import app.simplifymy.launcher.ui.component.Icon
import app.simplifymy.launcher.ui.component.IconAction
import app.simplifymy.launcher.ui.component.Icons
import app.simplifymy.launcher.ui.component.Menu
import app.simplifymy.launcher.ui.component.MenuItem
import app.simplifymy.launcher.ui.component.SectionHeader
import app.simplifymy.launcher.ui.component.pressable
import app.simplifymy.launcher.ui.launcher.AppIcon
import app.simplifymy.launcher.ui.launcher.BoundsRef
import app.simplifymy.launcher.ui.launcher.FolderIcon
import app.simplifymy.launcher.ui.launcher.IconCell
import app.simplifymy.launcher.ui.launcher.IconRow
import app.simplifymy.launcher.ui.launcher.iconSize
import app.simplifymy.launcher.ui.launcher.pageLabel
import app.simplifymy.launcher.ui.launcher.rememberAppIcon
import app.simplifymy.launcher.ui.launcher.rememberIcon
import app.simplifymy.launcher.ui.launcher.trackBounds
import app.simplifymy.launcher.ui.theme.Radius
import app.simplifymy.launcher.ui.theme.RoleText
import app.simplifymy.launcher.ui.theme.Simplify
import app.simplifymy.launcher.ui.theme.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface DrawerHost {
    fun launch(app: AppInfo, bounds: Rect)
    fun startDrag(app: AppInfo, icon: ImageBitmap?, bounds: Rect, at: Offset)
    fun dragTo(at: Offset)
    fun endDrag(cancelled: Boolean)
    fun openDrawerFolder(group: DrawerGroup)
    fun dragBy(delta: Offset)
    fun settle(velocity: Offset)
    fun openSettings()
    fun launchShortcut(info: ShortcutInfo, bounds: Rect)
}

private sealed interface Tab {
    data object All : Tab
    data object Work : Tab
    data class Group(val group: DrawerGroup) : Tab
}

@Composable
fun Drawer(
    state: HomeState,
    host: DrawerHost,
    open: Boolean,
    position: DrawerPosition,
    modifier: Modifier = Modifier,
    ownSearch: Boolean = true,
) {
    val s = state.settings
    val container = LocalContext.current.container
    var query by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf<Tab>(Tab.All) }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(open) {
        if (!open) { query = ""; focusManager.clearFocus() }
    }

    val tabs = buildList {
        add(Tab.All)
        state.groups.filter { it.kind == GroupKind.TAB }.forEach { add(Tab.Group(it)) }
        if (s.workTab && container.apps.hasWorkProfile) add(Tab.Work)
    }
    val hasWork = tabs.contains(Tab.Work)
    val apps = when (val t = tab) {
        Tab.All -> state.drawer.filter { !hasWork || !it.work }
        Tab.Work -> state.drawer.filter { it.work }
        is Tab.Group -> state.apps.values.filter { it.key.flat in t.group.memberKeys }.sortedBy { state.label(it).lowercase() }
    }
    val folders = if (tab == Tab.All) state.groups.filter { it.kind == GroupKind.FOLDER } else emptyList()

    val currentHost by androidx.compose.runtime.rememberUpdatedState(host)
    val closing by rememberUpdatedState(-position.openY)
    val closeOnPull = remember {
        object : NestedScrollConnection {
            var pulling = false
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y * closing > 0) {
                    pulling = true
                    currentHost.dragBy(Offset(0f, available.y))
                    return available
                }
                return Offset.Zero
            }
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (pulling && available.y * closing < 0 && source == NestedScrollSource.UserInput) {
                    currentHost.dragBy(Offset(0f, available.y))
                    return available
                }
                return Offset.Zero
            }
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!pulling) return Velocity.Zero
                pulling = false
                currentHost.settle(Offset(available.x, available.y))
                return available
            }
        }
    }
    val sheet = when (position) {
        DrawerPosition.Bottom -> RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)
        DrawerPosition.Top -> RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp)
        else -> RoundedCornerShape(0.dp)
    }

    Column(
        modifier
            .fillMaxSize()
            .clip(sheet)
            .background(Simplify.colors.surface.copy(alpha = s.drawerOpacity / 100f))
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Column(
            Modifier.pointerInput(position) {
                if (position.isPage) return@pointerInput
                detectVerticalDragGestures(
                    onVerticalDrag = { change, dy -> change.consume(); currentHost.dragBy(Offset(0f, dy)) },
                    onDragEnd = { currentHost.settle(Offset.Zero) },
                    onDragCancel = { currentHost.settle(Offset.Zero) },
                )
            }
        ) {
            if (position == DrawerPosition.Bottom) GrabHandle(Modifier.align(Alignment.CenterHorizontally))
            else Box(Modifier.height(Space.base + Space.gutter))
            SearchField(
                query = query,
                onQuery = { query = it },
                onSubmit = {
                    val first = container.search.search(query, state.searchable(), false).apps.firstOrNull()
                    if (first != null) host.launch(first, Rect.Zero)
                },
                onSettings = host::openSettings,
                settings = s,
                input = ownSearch,
            )
            if (tabs.size > 1 && query.isBlank()) {
                TabStrip(tabs, tab, { tab = it })
            }
        }
        Box(Modifier.weight(1f).nestedScroll(closeOnPull)) {
            if (query.isNotBlank()) {
                SearchResultsList(query, state, host)
            } else when (s.drawerStyle) {
                DrawerStyle.Vertical -> VerticalDrawer(state, apps, folders, host, tab == Tab.All)
                DrawerStyle.Paged -> PagedDrawer(state, apps, folders, host)
                DrawerStyle.List -> ListDrawer(state, apps, host, tab == Tab.All)
            }
        }
        if (position == DrawerPosition.Top) {
            GrabHandle(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { change, dy -> change.consume(); currentHost.dragBy(Offset(0f, dy)) },
                            onDragEnd = { currentHost.settle(Offset.Zero) },
                            onDragCancel = { currentHost.settle(Offset.Zero) },
                        )
                    }
            )
        }
        Box(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
    }
}

@Composable
private fun GrabHandle(modifier: Modifier = Modifier) {
    Box(modifier.padding(vertical = Space.base + Space.gutter, horizontal = Space.screen)) {
        Box(Modifier.width(36.dp).height(4.dp).clip(Radius.pill).background(Simplify.colors.hairline))
    }
}

@Composable
private fun SearchField(
    query: String,
    onQuery: (String) -> Unit,
    onSubmit: () -> Unit,
    onSettings: () -> Unit,
    settings: Settings,
    input: Boolean,
) {
    var menu by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val container = LocalContext.current.container
    val scope = rememberCoroutineScope()
    fun edit(change: (Settings) -> Settings) {
        scope.launch { container.settings.update(change) }
    }
    val on = stringResource(R.string.state_on).uppercase()
    val off = stringResource(R.string.state_off).uppercase()
    Row(
        Modifier.fillMaxWidth().padding(start = Space.toolbarInset, end = Space.gutter, bottom = Space.base),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (input) SearchInput(query, onQuery, onSubmit, Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
        Box {
            IconAction(Icons.More, { menu = true })

            Menu(
                menu,
                listOf(
                    MenuItem(stringResource(R.string.fast_scroll), value = if (settings.fastScroll) on else off, valueOn = settings.fastScroll) {
                        edit { it.copy(fastScroll = !it.fastScroll, drawerSort = if (!it.fastScroll) DrawerSort.Name else it.drawerSort) }
                    },
                    MenuItem(stringResource(R.string.frequent_row), value = if (settings.frequentRow) on else off, valueOn = settings.frequentRow) {
                        edit { it.copy(frequentRow = !it.frequentRow) }
                    },
                    MenuItem(stringResource(R.string.drawer_sort), value = stringResource(settings.drawerSort.label).uppercase()) {
                        edit { it.copy(drawerSort = DrawerSort.entries[(it.drawerSort.ordinal + 1) % DrawerSort.entries.size]) }
                    },
                    MenuItem(stringResource(R.string.launcher_settings)) { onSettings() },
                ),
                { menu = false; focusManager.clearFocus() },
            )
        }
    }
}

@Composable
private fun TabStrip(tabs: List<Tab>, selected: Tab, onSelect: (Tab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.toolbarInset)
            .padding(bottom = Space.base)
            .clip(Radius.control)
            .background(Simplify.colors.surfaceAlt)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        tabs.forEach { tab ->
            val active = tab == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(32.dp)
                    .pressable({ onSelect(tab) }, shape = Radius.control, base = if (active) Simplify.colors.accent else Simplify.colors.surfaceAlt),
                contentAlignment = Alignment.Center,
            ) {
                RoleText(
                    when (tab) {
                        Tab.All -> stringResource(if (tabs.contains(Tab.Work)) R.string.tab_personal else R.string.tab_all)
                        Tab.Work -> stringResource(R.string.tab_work)
                        is Tab.Group -> tab.group.name
                    },
                    Simplify.type.label,
                    color = if (active) Simplify.colors.onAccent else Simplify.colors.textMuted,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun DrawerApp(
    app: AppInfo,
    state: HomeState,
    host: DrawerHost,
    modifier: Modifier = Modifier,
    labels: Boolean = state.settings.drawerLabels,
    iconPercent: Int = state.settings.drawerIconSize,
) {
    val bounds = remember { BoundsRef() }
    val icon by rememberAppIcon(app.key, state.overrides[app.key.flat]?.icon)
    IconCell(
        state.label(app),
        labels,
        state.settings.twoLineLabels,
        modifier
            .padding(vertical = Space.base)
            .trackBounds(bounds)
            .itemGestures(
                key = app.key,
                onTap = { host.launch(app, bounds.rect) },
                onLongPress = { at -> host.startDrag(app, icon, bounds.rect, at) },
                onDrag = host::dragTo,
                onDragEnd = host::endDrag,
            ),
        labelColor = Simplify.colors.text,
    ) {
        AppIcon(app, state, iconSize(iconPercent))
    }
}

@Composable
private fun DrawerFolderTile(group: DrawerGroup, state: HomeState, host: DrawerHost) {
    val members = state.apps.values.filter { it.key.flat in group.memberKeys }
    val children = members.take(4).map { ItemEntity(container = -1, type = ItemType.APP, app = it.key.flat) }
    IconCell(
        group.name,
        state.settings.drawerLabels,
        false,
        Modifier.padding(vertical = Space.base).pressable({ host.openDrawerFolder(group) }, shape = Radius.control),
        labelColor = Simplify.colors.text,
    ) {
        FolderIcon(children, state, iconSize(state.settings.drawerIconSize), cover = false)
    }
}

private fun letterOf(label: String): Char {
    val c = label.firstOrNull()?.uppercaseChar() ?: '#'
    return if (c.isLetter()) c else '#'
}

@Composable
private fun VerticalDrawer(state: HomeState, apps: List<AppInfo>, folders: List<DrawerGroup>, host: DrawerHost, showFrequent: Boolean) {
    val s = state.settings
    val grid = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val frequent = if (showFrequent && s.frequentRow) state.frequent else emptyList()

    val lead = (if (frequent.isNotEmpty()) 1 + frequent.size + 1 else 0) + folders.size
    val letters = remember(apps, state.overrides) {
        apps.mapIndexed { i, app -> letterOf(state.label(app)) to i }.distinctBy { it.first }
    }
    Row(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(s.drawerColumns),
            state = grid,
            modifier = Modifier.weight(1f).fillMaxHeight(),
            contentPadding = PaddingValues(horizontal = Space.gutter, vertical = Space.base),
        ) {
            if (frequent.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "frequent") {
                    SectionHeader(stringResource(R.string.section_frequent))
                }
                items(frequent, key = { "f:" + it.key.flat }) { DrawerApp(it, state, host) }
                item(span = { GridItemSpan(maxLineSpan) }, key = "all") {
                    SectionHeader(stringResource(R.string.section_all_apps), count = "${apps.size}")
                }
            }
            items(folders, key = { "g:${it.id}" }) { DrawerFolderTile(it, state, host) }
            items(apps, key = { it.key.flat }) { DrawerApp(it, state, host) }
        }
        if (s.fastScroll && s.drawerSort == DrawerSort.Name && letters.size > 1) {
            FastScroller(letters.map { it.first }) { index ->
                scope.launch { grid.scrollToItem(lead + letters[index].second) }
            }
        }
    }
}

@Composable
private fun FastScroller(letters: List<Char>, onLetter: (Int) -> Unit) {
    var active by remember { mutableIntStateOf(-1) }
    var height by remember { mutableIntStateOf(1) }
    fun pick(y: Float) {
        val i = (y / height * letters.size).toInt().coerceIn(0, letters.size - 1)
        if (i != active) { active = i; onLetter(i) }
    }
    Column(
        Modifier
            .width(28.dp)
            .fillMaxHeight()
            .padding(vertical = Space.base)
            .onGloballyPositioned { height = it.size.height.coerceAtLeast(1) }
            .pointerInput(letters) {
                detectVerticalDragGestures(
                    onDragStart = { pick(it.y) },
                    onVerticalDrag = { change, _ -> change.consume(); pick(change.position.y) },
                    onDragEnd = { active = -1 },
                    onDragCancel = { active = -1 },
                )
            }
            .pointerInput(letters) { detectTapGestures { pick(it.y); active = -1 } },
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        letters.forEachIndexed { i, letter ->
            RoleText(
                letter.toString(),
                Simplify.type.section,
                color = if (i == active) Simplify.colors.accent else Simplify.colors.textMuted,
            )
        }
    }
}

@Composable
private fun PagedDrawer(state: HomeState, apps: List<AppInfo>, folders: List<DrawerGroup>, host: DrawerHost) {
    val s = state.settings
    val perPage = s.drawerColumns * s.drawerRows
    val entries: List<Any> = folders + apps
    val pages = ((entries.size + perPage - 1) / perPage).coerceAtLeast(1)
    val pager = rememberPagerState { pages }
    Column(Modifier.fillMaxSize()) {
        HorizontalPager(pager, Modifier.weight(1f)) { page ->
            BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = Space.gutter)) {
                val cw = maxWidth / s.drawerColumns
                val ch = maxHeight / s.drawerRows
                Column {
                    entries.drop(page * perPage).take(perPage).chunked(s.drawerColumns).forEach { row ->
                        Row {
                            row.forEach { entry ->
                                Box(Modifier.size(cw, ch), contentAlignment = Alignment.Center) {
                                    when (entry) {
                                        is AppInfo -> DrawerApp(entry, state, host)
                                        is DrawerGroup -> DrawerFolderTile(entry, state, host)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().padding(Space.base), contentAlignment = Alignment.Center) {
            RoleText(pageLabel(pager.currentPage, pages), Simplify.type.meta, color = Simplify.colors.textMuted)
        }
    }
}

@Composable
private fun ListDrawer(state: HomeState, apps: List<AppInfo>, host: DrawerHost, showFrequent: Boolean) {
    val s = state.settings
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val frequent = if (showFrequent && s.frequentRow) state.frequent else emptyList()

    val rows = remember(apps, frequent, state.overrides) {
        buildList<Pair<String, Any>> {
            if (frequent.isNotEmpty()) add("frequent" to "frequent")
            var last: Char? = null
            apps.forEach { app ->
                val letter = letterOf(state.label(app))
                if (letter != last) { last = letter; add("h:$letter" to letter) }
                add(app.key.flat to app)
            }
        }
    }
    val letters = rows.mapIndexedNotNull { i, (_, v) -> (v as? Char)?.let { it to i } }
    Row(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).fillMaxHeight(), state = list) {
            rows.forEach { (key, value) ->
                item(key = key) {
                    when (value) {
                        is AppInfo -> ListRow(value, state, host)
                        is Char -> SectionHeader(value.toString())
                        else -> Column {
                            SectionHeader(stringResource(R.string.section_frequent))
                            AppGrid(frequent, state, host)
                        }
                    }
                }
            }
        }
        if (s.fastScroll && s.drawerSort == DrawerSort.Name && letters.size > 1) {
            FastScroller(letters.map { it.first }) { index -> scope.launch { list.scrollToItem(letters[index].second) } }
        }
    }
}

@Composable
private fun ListRow(app: AppInfo, state: HomeState, host: DrawerHost) {
    val bounds = remember { BoundsRef() }
    val icon by rememberAppIcon(app.key, state.overrides[app.key.flat]?.icon)
    Row(
        Modifier
            .fillMaxWidth()
            .height(Space.settingsRow)
            .trackBounds(bounds)
            .itemGestures(
                key = app.key,
                onTap = { host.launch(app, bounds.rect) },
                onLongPress = { at -> host.startDrag(app, icon, bounds.rect, at) },
                onDrag = host::dragTo,
                onDragEnd = host::endDrag,
            )
            .padding(horizontal = Space.screen),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.rowGap),
    ) {
        Box { AppIcon(app, state, 36.dp) }
        RoleText(state.label(app), Simplify.type.body, maxLines = 1)
    }
}

@Composable
fun SearchResultsList(query: String, state: HomeState, host: DrawerHost) {
    val container = LocalContext.current.container
    val results by produceState(SearchResults(), query, state.apps, state.settings.searchContacts) {
        value = withContext(Dispatchers.Default) {
            container.search.search(query, state.searchable(), state.settings.searchContacts)
        }
    }
    LazyColumn(Modifier.fillMaxSize()) {
        results.calculation?.let { value ->
            item(key = "calc") {
                SectionHeader(stringResource(R.string.section_calculator))
                Row(
                    Modifier.fillMaxWidth().height(Space.settingsRow).padding(horizontal = Space.screen),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RoleText("= $value", Simplify.type.title.copy(fontFamily = Simplify.type.value.fontFamily))
                }
            }
        }
        if (results.apps.isNotEmpty()) {
            item(key = "apps") {
                SectionHeader(stringResource(R.string.section_apps), count = "${results.apps.size}")
                AppGrid(results.apps.take(state.settings.drawerColumns * 2), state, host, labels = true)
            }
        }
        if (results.shortcuts.isNotEmpty()) {
            item(key = "sc-h") { SectionHeader(stringResource(R.string.section_shortcuts)) }
            items(results.shortcuts, key = { "sc:" + it.`package` + it.id }) { info ->
                val icon by rememberIcon(info.`package`, info.id) { it.icons.shortcut(info) }
                IconRow(icon, (info.shortLabel ?: info.longLabel ?: "").toString(), { host.launchShortcut(info, Rect.Zero) })
            }
        }
        if (results.contacts.isNotEmpty()) {
            item(key = "ct-h") { SectionHeader(stringResource(R.string.section_contacts)) }
            items(results.contacts, key = { "ct:" + it.uri }) { contact ->
                IconRow(null, contact.name, {
                    container.actions.start(Intent(Intent.ACTION_VIEW, contact.uri))
                })
            }
        }
        item(key = "web") {
            SectionHeader(stringResource(R.string.section_web))
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(Space.settingsRow)
                    .pressable({
                        container.actions.start(Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query))
                    }, shape = RoundedCornerShape(0.dp))
                    .padding(horizontal = Space.screen),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.rowGap),
            ) {
                Icon(Icons.Search, tint = Simplify.colors.textMuted, decorative = true)
                RoleText(stringResource(R.string.search_web, query), Simplify.type.body, maxLines = 1)
            }
        }
        if (results.isEmpty) {
            item(key = "empty") {
                RoleText(
                    stringResource(R.string.search_no_apps), Simplify.type.meta, color = Simplify.colors.textMuted,
                    modifier = Modifier.padding(horizontal = Space.screen, vertical = Space.base),
                )
            }
        }
    }
}

fun Rect.toAndroid(): AndroidRect = AndroidRect(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())

@Composable
fun AppGrid(
    apps: List<AppInfo>,
    state: HomeState,
    host: DrawerHost,
    columns: Int = state.settings.drawerColumns,
    labels: Boolean = state.settings.drawerLabels,
    iconPercent: Int = state.settings.drawerIconSize,
) {
    Column(Modifier.padding(horizontal = Space.gutter)) {
        apps.chunked(columns).forEach { row ->
            Row {
                row.forEach { app -> Box(Modifier.weight(1f)) { DrawerApp(app, state, host, Modifier.fillMaxWidth(), labels, iconPercent) } }
                repeat(columns - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun SearchInput(
    query: String,
    onQuery: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    focus: FocusRequester? = null,
) {
    Row(
        modifier
            .height(40.dp)
            .clip(Radius.control)
            .background(Simplify.colors.surfaceAlt)
            .padding(horizontal = Space.toolbarInset),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.base),
    ) {
        Icon(Icons.Search, tint = Simplify.colors.textMuted, size = 16.dp, decorative = true)
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                RoleText(stringResource(R.string.search_apps_hint), Simplify.type.body, color = Simplify.colors.textFaint, maxLines = 1)
            }
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = Simplify.type.body.copy(color = Simplify.colors.text),
                cursorBrush = SolidColor(Simplify.colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onSubmit() }),
                modifier = Modifier.fillMaxWidth().then(if (focus != null) Modifier.focusRequester(focus) else Modifier),
            )
        }
        if (query.isNotEmpty()) {
            RoleText(
                stringResource(R.string.action_clear).uppercase(), Simplify.type.section, color = Simplify.colors.accent,
                modifier = Modifier.pressable({ onQuery("") }).padding(Space.gutter),
            )
        }
    }
}
