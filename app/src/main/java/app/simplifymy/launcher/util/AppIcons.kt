package app.simplifymy.launcher.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.simplifymy.launcher.R

enum class AppIcon(@StringRes val label: Int, @ColorRes val background: Int) {
    Indigo(R.string.icon_indigo, R.color.icon_indigo),
    Carbon(R.string.icon_carbon, R.color.icon_carbon),
    Blue(R.string.icon_blue, R.color.icon_blue),
    Purple(R.string.icon_purple, R.color.icon_purple),
    Magenta(R.string.icon_magenta, R.color.icon_magenta),
    Teal(R.string.icon_teal, R.color.icon_teal),
    Green(R.string.icon_green, R.color.icon_green),
}

enum class IconTarget(private val prefix: String, @DrawableRes val foreground: Int, val choices: List<AppIcon>, val default: AppIcon) {
    Launcher(".", R.drawable.ic_launcher_foreground, AppIcon.entries, AppIcon.Indigo),
    Weather(
        ".Weather", R.drawable.ic_weather_foreground,
        listOf(AppIcon.Teal, AppIcon.Carbon, AppIcon.Blue, AppIcon.Purple, AppIcon.Magenta, AppIcon.Green), AppIcon.Teal,
    ),
    ;

    fun alias(icon: AppIcon): String = prefix + icon.name
}

private const val NAMESPACE = "app.simplifymy.launcher"

object AppIcons {
    fun apply(context: Context, target: IconTarget, icon: AppIcon) {
        val chosen = icon.takeIf { it in target.choices } ?: target.default
        if (current(context, target) == chosen) return
        val manager = context.packageManager
        val packageName = context.packageName
        fun set(entry: AppIcon, enabled: Boolean) = manager.setComponentEnabledSetting(
            ComponentName(packageName, NAMESPACE + target.alias(entry)),
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
        set(chosen, true)
        target.choices.filter { it != chosen }.forEach { set(it, false) }
    }

    private fun current(context: Context, target: IconTarget): AppIcon {
        val manager = context.packageManager
        val packageName = context.packageName
        return target.choices.firstOrNull { entry ->
            manager.getComponentEnabledSetting(ComponentName(packageName, NAMESPACE + target.alias(entry))) ==
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } ?: target.default
    }
}
