package app.simplifymy.launcher.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.simplifymy.launcher.ui.component.DetailBar
import app.simplifymy.launcher.ui.component.DropdownPill
import app.simplifymy.launcher.ui.component.Icon
import app.simplifymy.launcher.ui.component.Icons
import app.simplifymy.launcher.ui.component.Menu
import app.simplifymy.launcher.ui.component.MenuItem
import app.simplifymy.launcher.ui.component.SectionHeader
import app.simplifymy.launcher.ui.component.Segmented
import app.simplifymy.launcher.ui.component.SettingsRow
import app.simplifymy.launcher.ui.component.Stepper
import app.simplifymy.launcher.ui.component.Toggle
import app.simplifymy.launcher.ui.theme.RoleText
import app.simplifymy.launcher.ui.theme.Simplify
import app.simplifymy.launcher.ui.theme.Space

@Composable
fun SettingsPage(title: String, onBack: () -> Unit, meta: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        DetailBar(title, onBack, meta = meta)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            content()
            Spacer(Modifier.height(Space.section))
        }
    }
}

@Composable
fun Section(@StringRes title: Int) = SectionHeader(stringResource(title), Modifier.padding(top = Space.section - Space.base))

@Composable
fun ToggleRow(@StringRes label: Int, checked: Boolean, subline: String? = null, onChange: (Boolean) -> Unit) =
    SettingsRow(stringResource(label), subline = subline, onClick = { onChange(!checked) }) {
        Toggle(checked, onChange)
    }

@Composable
fun StepperRow(
    @StringRes label: Int,
    value: Int,
    range: IntRange,
    step: Int = 1,
    subline: String? = null,
    format: (Int) -> String = { it.toString() },
    onChange: (Int) -> Unit,
) = SettingsRow(stringResource(label), subline = subline) {
    Stepper(value, onChange, range, step = step, format = format)
}

@Composable
fun <T> ChoiceRow(
    @StringRes label: Int,
    options: List<T>,
    selected: T,
    subline: String? = null,
    segmented: Boolean = options.size <= 4,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    if (segmented) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Space.screen).padding(top = Space.base + Space.gutter, bottom = Space.base),
            verticalArrangement = Arrangement.spacedBy(Space.base),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                RoleText(stringResource(label), Simplify.type.body)
                subline?.let { RoleText(it, Simplify.type.meta, color = Simplify.colors.textMuted) }
            }
            Segmented(
                options, selected, onSelect,
                Modifier.fillMaxWidth(),
                label = optionLabel,
            )
        }
    } else {
        SettingsRow(stringResource(label), subline = subline) {
            var open by remember { mutableStateOf(false) }
            Box {
                DropdownPill(optionLabel(selected), { open = true })
                Menu(open, options.map { option -> MenuItem(optionLabel(option)) { onSelect(option) } }, { open = false })
            }
        }
    }
}

@Composable
fun NavRow(@StringRes label: Int, subline: String? = null, onClick: () -> Unit) =
    SettingsRow(stringResource(label), subline = subline, onClick = onClick) {
        Icon(Icons.ChevronRight, tint = Simplify.colors.textMuted, decorative = true)
    }

@Composable
fun ValueRow(label: String, value: String, onClick: (() -> Unit)? = null) =
    SettingsRow(label, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoleText(value, Simplify.type.value, color = Simplify.colors.textMuted, maxLines = 1)
        }
    }
