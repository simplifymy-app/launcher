package app.simplifymy.launcher.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.simplifymy.launcher.R
import app.simplifymy.launcher.container
import app.simplifymy.launcher.core.apps.AppInfo
import app.simplifymy.launcher.core.workspace.AppOverride
import app.simplifymy.launcher.core.workspace.DrawerGroup
import app.simplifymy.launcher.core.workspace.GroupKind
import app.simplifymy.launcher.ui.component.ButtonKind
import app.simplifymy.launcher.ui.component.DetailBar
import app.simplifymy.launcher.ui.component.InputField
import app.simplifymy.launcher.ui.component.SectionHeader
import app.simplifymy.launcher.ui.component.Segmented
import app.simplifymy.launcher.ui.component.SettingsRow
import app.simplifymy.launcher.ui.component.SimplifyButton
import app.simplifymy.launcher.ui.component.Toggle
import app.simplifymy.launcher.ui.launcher.IconRow
import app.simplifymy.launcher.ui.launcher.rememberAppIcon
import app.simplifymy.launcher.ui.theme.RoleText
import app.simplifymy.launcher.ui.theme.Simplify
import app.simplifymy.launcher.ui.theme.Space
import kotlinx.coroutines.launch

@Composable
private fun AppChecklist(
    apps: List<AppInfo>,
    checked: (AppInfo) -> Boolean,
    onToggle: (AppInfo, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier) {
        items(apps, key = { it.key.flat }) { app ->
            val on = checked(app)
            val icon by rememberAppIcon(app.key)
            IconRow(icon, app.label, { onToggle(app, !on) }) { Toggle(on, { onToggle(app, it) }) }
        }
    }
}

@Composable
fun HiddenAppsPage(back: () -> Unit) {
    val container = LocalContext.current.container
    val apps by container.apps.apps.collectAsState()
    val overrides by container.workspace.overrides.collectAsState(emptyList<AppOverride>())
    val hidden = overrides.filter { it.hidden }.map { it.key }.toSet()
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        DetailBar(stringResource(R.string.page_hidden), back, meta = stringResource(R.string.hidden_count, hidden.size))
        AppChecklist(
            apps,
            checked = { it.key.flat in hidden },
            onToggle = { app, on -> scope.launch { container.workspace.editOverride(app.key) { it.copy(hidden = on) } } },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun GroupsPage(back: () -> Unit) {
    val container = LocalContext.current.container
    val groups by container.workspace.groups.collectAsState(emptyList<DrawerGroup>())
    var editing by remember { mutableStateOf<DrawerGroup?>(null) }
    val scope = rememberCoroutineScope()
    val current = editing
    if (current != null) {
        GroupEditor(current, back = { editing = null }) { updated, delete ->
            scope.launch {
                if (delete) container.workspace.deleteGroup(updated) else container.workspace.saveGroup(updated)
            }
            editing = null
        }
        return
    }
    val newTab = stringResource(R.string.group_new_tab)
    val newFolder = stringResource(R.string.group_new_folder)
    SettingsPage(stringResource(R.string.page_groups), back, meta = "${groups.size}") {
        SectionHeader(
            stringResource(R.string.section_tabs), count = "${groups.count { it.kind == GroupKind.TAB }}",
            action = stringResource(R.string.action_new), onAction = {
                editing = DrawerGroup(name = newTab, kind = GroupKind.TAB, rank = groups.size)
            },
        )
        groups.filter { it.kind == GroupKind.TAB }.forEach { group ->
            SettingsRow(group.name, subline = stringResource(R.string.group_count, group.memberKeys.size), onClick = { editing = group })
        }
        SectionHeader(
            stringResource(R.string.section_drawer_folders), count = "${groups.count { it.kind == GroupKind.FOLDER }}",
            action = stringResource(R.string.action_new), onAction = {
                editing = DrawerGroup(name = newFolder, kind = GroupKind.FOLDER, rank = groups.size)
            },
        )
        groups.filter { it.kind == GroupKind.FOLDER }.forEach { group ->
            SettingsRow(group.name, subline = stringResource(R.string.group_count, group.memberKeys.size), onClick = { editing = group })
        }
        RoleText(
            stringResource(R.string.groups_hint), Simplify.type.meta, color = Simplify.colors.textMuted,
            modifier = Modifier.padding(horizontal = Space.screen, vertical = Space.base),
        )
    }
}

@Composable
private fun GroupEditor(group: DrawerGroup, back: () -> Unit, onDone: (DrawerGroup, delete: Boolean) -> Unit) {
    val container = LocalContext.current.container
    val apps by container.apps.apps.collectAsState()
    var name by remember(group.id) { mutableStateOf(group.name) }
    var kind by remember(group.id) { mutableStateOf(group.kind) }
    var members by remember(group.id) { mutableStateOf(group.memberKeys) }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        DetailBar(name.ifBlank { " " }, back, meta = stringResource(R.string.group_count, members.size))
        InputField(name, { name = it }, Modifier.padding(horizontal = Space.screen, vertical = Space.base))
        Segmented(
            listOf(GroupKind.TAB, GroupKind.FOLDER), kind, { kind = it },
            Modifier.fillMaxWidth().padding(horizontal = Space.screen),
        ) { stringResource(if (it == GroupKind.TAB) R.string.group_kind_tab else R.string.group_kind_folder) }
        SectionHeader(stringResource(R.string.section_apps), count = "${members.size}")
        AppChecklist(
            apps,
            checked = { it.key.flat in members },
            onToggle = { app, on -> members = if (on) members + app.key.flat else members - app.key.flat },
            modifier = Modifier.weight(1f),
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.screen, vertical = Space.base),
            horizontalArrangement = Arrangement.spacedBy(Space.base),
        ) {
            if (group.id != 0L) {
                SimplifyButton(stringResource(R.string.action_delete), { onDone(group, true) }, Modifier.weight(1f), ButtonKind.Danger)
            }
            SimplifyButton(
                stringResource(R.string.action_save),
                { onDone(group.copy(name = name.trim().ifEmpty { group.name }, kind = kind, members = members.joinToString("\n")), false) },
                Modifier.weight(1f), ButtonKind.Primary,
            )
        }
    }
}
