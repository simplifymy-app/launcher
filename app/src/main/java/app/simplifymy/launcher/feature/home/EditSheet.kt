package app.simplifymy.launcher.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.simplifymy.launcher.R
import app.simplifymy.launcher.container
import app.simplifymy.launcher.core.actions.Action
import app.simplifymy.launcher.core.apps.AppInfo
import app.simplifymy.launcher.core.icons.IconPack
import app.simplifymy.launcher.core.icons.IconRef
import app.simplifymy.launcher.core.settings.SettingsJson
import app.simplifymy.launcher.core.workspace.ItemEntity
import app.simplifymy.launcher.core.workspace.ItemFlags
import app.simplifymy.launcher.ui.component.BottomSheet
import app.simplifymy.launcher.ui.component.InputField
import app.simplifymy.launcher.ui.component.SectionHeader
import app.simplifymy.launcher.ui.component.SettingsRow
import app.simplifymy.launcher.ui.component.SheetButtons
import app.simplifymy.launcher.ui.component.SheetKicker
import app.simplifymy.launcher.ui.component.Toggle
import app.simplifymy.launcher.ui.component.pressable
import app.simplifymy.launcher.ui.launcher.ActionPicker
import app.simplifymy.launcher.ui.launcher.IconImage
import app.simplifymy.launcher.ui.launcher.actionLabel
import app.simplifymy.launcher.ui.launcher.rememberAppIcon
import app.simplifymy.launcher.ui.launcher.rememberIcon
import app.simplifymy.launcher.ui.launcher.rememberItemIcon
import app.simplifymy.launcher.ui.theme.Radius
import app.simplifymy.launcher.ui.theme.RoleText
import app.simplifymy.launcher.ui.theme.Simplify
import app.simplifymy.launcher.ui.theme.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface EditRequest {
    data class Item(val item: ItemEntity) : EditRequest
    data class App(val app: AppInfo) : EditRequest
}

fun ItemEntity.swipeAction(): Action? =
    swipe?.let { runCatching { SettingsJson.decodeFromString(Action.serializer(), it) }.getOrNull() }

@Composable
fun EditSheet(request: EditRequest?, state: HomeState, viewModel: HomeViewModel, onDismiss: () -> Unit) {
    BottomSheet(request != null, onDismiss) {
        when (request) {
            is EditRequest.Item -> EditItem(request.item, state, viewModel, onDismiss)
            is EditRequest.App -> EditApp(request.app, state, viewModel, onDismiss)
            null -> Unit
        }
    }
}

@Composable
private fun EditItem(item: ItemEntity, state: HomeState, viewModel: HomeViewModel, onDismiss: () -> Unit) {
    var label by remember(item.id) { mutableStateOf(state.label(item)) }
    var icon by remember(item.id) { mutableStateOf(item.icon) }
    var swipe by remember(item.id) { mutableStateOf(item.swipeAction() ?: Action.None) }
    var cover by remember(item.id) { mutableStateOf(item.has(ItemFlags.SWIPE_TO_OPEN)) }
    var picking by remember { mutableStateOf(false) }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        SheetKicker(stringResource(if (item.isFolder) R.string.edit_folder else R.string.edit_item))
        InputField(label, { label = it }, Modifier.padding(horizontal = Space.screen, vertical = Space.base))
        if (item.isLaunchable) {
            val preview by rememberItemIcon(item, icon ?: item.app?.let { state.overrides[it]?.icon })
            IconChooser(preview, onPick = { icon = it }, onReset = { icon = null })
            SettingsRow(stringResource(R.string.swipe_action), subline = actionLabel(swipe), onClick = { picking = true })
        }
        if (item.isFolder) {
            SettingsRow(stringResource(R.string.folder_swipe_to_open), subline = stringResource(R.string.folder_swipe_to_open_hint)) {
                Toggle(cover, { cover = it })
            }
        }
        SheetButtons(stringResource(R.string.action_save), onDismiss) {
            val defaultLabel = state.label(item.copy(title = null))
            viewModel.update(
                item.copy(
                    title = label.trim().takeIf { it.isNotEmpty() && (item.isFolder || it != defaultLabel) },
                    icon = icon,
                    swipe = swipe.takeIf { it != Action.None }?.let { SettingsJson.encodeToString(Action.serializer(), it) },
                    flags = if (cover) item.flags or ItemFlags.SWIPE_TO_OPEN else item.flags and ItemFlags.SWIPE_TO_OPEN.inv(),
                )
            )
            onDismiss()
        }
    }
    ActionPicker(picking, stringResource(R.string.swipe_action), swipe, { swipe = it }, { picking = false })
}

