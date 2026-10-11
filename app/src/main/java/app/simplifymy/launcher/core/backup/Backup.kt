package app.simplifymy.launcher.core.backup

import android.content.Context
import android.net.Uri
import app.simplifymy.launcher.core.settings.Settings
import app.simplifymy.launcher.core.settings.SettingsJson
import app.simplifymy.launcher.core.settings.SettingsStore
import app.simplifymy.launcher.core.workspace.AppOverride
import app.simplifymy.launcher.core.workspace.DrawerGroup
import app.simplifymy.launcher.core.workspace.ItemEntity
import app.simplifymy.launcher.core.workspace.WorkspaceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
data class BackupFile(
    val version: Int = 1,
    val settings: Settings,
    val items: List<ItemEntity>,
    val overrides: List<AppOverride>,
    val groups: List<DrawerGroup>,
)

class Backup(
    private val context: Context,
    private val settings: SettingsStore,
    private val workspace: WorkspaceRepository,
) {
    suspend fun export(uri: Uri) = withContext(Dispatchers.IO) {
        val (items, overrides, groups) = workspace.snapshot()
        val file = BackupFile(settings = settings.settings.value, items = items, overrides = overrides, groups = groups)
        context.contentResolver.openOutputStream(uri, "wt")!!.use {
            it.write(SettingsJson.encodeToString(BackupFile.serializer(), file).toByteArray())
        }
    }

    suspend fun import(uri: Uri): Int = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
        val file = SettingsJson.decodeFromString(BackupFile.serializer(), text)
        val lost = file.items.filter { it.isWidget && it.widgetId >= 0 }
        workspace.restore(file.items - lost.toSet(), file.overrides, file.groups)
        settings.replace(file.settings)
        lost.size
    }
}
