package app.simplifymy.launcher.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import app.simplifymy.launcher.R

enum class AppIcon(
    val alias: String,
    @StringRes val label: Int,
    @ColorRes val background: Int,
) {
    Indigo(".Indigo", R.string.icon_indigo, R.color.icon_indigo),
    Carbon(".Carbon", R.string.icon_carbon, R.color.icon_carbon),
    Blue(".Blue", R.string.icon_blue, R.color.icon_blue),
    Purple(".Purple", R.string.icon_purple, R.color.icon_purple),
    Magenta(".Magenta", R.string.icon_magenta, R.color.icon_magenta),
    Teal(".Teal", R.string.icon_teal, R.color.icon_teal),
    Green(".Green", R.string.icon_green, R.color.icon_green),
}

private const val NAMESPACE = "app.simplifymy.launcher"

object AppIcons {
    fun apply(context: Context, icon: AppIcon) {
        if (current(context) == icon) return
        val manager = context.packageManager
        val packageName = context.packageName
        fun set(entry: AppIcon, enabled: Boolean) = manager.setComponentEnabledSetting(
            ComponentName(packageName, NAMESPACE + entry.alias),
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
        set(icon, true)
        AppIcon.entries.filter { it != icon }.forEach { set(it, false) }
    }

    fun current(context: Context): AppIcon {
        val manager = context.packageManager
        val packageName = context.packageName
        return AppIcon.entries.firstOrNull { entry ->
            manager.getComponentEnabledSetting(ComponentName(packageName, NAMESPACE + entry.alias)) ==
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } ?: AppIcon.Indigo
    }
}
