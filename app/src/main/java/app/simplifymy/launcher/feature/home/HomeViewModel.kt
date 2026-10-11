package app.simplifymy.launcher.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.simplifymy.launcher.AppContainer
import app.simplifymy.launcher.core.apps.AppInfo
import app.simplifymy.launcher.core.apps.AppKey
import app.simplifymy.launcher.core.settings.DrawerSort
import app.simplifymy.launcher.core.settings.Settings
import app.simplifymy.launcher.core.workspace.AppOverride
import app.simplifymy.launcher.core.workspace.Container
import app.simplifymy.launcher.core.workspace.DrawerGroup
import app.simplifymy.launcher.core.workspace.GroupKind
import app.simplifymy.launcher.core.workspace.ItemEntity
import app.simplifymy.launcher.core.workspace.ItemType
import app.simplifymy.launcher.core.workspace.Occupancy
import java.text.Collator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HomeState(
    val settings: Settings = Settings(),
    val desktop: Map<Int, List<ItemEntity>> = emptyMap(),
    val dock: List<ItemEntity> = emptyList(),
    val folders: Map<Long, List<ItemEntity>> = emptyMap(),
    val apps: Map<String, AppInfo> = emptyMap(),
    val overrides: Map<String, AppOverride> = emptyMap(),
    val groups: List<DrawerGroup> = emptyList(),
    val drawer: List<AppInfo> = emptyList(),
    val frequent: List<AppInfo> = emptyList(),
    val loaded: Boolean = false,
) {
    val pageCount: Int
        get() = maxOf(settings.desktopPages, (desktop.keys.maxOrNull() ?: -1) + 1, 1)

    fun label(item: ItemEntity): String = item.title
        ?: item.app?.let { key -> overrides[key]?.label ?: apps[key]?.label }
        ?: ""

    fun label(app: AppInfo): String = overrides[app.key.flat]?.label ?: app.label

    fun iconRef(item: ItemEntity): String? = item.icon ?: item.app?.let { overrides[it]?.icon }

    fun searchable(): List<AppInfo> = apps.values.filter { settings.hiddenInSearch || overrides[it.key.flat]?.hidden != true }
}

class HomeViewModel(val container: AppContainer) : ViewModel() {
    private val workspace = container.workspace
    val actions = container.actions

    val state: StateFlow<HomeState> = combine(
        container.settings.settings,
        workspace.items,
        container.apps.apps,
        workspace.overrides,
        workspace.groups,
    ) { settings, items, apps, overrides, groups ->
        build(settings, items, apps, overrides.associateBy { it.key }, groups)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeState())

    private fun build(
        settings: Settings,
        items: List<ItemEntity>,
        apps: List<AppInfo>,
        overrides: Map<String, AppOverride>,
        groups: List<DrawerGroup>,
    ): HomeState {
        val byKey = apps.associateBy { it.key.flat }
        val inDrawerFolders = groups.filter { it.kind == GroupKind.FOLDER }.flatMap { it.memberKeys }.toSet()
        val visible = apps.filter { overrides[it.key.flat]?.hidden != true }
        val sorted = when (settings.drawerSort) {
            DrawerSort.Name -> visible.sortedWith(compareBy(Collator.getInstance()) { overrides[it.key.flat]?.label ?: it.label })
            DrawerSort.Installed -> visible.sortedByDescending { it.installed }
            DrawerSort.Usage -> visible.sortedByDescending { overrides[it.key.flat]?.launches ?: 0 }
        }
        val frequent = visible
            .filter { (overrides[it.key.flat]?.launches ?: 0) > 0 }
            .sortedByDescending { overrides[it.key.flat]?.lastLaunch ?: 0 }
            .sortedByDescending { overrides[it.key.flat]?.launches ?: 0 }
            .take(settings.drawerColumns)
        return HomeState(
            settings = settings,
            desktop = items.filter { it.container == Container.DESKTOP }.groupBy { it.page },
            dock = items.filter { it.container == Container.DOCK },
            folders = items.filter { it.container >= 0 }.groupBy { it.container }.mapValues { (_, v) -> v.sortedBy { it.x } },
            apps = byKey,
            overrides = overrides,
            groups = groups,
            drawer = sorted.filter { it.key.flat !in inDrawerFolders },
            frequent = frequent,
            loaded = apps.isNotEmpty(),
        )
    }

