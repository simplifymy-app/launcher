package app.simplifymy.launcher.ui.component

import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.simplifymy.launcher.ui.theme.Motion
import app.simplifymy.launcher.ui.theme.Radius
import app.simplifymy.launcher.ui.theme.RoleText
import app.simplifymy.launcher.ui.theme.Simplify
import app.simplifymy.launcher.ui.theme.Sizes
import app.simplifymy.launcher.ui.theme.Space
import app.simplifymy.launcher.ui.theme.States
import app.simplifymy.launcher.ui.theme.enterTween

@Composable
fun Modifier.pressable(
    onClick: () -> Unit,
    enabled: Boolean = true,
    shape: Shape = Radius.control,
    selected: Boolean = false,
    base: Color = Color.Transparent,
    role: Role? = Role.Button,
    onLongClick: (() -> Unit)? = null,
): Modifier {
    val accent = Simplify.colors.accent
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val overlay = when {
        !enabled -> Color.Transparent
        pressed -> accent.copy(alpha = States.PRESS)
        selected -> accent.copy(alpha = States.SELECTED)
        hovered -> accent.copy(alpha = States.HOVER)
        else -> Color.Transparent
    }
    val layer by animateColorAsState(overlay, enterTween(Motion.STATE), label = "state")
    return this
        .alpha(if (enabled) 1f else States.DISABLED)
        .clip(shape)
        .background(base)
        .background(layer)
        .combinedClickable(interaction, indication = null, enabled = enabled, role = role, onLongClick = onLongClick, onClick = onClick)
}

enum class ButtonKind { Primary, Secondary, Ghost, Danger }

@Composable
fun SimplifyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Secondary,
    enabled: Boolean = true,
    icon: SimplifyIcon? = null,
) {
    val colors = Simplify.colors
    val background = when (kind) {
        ButtonKind.Primary -> colors.accent
        ButtonKind.Secondary -> colors.surfaceAlt
        ButtonKind.Ghost, ButtonKind.Danger -> Color.Transparent
    }
    val content = when (kind) {
        ButtonKind.Primary -> colors.onAccent
        ButtonKind.Danger -> colors.danger
        else -> colors.text
    }
    Row(
        modifier
            .height(Sizes.button)
            .pressable(onClick, enabled, Radius.control, base = background)
            .padding(horizontal = Space.rowGap),
        horizontalArrangement = Arrangement.spacedBy(Space.base, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { Icon(it, tint = content, size = 18.dp, decorative = true) }
        RoleText(text, Simplify.type.label, color = content, maxLines = 1)
    }
}

@Composable
fun IconAction(
    icon: SimplifyIcon,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Simplify.colors.text,
    enabled: Boolean = true,
    size: Dp = Space.hitTarget,
    label: String? = null,
) {
    Box(
        modifier.size(size).pressable(onClick, enabled, Radius.control),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, tint = tint, label = label)
    }
}

@Composable
fun DropdownPill(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .height(Sizes.dropdown)
            .pressable(onClick, shape = Radius.control, base = Simplify.colors.surfaceAlt)
            .padding(start = Space.rowGap, end = Space.base + Space.gutter),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.gutter),
    ) {
        RoleText(label, Simplify.type.label, maxLines = 1)
        Icon(Icons.ChevronDown, tint = Simplify.colors.textMuted, size = 16.dp, decorative = true)
    }
}

