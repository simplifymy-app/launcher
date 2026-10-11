package app.simplifymy.launcher.core.apps

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import java.text.Collator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppKey(val component: ComponentName, val user: Long) {
    val flat: String get() = "${component.flattenToString()}#$user"

    companion object {
        fun parse(flat: String): AppKey? {
            val component = ComponentName.unflattenFromString(flat.substringBefore('#')) ?: return null
            return AppKey(component, flat.substringAfter('#', "0").toLongOrNull() ?: 0)
        }
    }
}

data class AppInfo(
    val key: AppKey,
    val label: String,
    val installed: Long,
    val work: Boolean,
) {
    val packageName: String get() = key.component.packageName
}

class AppRepository(context: Context, private val scope: CoroutineScope) {
    private val app = context.applicationContext
    val launcherApps: LauncherApps = app.getSystemService(LauncherApps::class.java)
    private val users: UserManager = app.getSystemService(UserManager::class.java)
    private val ownSerial = users.getSerialNumberForUser(Process.myUserHandle())

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())

    val apps: StateFlow<List<AppInfo>> = _apps

    private val _version = MutableStateFlow(0)

    val version: StateFlow<Int> = _version

    private val _removed = MutableSharedFlow<String>(extraBufferCapacity = 8)

    val removed: SharedFlow<String> = _removed

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) {
            _removed.tryEmit(packageName)
            reload()
        }
        override fun onPackageAdded(packageName: String, user: UserHandle) = reload()
        override fun onPackageChanged(packageName: String, user: UserHandle) = reload()
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = reload()
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = reload()
        override fun onShortcutsChanged(packageName: String, shortcuts: MutableList<ShortcutInfo>, user: UserHandle) {
            _version.value++
        }
    }

    init {
        launcherApps.registerCallback(callback)
        reload()
    }

    private fun reload() {
        scope.launch {
            _apps.value = withContext(Dispatchers.IO) { query() }
            _version.value++
        }
    }

    private fun query(): List<AppInfo> {
        val collator = Collator.getInstance()
        return users.userProfiles.flatMap { user ->
            val serial = users.getSerialNumberForUser(user)
            launcherApps.getActivityList(null, user).map { it.toInfo(serial) }
        }.sortedWith(compareBy(collator) { it.label })
    }

    private fun LauncherActivityInfo.toInfo(serial: Long) = AppInfo(
        key = AppKey(componentName, serial),
        label = label.toString(),
        installed = runCatching { firstInstallTime }.getOrDefault(0),
        work = serial != ownSerial,
    )

    fun user(serial: Long): UserHandle? = users.getUserForSerialNumber(serial)

    fun serial(user: UserHandle): Long = users.getSerialNumberForUser(user)

    val hasWorkProfile: Boolean get() = users.userProfiles.size > 1

    fun activity(key: AppKey): LauncherActivityInfo? {
        val user = user(key.user) ?: return null
        return runCatching { launcherApps.resolveActivity(Intent().setComponent(key.component), user) }.getOrNull()
            ?: launcherApps.getActivityList(key.component.packageName, user).firstOrNull { it.componentName == key.component }
    }

    fun shortcuts(packageName: String, userSerial: Long): List<ShortcutInfo> {
        if (!launcherApps.hasShortcutHostPermission()) return emptyList()
        val user = user(userSerial) ?: return emptyList()
        val query = LauncherApps.ShortcutQuery()
            .setPackage(packageName)
            .setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST,
            )
        return runCatching { launcherApps.getShortcuts(query, user) }.getOrNull().orEmpty()
            .sortedBy { it.rank }
            .take(5)
    }

    fun pinnedShortcut(packageName: String, id: String, userSerial: Long): ShortcutInfo? {
        if (!launcherApps.hasShortcutHostPermission()) return null
        val user = user(userSerial) ?: return null
        val query = LauncherApps.ShortcutQuery()
            .setPackage(packageName)
            .setShortcutIds(listOf(id))
            .setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST,
            )
        return runCatching { launcherApps.getShortcuts(query, user) }.getOrNull()?.firstOrNull()
    }

    fun unpin(packageName: String, id: String, userSerial: Long) {
        if (!launcherApps.hasShortcutHostPermission()) return
        val user = user(userSerial) ?: return
        val query = LauncherApps.ShortcutQuery().setPackage(packageName)
            .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
        val pinned = runCatching { launcherApps.getShortcuts(query, user) }.getOrNull().orEmpty().map { it.id } - id
        runCatching { launcherApps.pinShortcuts(packageName, pinned, user) }
    }

    fun allShortcuts(): List<ShortcutInfo> {
        if (!launcherApps.hasShortcutHostPermission()) return emptyList()
        val query = LauncherApps.ShortcutQuery().setQueryFlags(
            LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST,
        )
        return users.userProfiles.flatMap { user ->
            runCatching { launcherApps.getShortcuts(query, user) }.getOrNull().orEmpty()
        }
    }
}
