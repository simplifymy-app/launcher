package app.simplifymy.launcher.core.workspace

import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import app.simplifymy.launcher.core.apps.AppInfo
import app.simplifymy.launcher.core.apps.AppKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class Occupancy(val columns: Int, val rows: Int) {
    private val used = Array(columns) { BooleanArray(rows) }

    fun mark(item: ItemEntity, value: Boolean = true) {
        for (cx in item.x until (item.x + item.spanX).coerceAtMost(columns))
            for (cy in item.y until (item.y + item.spanY).coerceAtMost(rows))
                if (cx >= 0 && cy >= 0) used[cx][cy] = value
    }

    fun fits(x: Int, y: Int, spanX: Int, spanY: Int): Boolean {
        if (x < 0 || y < 0 || x + spanX > columns || y + spanY > rows) return false
        for (cx in x until x + spanX) for (cy in y until y + spanY) if (used[cx][cy]) return false
        return true
    }

    fun firstFree(spanX: Int = 1, spanY: Int = 1): Pair<Int, Int>? {
        for (cy in 0 until rows) for (cx in 0 until columns) if (fits(cx, cy, spanX, spanY)) return cx to cy
        return null
    }

    fun nearest(x: Int, y: Int, spanX: Int, spanY: Int): Pair<Int, Int>? {
        var best: Pair<Int, Int>? = null
        var bestDistance = Int.MAX_VALUE
        for (cy in 0 until rows) for (cx in 0 until columns) {
            if (!fits(cx, cy, spanX, spanY)) continue
            val d = (cx - x) * (cx - x) + (cy - y) * (cy - y)
            if (d < bestDistance) { bestDistance = d; best = cx to cy }
        }
        return best
    }

    companion object {
        fun of(items: List<ItemEntity>, columns: Int, rows: Int, except: Long? = null) =
            Occupancy(columns, rows).apply { items.filter { it.id != except }.forEach { mark(it) } }
    }
}

class WorkspaceRepository(context: Context, private val dao: WorkspaceDao) {
    private val app = context.applicationContext
    private val lock = Mutex()
    private val prefs = app.getSharedPreferences("workspace", Context.MODE_PRIVATE)

    val items: Flow<List<ItemEntity>> = dao.items()
    val overrides: Flow<List<AppOverride>> = dao.overrides()
    val groups: Flow<List<DrawerGroup>> = dao.groups()

    suspend fun allItems() = dao.allItems()

