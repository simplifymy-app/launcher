package app.simplifymy.launcher.core.badges

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.os.UserManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object Badges {
    internal val counts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val all: StateFlow<Map<String, Int>> = counts

    fun key(packageName: String, user: Long) = "$packageName#$user"

    fun granted(context: Context): Boolean {
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
        val mine = ComponentName(context, BadgeListenerService::class.java)
        return flat.split(':').any { ComponentName.unflattenFromString(it) == mine }
    }
}

class BadgeListenerService : NotificationListenerService() {
    override fun onListenerConnected() = recount()
    override fun onNotificationPosted(sbn: StatusBarNotification?) = recount()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = recount()

    override fun onListenerDisconnected() {
        Badges.counts.value = emptyMap()
    }

    private fun recount() {
        val active = runCatching { activeNotifications }.getOrNull() ?: return
        val users = getSystemService(UserManager::class.java)
        Badges.counts.value = active
            .filter { it.counts() }
            .groupBy { Badges.key(it.packageName, users.getSerialNumberForUser(it.user)) }
            .mapValues { (_, list) -> list.sumOf { it.notification.number.coerceAtLeast(1) } }
    }

    private fun StatusBarNotification.counts(): Boolean {
        val n = notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return false
        return !isOngoing
    }
}