@Composable
fun <T> Segmented(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: (T) -> Boolean = { true },
    label: @Composable (T) -> String,
) {
    Row(
        modifier
            .clip(Radius.control)
            .background(Simplify.colors.surfaceAlt)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { option ->
            val active = option == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(Sizes.button)
                    .pressable(
                        onClick = { onSelect(option) },
                        enabled = enabled(option),
                        shape = Radius.control,
                        base = if (active) Simplify.colors.accent else Color.Transparent,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                RoleText(
                    label(option),
                    Simplify.type.label,
                    color = if (active) Simplify.colors.onAccent else Simplify.colors.textMuted,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun Toggle(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val colors = Simplify.colors
    val track by animateColorAsState(
        if (checked) colors.accent else colors.surfaceAlt, enterTween(Motion.STATE), label = "track",
    )
    val offset by animateDpAsState(
        if (checked) Sizes.toggleTrackWidth - Sizes.toggleKnob - 3.dp else 3.dp,
        enterTween(Motion.STATE), label = "knob",
    )
    Box(
        modifier.height(Space.hitTarget).width(Sizes.toggleTrackWidth),
        contentAlignment = Alignment.Center,
    ) {
      Box(
        Modifier
            .size(Sizes.toggleTrackWidth, Sizes.toggleTrackHeight)
            .pressable({ onChange(!checked) }, shape = Radius.pill, base = track, role = Role.Switch),
        contentAlignment = Alignment.CenterStart,
      ) {
        Box(
            Modifier
                .padding(start = offset)
                .size(Sizes.toggleKnob)
                .clip(Radius.pill)
                .background(if (checked) colors.onAccent else colors.textMuted)
        )
      }
    }
}

@Composable
fun SettingsRow(
    label: String,
    modifier: Modifier = Modifier,
    subline: String? = null,
    onClick: (() -> Unit)? = null,
    control: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Space.settingsRow)
            .then(if (onClick == null) Modifier else Modifier.pressable(onClick, shape = RoundedCornerShape(0.dp)))
            .padding(horizontal = Space.screen, vertical = Space.base),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.rowGap),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            RoleText(label, Simplify.type.body)
            subline?.let { RoleText(it, Simplify.type.meta, color = Simplify.colors.textMuted, maxLines = 2) }
        }
        control?.invoke()
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    count: String? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    actionIsAccent: Boolean = true,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Space.screen, vertical = Space.base),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.base),
    ) {
        RoleText(title.uppercase(), Simplify.type.section, color = Simplify.colors.textMuted)
        count?.let { RoleText(it, Simplify.type.section, color = Simplify.colors.textFaint) }
        Box(Modifier.weight(1f))
        if (action != null && onAction != null) {
            Box(
                Modifier
                    .pressable(onAction, shape = Radius.control)
                    .defaultMinSize(minWidth = Space.hitTarget, minHeight = Space.hitTarget)
                    .padding(horizontal = Space.base),
                contentAlignment = Alignment.Center,
            ) {
                RoleText(
                    action.uppercase(),
                    Simplify.type.section,
                    color = if (actionIsAccent) Simplify.colors.accent else Simplify.colors.textMuted,
                )
            }
        }
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier) =
    Box(modifier.fillMaxWidth().height(Sizes.hairline).background(Simplify.colors.hairline))

@Composable
fun SwatchRow(
    presets: List<Color>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(Space.base)) {
        presets.forEachIndexed { index, color ->
            val selected = index == selectedIndex
            Box(
                Modifier
                    .size(Space.hitTarget)
                    .pressable({ onSelect(index) }, shape = Radius.pill),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(if (selected) 26.dp else 30.dp)
                        .clip(Radius.pill)
                        .background(color)
                )
                if (selected) {
                    Box(
                        Modifier
                            .size(Space.hitTarget - 6.dp)
                            .clip(Radius.pill)
                            .border(2.dp, color, Radius.pill)
                    )
                }
            }
        }
    }
}

@Composable
fun Stepper(
    value: Int,
    onChange: (Int) -> Unit,
    range: IntRange,
    modifier: Modifier = Modifier,
    step: Int = 1,
    format: (Int) -> String = { it.toString() },
) {
    Row(
        modifier.clip(Radius.control).background(Simplify.colors.surfaceAlt),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepButton("−", enabled = value - step >= range.first) { onChange((value - step).coerceIn(range)) }
        RoleText(
            format(value),
            Simplify.type.value,
            modifier = Modifier.width(52.dp),
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        StepButton("+", enabled = value + step <= range.last) { onChange((value + step).coerceIn(range)) }
    }
}

@Composable
private fun StepButton(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(Space.hitTarget, Sizes.dropdown).pressable(onClick, enabled, Radius.control),
        contentAlignment = Alignment.Center,
    ) {
        RoleText(symbol, Simplify.type.value, color = Simplify.colors.text)
    }
}

@Composable
fun InputField(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    focus: FocusRequester? = null,
    icon: SimplifyIcon? = null,
    focusRing: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier
            .fillMaxWidth()
            .height(Space.hitTarget)
            .clip(Radius.control)
            .background(Simplify.colors.surface)
            .then(if (focusRing && focused) Modifier.border(2.dp, Simplify.colors.accent, Radius.control) else Modifier)
            .padding(horizontal = Space.toolbarInset),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        icon?.let { Icon(it, tint = Simplify.colors.textMuted, size = 18.dp, decorative = true) }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty() && hint != null) RoleText(hint, Simplify.type.body, color = Simplify.colors.textFaint, maxLines = 1)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = Simplify.type.body.copy(color = Simplify.colors.text),
                cursorBrush = SolidColor(Simplify.colors.accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focused = it.isFocused }
                    .then(if (focus != null) Modifier.focusRequester(focus) else Modifier),
            )
        }
    }
}

fun Modifier.tapCatcher(onTap: () -> Unit = {}): Modifier = composed {
    clickable(remember { MutableInteractionSource() }, indication = null, onClick = onTap)
}