    suspend fun seedIfEmpty(apps: List<AppInfo>, dockColumns: Int) = lock.withLock {
        if (prefs.getBoolean("seeded", false) || apps.isEmpty()) return@withLock
        prefs.edit().putBoolean("seeded", true).apply()
        if (dao.allItems().isNotEmpty()) return@withLock
        val intents = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING),
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER),
            Intent(MediaStore.ACTION_IMAGE_CAPTURE),
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY),
        )
        val packages = intents.mapNotNull { intent ->
            app.packageManager.resolveActivity(intent, 0)?.activityInfo?.packageName
                ?.takeIf { it != "android" }
        }.distinct()
        val primary = apps.filterNot { it.work }
        val dock = packages.mapNotNull { pkg -> primary.firstOrNull { it.packageName == pkg } }.take(dockColumns)
        dao.insertAll(dock.mapIndexed { i, info ->
            ItemEntity(container = Container.DOCK, x = i, type = ItemType.APP, app = info.key.flat)
        })
    }

    suspend fun move(item: ItemEntity, container: Long, page: Int, x: Int, y: Int) = lock.withLock {
        val current = dao.item(item.id) ?: return@withLock
        val previous = current.container
        dao.update(current.copy(container = container, page = page, x = x, y = y))
        if (previous >= 0 && previous != container) collapseFolder(previous)
    }

    suspend fun insert(item: ItemEntity): Long = lock.withLock { dao.insert(item) }

    suspend fun update(item: ItemEntity) = lock.withLock { dao.update(item) }

    suspend fun remove(item: ItemEntity) = lock.withLock {
        if (item.isFolder) dao.deleteIds(dao.inContainer(item.id).map { it.id })
        dao.delete(item)
        if (item.container >= 0) collapseFolder(item.container)
    }

    suspend fun addToDesktop(item: ItemEntity, startPage: Int, pages: Int, columns: Int, rows: Int): Int = lock.withLock {
        val desktop = dao.inContainer(Container.DESKTOP)
        val order = (startPage until pages) + (0 until startPage)
        for (page in order) {
            val spot = Occupancy.of(desktop.filter { it.page == page }, columns, rows)
                .firstFree(item.spanX, item.spanY) ?: continue
            dao.insert(item.copy(id = 0, container = Container.DESKTOP, page = page, x = spot.first, y = spot.second))
            return@withLock page
        }
        dao.insert(item.copy(id = 0, container = Container.DESKTOP, page = pages, x = 0, y = 0))
        pages
    }

    suspend fun createFolder(target: ItemEntity, dropped: ItemEntity, title: String): Long = lock.withLock {
        val folderId = dao.insert(
            ItemEntity(
                container = target.container, page = target.page, x = target.x, y = target.y,
                type = ItemType.FOLDER, title = title,
            )
        )
        dao.update(target.copy(container = folderId, page = 0, x = 0, y = 0))
        val previous = dropped.container
        if (dropped.id == 0L) dao.insert(dropped.copy(container = folderId, page = 0, x = 1, y = 0))
        else dao.update(dropped.copy(container = folderId, page = 0, x = 1, y = 0))
        if (previous >= 0 && previous != folderId && dropped.id != 0L) collapseFolder(previous)
        folderId
    }

    suspend fun addToFolder(folder: ItemEntity, dropped: ItemEntity) = lock.withLock {
        val rank = dao.inContainer(folder.id).maxOfOrNull { it.x + 1 } ?: 0
        val previous = dropped.container
        if (dropped.id == 0L) dao.insert(dropped.copy(container = folder.id, page = 0, x = rank, y = 0))
        else dao.update(dropped.copy(container = folder.id, page = 0, x = rank, y = 0))
        if (previous >= 0 && previous != folder.id && dropped.id != 0L) collapseFolder(previous)
    }

    suspend fun reorderFolder(folderId: Long, ordered: List<ItemEntity>) = lock.withLock {
        dao.updateAll(ordered.mapIndexed { i, item -> item.copy(container = folderId, x = i, y = 0) })
    }

    private suspend fun collapseFolder(folderId: Long) {
        val folder = dao.item(folderId) ?: return
        val children = dao.inContainer(folderId).sortedBy { it.x }
        when (children.size) {
            0 -> dao.delete(folder)
            1 -> {
                dao.update(children[0].copy(container = folder.container, page = folder.page, x = folder.x, y = folder.y))
                dao.delete(folder)
            }
            else -> dao.updateAll(children.mapIndexed { i, c -> c.copy(x = i) })
        }
    }

    suspend fun removePage(page: Int) = lock.withLock {
        val desktop = dao.inContainer(Container.DESKTOP)
        val doomed = desktop.filter { it.page == page }
        doomed.filter { it.isFolder }.forEach { folder -> dao.deleteIds(dao.inContainer(folder.id).map { it.id }) }
        dao.deleteIds(doomed.map { it.id })
        dao.updateAll(desktop.filter { it.page > page }.map { it.copy(page = it.page - 1) })
        doomed.filter { it.isWidget }.map { it.widgetId }
    }

    suspend fun swapPages(a: Int, b: Int) = lock.withLock {
        val desktop = dao.inContainer(Container.DESKTOP)
        dao.updateAll(desktop.filter { it.page == a || it.page == b }.map { it.copy(page = if (it.page == a) b else a) })
    }

    suspend fun prune(installed: Set<String>, installedPackages: Set<String>) = lock.withLock {
        val all = dao.allItems()
        val dead = all.filter { item ->
            when (item.type) {
                ItemType.APP -> item.app !in installed
                ItemType.SHORTCUT -> item.packageName !in installedPackages
                else -> false
            }
        }
        if (dead.isEmpty()) return@withLock
        dao.deleteIds(dead.map { it.id })
        dead.map { it.container }.filter { it >= 0 }.distinct().forEach { collapseFolder(it) }
    }

    suspend fun editOverride(key: AppKey, edit: (AppOverride) -> AppOverride) = lock.withLock {
        dao.upsert(edit(dao.override(key.flat) ?: AppOverride(key.flat)))
    }

    suspend fun recordLaunch(key: AppKey) = editOverride(key) {
        it.copy(launches = it.launches + 1, lastLaunch = System.currentTimeMillis())
    }

    suspend fun saveGroup(group: DrawerGroup): Long = dao.upsert(group)
    suspend fun deleteGroup(group: DrawerGroup) = dao.delete(group)

    suspend fun snapshot() = Triple(dao.allItems(), dao.allOverrides(), dao.allGroups())

    suspend fun restore(items: List<ItemEntity>, overrides: List<AppOverride>, groups: List<DrawerGroup>) =
        lock.withLock { dao.replaceAll(items, overrides, groups) }
}
