package app.simplifymy.launcher.core.actions

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import app.simplifymy.launcher.R
import app.simplifymy.launcher.SettingsActivity
import app.simplifymy.launcher.core.apps.AppKey
import app.simplifymy.launcher.core.apps.AppRepository
import app.simplifymy.launcher.core.workspace.WorkspaceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

class LauncherAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() { instance = this }
    override fun onUnbind(intent: Intent?): Boolean { instance = null; return super.onUnbind(intent) }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    companion object {
        @Volatile var instance: LauncherAccessibilityService? = null
            private set
    }
}

enum class UiAction { Drawer, Search, Overview, DefaultPage, ToggleDock }

class ActionRunner(
    private val context: Context,
    private val apps: AppRepository,
    private val workspace: WorkspaceRepository,
    private val scope: CoroutineScope,
) {
    private val _ui = MutableSharedFlow<UiAction>(extraBufferCapacity = 4)
    val ui: SharedFlow<UiAction> = _ui

    fun run(action: Action, bounds: Rect? = null, options: Bundle? = null) {
        when (action) {
            Action.None -> Unit
            Action.Drawer -> _ui.tryEmit(UiAction.Drawer)
            Action.Search -> _ui.tryEmit(UiAction.Search)
            Action.Overview -> _ui.tryEmit(UiAction.Overview)
            Action.DefaultPage -> _ui.tryEmit(UiAction.DefaultPage)
            Action.ToggleDock -> _ui.tryEmit(UiAction.ToggleDock)
            Action.Notifications -> global(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS) || statusBar("expandNotificationsPanel")
            Action.QuickSettings -> global(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS) || statusBar("expandSettingsPanel")
            Action.LockScreen -> global(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN) || askForAccessibility()
            Action.Recents -> global(AccessibilityService.GLOBAL_ACTION_RECENTS) || askForAccessibility()
            Action.LauncherSettings -> start(Intent(context, SettingsActivity::class.java))
            is Action.OpenApp -> ComponentName.unflattenFromString(action.component)
                ?.let { launch(AppKey(it, action.user), bounds, options) }
            is Action.OpenShortcut -> launchShortcut(action.packageName, action.id, action.user, bounds, options)
        }
    }

    fun launch(key: AppKey, bounds: Rect? = null, options: Bundle? = null) {
        val user = apps.user(key.user) ?: return
        runCatching { apps.launcherApps.startMainActivity(key.component, user, bounds, options) }
            .onSuccess { scope.launch { workspace.recordLaunch(key) } }
            .onFailure { toast(R.string.error_launch) }
    }

    fun launchShortcut(packageName: String, id: String, user: Long, bounds: Rect? = null, options: Bundle? = null) {
        val handle = apps.user(user) ?: return
        runCatching { apps.launcherApps.startShortcut(packageName, id, bounds, options, handle) }
            .onFailure { toast(R.string.error_launch) }
    }

    fun appInfo(key: AppKey) {
        val user = apps.user(key.user) ?: return
        runCatching { apps.launcherApps.startAppDetailsActivity(key.component, user, null, null) }
    }

    fun uninstall(packageName: String) =
        start(Intent(Intent.ACTION_DELETE, Uri.fromParts("package", packageName, null)))

    fun start(intent: Intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            toast(R.string.error_launch)
        }
    }

    val accessibilityEnabled: Boolean get() = LauncherAccessibilityService.instance != null

    private fun global(action: Int): Boolean =
        LauncherAccessibilityService.instance?.performGlobalAction(action) == true

    @SuppressLint("WrongConstant")
    private fun statusBar(method: String): Boolean = runCatching {
        val service = context.getSystemService("statusbar")
        service.javaClass.getMethod(method).invoke(service)
        true
    }.onFailure { Log.w("ActionRunner", "status bar $method failed", it) }.getOrDefault(false)

    private fun askForAccessibility(): Boolean {
        toast(R.string.needs_accessibility)
        start(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        return false
    }

    private fun toast(message: Int) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