@Composable
private fun EditApp(app: AppInfo, state: HomeState, viewModel: HomeViewModel, onDismiss: () -> Unit) {
    val override = state.overrides[app.key.flat]
    var label by remember(app.key) { mutableStateOf(override?.label ?: app.label) }
    var icon by remember(app.key) { mutableStateOf(override?.icon) }
    var hidden by remember(app.key) { mutableStateOf(override?.hidden == true) }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        SheetKicker(stringResource(R.string.edit_app))
        InputField(label, { label = it }, Modifier.padding(horizontal = Space.screen, vertical = Space.base))
        val preview by rememberAppIcon(app.key, icon)
        IconChooser(preview, onPick = { icon = it }, onReset = { icon = null })
        SettingsRow(stringResource(R.string.hide_from_drawer)) { Toggle(hidden, { hidden = it }) }
        SheetButtons(stringResource(R.string.action_save), onDismiss) {
            viewModel.editApp(app.key, label.trim().takeIf { it != app.label }, icon)
            if (hidden != (override?.hidden == true)) viewModel.setHidden(app.key, hidden)
            onDismiss()
        }
    }
}

@Composable
private fun IconChooser(preview: ImageBitmap?, onPick: (String) -> Unit, onReset: () -> Unit) {
    val context = LocalContext.current
    val container = context.container
    val packs = remember { IconPack.installed(context) }
    var pack by remember { mutableStateOf<String?>(null) }
    SectionHeader(stringResource(R.string.section_icon), action = stringResource(R.string.action_reset), onAction = onReset)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Space.screen),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.rowGap),
    ) {
        IconImage(preview, 52.dp)
        if (packs.isEmpty()) {
            RoleText(stringResource(R.string.no_icon_packs), Simplify.type.meta, color = Simplify.colors.textMuted)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.base)) {
                items(packs, key = { it.packageName }) { info ->
                    val active = info.packageName == pack
                    Box(
                        Modifier
                            .height(34.dp)
                            .pressable({ pack = if (active) null else info.packageName }, shape = Radius.control,
                                base = if (active) Simplify.colors.accent else Simplify.colors.surface)
                            .padding(horizontal = Space.toolbarInset),
                        contentAlignment = Alignment.Center,
                    ) {
                        RoleText(info.label, Simplify.type.label, color = if (active) Simplify.colors.onAccent else Simplify.colors.text, maxLines = 1)
                    }
                }
            }
        }
    }
    val chosen = pack ?: return
    val names by produceState(emptyList<String>(), chosen) {
        value = withContext(Dispatchers.IO) { container.icons.pack(chosen)?.allDrawables().orEmpty() }
    }
    LazyVerticalGrid(
        GridCells.Fixed(6),
        Modifier.fillMaxWidth().height(280.dp).padding(horizontal = Space.toolbarInset, vertical = Space.base),
    ) {
        items(names, key = { it }) { name ->
            val ref = IconRef.pack(chosen, name)
            val bitmap by rememberIcon(ref) { it.icons.ref(ref) }
            Box(
                Modifier.size(52.dp).pressable({ onPick(ref) }, shape = Radius.control),
                contentAlignment = Alignment.Center,
            ) { IconImage(bitmap, 40.dp) }
        }
    }
}
