package app.simplifymy.launcher

import android.app.Application
import android.content.Context
import app.simplifymy.launcher.core.actions.ActionRunner
import app.simplifymy.launcher.core.apps.AppRepository
import app.simplifymy.launcher.core.backup.Backup
import app.simplifymy.launcher.core.icons.IconLoader
import app.simplifymy.launcher.core.search.SearchEngine
import app.simplifymy.launcher.core.settings.SettingsStore
import app.simplifymy.launcher.core.weather.WeatherRepository
import app.simplifymy.launcher.core.widgets.Widgets
import app.simplifymy.launcher.core.workspace.LauncherDatabase
import app.simplifymy.launcher.core.workspace.WorkspaceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LauncherApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

val Context.container: AppContainer
    get() = (applicationContext as LauncherApp).container

class AppContainer(context: Context) {
    val app: Context = context.applicationContext
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val settings = SettingsStore(app, scope)
    private val database = LauncherDatabase.create(app)
    val workspace = WorkspaceRepository(app, database.dao())
    val apps = AppRepository(app, scope)
    val icons = IconLoader(app, apps)
    val widgets = Widgets(app)
    val actions = ActionRunner(app, apps, workspace, scope)
    val search = SearchEngine(app, apps)
    val backup = Backup(app, settings, workspace)
    val weather = WeatherRepository(app, settings, scope)

    val homePressed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    init {
        scope.launch {
            combine(apps.apps, apps.version, settings.settings.map { it.iconShape to it.iconPack }.distinctUntilChanged()) { list, _, look ->
                list to look
            }.collectLatest { (list, look) ->
                icons.invalidate()
                icons.configure(look.first, look.second)
                withContext(Dispatchers.IO) { list.forEach { icons.app(it.key) } }
            }
        }
        scope.launch {
            val list = apps.apps.first { it.isNotEmpty() }
            workspace.seedIfEmpty(list, settings.settings.value.dockColumns)
            prune()
        }
        scope.launch {
            apps.removed.collect { pkg ->
                apps.apps.first { list -> list.none { it.packageName == pkg } }
                prune()
            }
        }
    }

    private suspend fun prune() {
        val list = apps.apps.value
        if (list.isEmpty()) return
        workspace.prune(list.map { it.key.flat }.toSet(), list.map { it.packageName }.toSet())
    }
}