    fun move(item: ItemEntity, container: Long, page: Int, x: Int, y: Int) = launch {
        if (item.id == 0L) workspace.insert(item.copy(container = container, page = page, x = x, y = y))
        else workspace.move(item, container, page, x, y)
    }

    fun createFolder(target: ItemEntity, dropped: ItemEntity, title: String) = launch {
        workspace.createFolder(target, dropped, title)
    }

    fun addToFolder(folder: ItemEntity, dropped: ItemEntity) = launch { workspace.addToFolder(folder, dropped) }

    fun reorderFolder(folderId: Long, ordered: List<ItemEntity>) = launch { workspace.reorderFolder(folderId, ordered) }

    fun remove(item: ItemEntity) = launch {
        if (item.isWidget) container.widgets.delete(item.widgetId)
        if (item.type == ItemType.SHORTCUT) {
            val stillUsed = workspace.allItems().count { it.type == ItemType.SHORTCUT && it.shortcutId == item.shortcutId && it.packageName == item.packageName } > 1
            if (!stillUsed) withContext(Dispatchers.IO) {
                container.apps.unpin(item.packageName.orEmpty(), item.shortcutId.orEmpty(), item.user)
            }
        }
        workspace.remove(item)
    }

    fun update(item: ItemEntity) = launch { workspace.update(item) }

    fun addToDesktop(item: ItemEntity, startPage: Int) = launch {
        val s = state.value.settings
        val page = workspace.addToDesktop(item, startPage, state.value.pageCount, s.desktopColumns, s.desktopRows)
        if (page >= s.desktopPages) container.settings.update { it.copy(desktopPages = page + 1) }
    }

    fun setHidden(key: AppKey, hidden: Boolean) = launch { workspace.editOverride(key) { it.copy(hidden = hidden) } }

    fun editApp(key: AppKey, label: String?, icon: String?) = launch {
        workspace.editOverride(key) { it.copy(label = label?.takeIf { l -> l.isNotBlank() }, icon = icon) }
    }

    fun settings(edit: (Settings) -> Settings) = launch { container.settings.update(edit) }

    fun addPage() = settings { it.copy(desktopPages = state.value.pageCount + 1) }

    fun removePage(page: Int) = launch {
        val widgets = state.value.desktop[page].orEmpty().filter { it.isWidget }.map { it.widgetId }
        workspace.removePage(page)
        widgets.forEach(container.widgets::delete)
        container.settings.update {
            val pages = (state.value.pageCount - 1).coerceAtLeast(1)
            it.copy(desktopPages = pages, defaultPage = it.defaultPage.coerceAtMost(pages - 1).let { d -> if (d > page) d - 1 else d })
        }
    }

    fun movePage(from: Int, to: Int) = launch {
        if (to < 0 || to >= state.value.pageCount) return@launch
        workspace.swapPages(from, to)
        container.settings.update {
            it.copy(defaultPage = when (it.defaultPage) { from -> to; to -> from; else -> it.defaultPage })
        }
    }

    fun place(item: ItemEntity, page: Int) {
        val spot = freeCell(page, item.spanX, item.spanY)
        if (spot != null) move(item, Container.DESKTOP, page, spot.first, spot.second) else addToDesktop(item, page)
    }

    fun freeCell(page: Int, spanX: Int, spanY: Int): Pair<Int, Int>? {
        val s = state.value.settings
        return Occupancy.of(state.value.desktop[page].orEmpty(), s.desktopColumns, s.desktopRows).firstFree(spanX, spanY)
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
