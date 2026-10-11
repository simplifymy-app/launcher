package app.simplifymy.launcher.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.simplifymy.launcher.R
import app.simplifymy.launcher.ui.component.Icon
import app.simplifymy.launcher.ui.component.IconAction
import app.simplifymy.launcher.ui.component.Icons
import app.simplifymy.launcher.ui.component.SimplifyIcon
import app.simplifymy.launcher.ui.component.pressable
import app.simplifymy.launcher.ui.component.tapCatcher
import app.simplifymy.launcher.ui.launcher.IconImage
import app.simplifymy.launcher.ui.launcher.rootRect
import app.simplifymy.launcher.ui.theme.Motion
import app.simplifymy.launcher.ui.theme.Radius
import app.simplifymy.launcher.ui.theme.RoleText
import app.simplifymy.launcher.ui.theme.Simplify
import app.simplifymy.launcher.ui.theme.Space
import app.simplifymy.launcher.ui.theme.States
import app.simplifymy.launcher.ui.theme.enterTween
import app.simplifymy.launcher.ui.theme.exitTween

data class MenuShortcut(val label: String, val icon: ImageBitmap?, val onClick: () -> Unit)

data class MenuAction(val icon: SimplifyIcon, val onClick: () -> Unit, val danger: Boolean = false)

data class MenuRequest(
    val anchor: Rect,
    val title: String?,
    val shortcuts: List<MenuShortcut>,
    val actions: List<MenuAction>,
)

@Composable
fun ItemMenu(request: MenuRequest?, onDismiss: () -> Unit) {
    var shown by remember { mutableStateOf<MenuRequest?>(null) }
    if (request != null) shown = request
    AnimatedVisibility(
        visible = request != null,
        enter = fadeIn(enterTween(Motion.CHROME)),
        exit = fadeOut(exitTween(Motion.CHROME)),
    ) {
        val menu = shown ?: return@AnimatedVisibility
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .tapCatcher(onDismiss)
        ) {
            val density = LocalDensity.current
            var height by remember { mutableStateOf(0) }
            var width by remember { mutableStateOf(0) }
            val screenW = with(density) { maxWidth.toPx() }
            val gap = with(density) { Space.base.toPx() }
            val above = menu.anchor.top - height - gap
            val y = if (above > gap * 6) above else menu.anchor.bottom + gap
            val x = (menu.anchor.center.x - width / 2f).coerceIn(gap, (screenW - width - gap).coerceAtLeast(gap))
            Column(
                Modifier
                    .offset { IntOffset(x.toInt(), y.toInt()) }
                    .onGloballyPositioned { height = it.size.height; width = it.size.width }
                    .widthIn(min = 200.dp, max = 300.dp)
                    .width(IntrinsicSize.Max)
                    .clip(Radius.card)
                    .background(Simplify.colors.surfaceAlt)
                    .padding(vertical = Space.gutter),
            ) {
                menu.title?.let {
                    RoleText(
                        it.uppercase(), Simplify.type.section, color = Simplify.colors.textMuted, maxLines = 1,
                        modifier = Modifier.padding(horizontal = Space.rowGap, vertical = Space.base),
                    )
                }
                menu.shortcuts.forEach { shortcut ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(Space.hitTarget)
                            .pressable({ shortcut.onClick(); onDismiss() }, shape = RoundedCornerShape(0.dp))
                            .padding(horizontal = Space.rowGap),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.toolbarInset),
                    ) {
                        IconImage(shortcut.icon, 22.dp)
                        RoleText(shortcut.label, Simplify.type.label, maxLines = 1)
                    }
                }
                if (menu.actions.isNotEmpty()) {
                    if (menu.shortcuts.isNotEmpty()) {
                        Box(Modifier.fillMaxWidth().padding(vertical = Space.gutter).height(1.dp).background(Simplify.colors.hairline))
                    }
                    Row(Modifier.padding(horizontal = Space.gutter)) {
                        menu.actions.forEach { action ->
                            IconAction(
                                action.icon,
                                { action.onClick(); onDismiss() },
                                tint = if (action.danger) Simplify.colors.danger else Simplify.colors.text,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DropBar(drag: DragController, targets: List<BarTarget>, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = drag.active,
        enter = fadeIn(enterTween(Motion.CHROME)),
        exit = fadeOut(exitTween(Motion.CHROME)),
        modifier = modifier,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.toolbarInset, vertical = Space.base),
            horizontalArrangement = Arrangement.spacedBy(Space.base),
        ) {
            targets.forEach { target ->
                val hovered = (drag.target as? DropTarget.Bar)?.target == target
                val danger = target == BarTarget.Uninstall
                val ink = when {
                    hovered && danger -> Simplify.colors.danger
                    hovered -> Simplify.colors.onAccent
                    else -> Simplify.colors.text
                }
                val fill = when {
                    hovered && danger -> Simplify.colors.danger.copy(alpha = States.SELECTED)
                    hovered -> Simplify.colors.accent
                    else -> Simplify.colors.surfaceAlt
                }
                Row(
                    Modifier
                        .weight(1f)
                        .height(Space.hitTarget)
                        .clip(Radius.control)
                        .background(Simplify.colors.surfaceAlt)
                        .background(fill)
                        .onGloballyPositioned { drag.bar[target] = it.rootRect() },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        when (target) {
                            BarTarget.Remove -> Icons.Remove
                            BarTarget.Info -> Icons.Info
                            BarTarget.Uninstall -> Icons.Trash
                        },
                        tint = ink, size = 16.dp, decorative = true,
                    )
                    RoleText(
                        stringResource(
                            when (target) {
                                BarTarget.Remove -> R.string.drop_remove
                                BarTarget.Info -> R.string.drop_info
                                BarTarget.Uninstall -> R.string.drop_uninstall
                            }
                        ).uppercase(),
                        Simplify.type.section,
                        color = ink,
                        modifier = Modifier.padding(start = 6.dp),
                        maxLines = 1,
                    )
                }
            }
        }
    }
    if (!drag.active) SideEffect { drag.bar.clear() }
}
