package app.simplifymy.launcher.core.actions

import androidx.annotation.StringRes
import app.simplifymy.launcher.R
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface Action {
    @get:StringRes val label: Int

    @Serializable @SerialName("none")
    data object None : Action { override val label get() = R.string.action_none }

    @Serializable @SerialName("drawer")
    data object Drawer : Action { override val label get() = R.string.action_drawer }

    @Serializable @SerialName("search")
    data object Search : Action { override val label get() = R.string.action_search }

    @Serializable @SerialName("notifications")
    data object Notifications : Action { override val label get() = R.string.action_notifications }

    @Serializable @SerialName("quick_settings")
    data object QuickSettings : Action { override val label get() = R.string.action_quick_settings }

    @Serializable @SerialName("lock")
    data object LockScreen : Action { override val label get() = R.string.action_lock }

    @Serializable @SerialName("recents")
    data object Recents : Action { override val label get() = R.string.action_recents }

    @Serializable @SerialName("overview")
    data object Overview : Action { override val label get() = R.string.action_overview }

    @Serializable @SerialName("default_page")
    data object DefaultPage : Action { override val label get() = R.string.action_default_page }

    @Serializable @SerialName("toggle_dock")
    data object ToggleDock : Action { override val label get() = R.string.action_toggle_dock }

    @Serializable @SerialName("settings")
    data object LauncherSettings : Action { override val label get() = R.string.action_settings }

    @Serializable @SerialName("app")
    data class OpenApp(val component: String, val user: Long) : Action {
        override val label get() = R.string.action_open_app
    }

    @Serializable @SerialName("shortcut")
    data class OpenShortcut(val packageName: String, val id: String, val user: Long, val title: String) : Action {
        override val label get() = R.string.action_open_shortcut
    }

    companion object {
        val fixed: List<Action> = listOf(
            None, Drawer, Search, Notifications, QuickSettings, LockScreen, Recents,
            Overview, DefaultPage, ToggleDock, LauncherSettings,
        )
    }
}
