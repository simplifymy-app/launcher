package app.simplifymy.launcher.ui.launcher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.simplifymy.launcher.R
import app.simplifymy.launcher.container
import app.simplifymy.launcher.core.actions.Action
import app.simplifymy.launcher.ui.component.BottomSheet
import app.simplifymy.launcher.ui.component.Icon
import app.simplifymy.launcher.ui.component.Icons
import app.simplifymy.launcher.ui.component.SectionHeader
import app.simplifymy.launcher.ui.component.SheetKicker
import app.simplifymy.launcher.ui.component.pressable
import app.simplifymy.launcher.ui.theme.RoleText
import app.simplifymy.launcher.ui.theme.Simplify
import app.simplifymy.launcher.ui.theme.Space

@Composable
fun actionLabel(action: Action): String {
    val apps by LocalContext.current.container.apps.apps.collectAsState()
    return when (action) {
        is Action.OpenApp -> apps.firstOrNull { it.key.flat == "${action.component}#${action.user}" }?.label
            ?: stringResource(action.label)
        is Action.OpenShortcut -> action.title
        else -> stringResource(action.label)
    }
}

@Composable
fun ActionPicker(visible: Boolean, title: String, selected: Action?, onPick: (Action) -> Unit, onDismiss: () -> Unit) {
    val apps by LocalContext.current.container.apps.apps.collectAsState()
    var choosingApp by remember(visible) { mutableStateOf(false) }
    BottomSheet(visible, onDismiss) {
        SheetKicker(title)
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
            if (!choosingApp) {
                items(Action.fixed, key = { it.toString() }) { action ->
                    PickerRow(stringResource(action.label), action == selected) { onPick(action); onDismiss() }
                }
                item(key = "app") {
                    PickerRow(stringResource(R.string.action_open_app), selected is Action.OpenApp, chevron = true) { choosingApp = true }
                }
            } else {
                item(key = "apps-h") { SectionHeader(stringResource(R.string.section_apps)) }
                items(apps, key = { it.key.flat }) { app ->
                    val icon by rememberAppIcon(app.key)
                    IconRow(icon, app.label, {
                        onPick(Action.OpenApp(app.key.component.flattenToString(), app.key.user))
                        onDismiss()
                    })
                }
            }
        }
    }
}

@Composable
fun PickerRow(label: String, selected: Boolean, chevron: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(Space.hitTarget + 4.dp)
            .pressable(onClick, shape = RoundedCornerShape(0.dp), selected = selected)
            .padding(horizontal = Space.screen),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoleText(label, Simplify.type.label, color = if (selected) Simplify.colors.text else Simplify.colors.textMuted, modifier = Modifier.weight(1f))
        when {
            selected -> RoleText(stringResource(R.string.marker_selected).uppercase(), Simplify.type.section, color = Simplify.colors.accent)
            chevron -> Icon(Icons.ChevronRight, tint = Simplify.colors.textMuted, decorative = true)
            else -> Box {}
        }
    }
}
