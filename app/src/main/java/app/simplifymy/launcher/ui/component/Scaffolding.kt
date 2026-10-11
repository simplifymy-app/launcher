package app.simplifymy.launcher.ui.component

import androidx.compose.foundation.layout.navigationBars
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.simplifymy.launcher.R
import app.simplifymy.launcher.ui.Localized
import app.simplifymy.launcher.ui.theme.Motion
import app.simplifymy.launcher.ui.theme.Radius
import app.simplifymy.launcher.ui.theme.RoleText
import app.simplifymy.launcher.ui.theme.Simplify
import app.simplifymy.launcher.ui.theme.Space
import app.simplifymy.launcher.ui.theme.enterTween
import app.simplifymy.launcher.ui.theme.exitTween

@Composable
fun TopLevelBar(title: String, modifier: Modifier = Modifier, actions: BarActions = {}) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = Space.screen, end = Space.toolbarInset, top = Space.section, bottom = Space.base),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoleText(title, Simplify.type.display, modifier = Modifier.weight(1f), maxLines = 1)
        actions()
    }
}

@Composable
fun DetailBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    meta: String? = null,
    actions: BarActions = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Space.toolbarInset, vertical = Space.base),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconAction(Icons.Back, onBack)
        Column(Modifier.weight(1f).padding(start = Space.gutter)) {
            RoleText(title, Simplify.type.headline, maxLines = 1)
            meta?.let { RoleText(it, Simplify.type.meta, color = Simplify.colors.textMuted, maxLines = 1) }
        }
        actions()
    }
}

typealias BarActions = @Composable () -> Unit

@Composable
fun BottomSheet(visible: Boolean, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    if (!visible) return
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
      Localized {
        Box(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars)),
            contentAlignment = Alignment.BottomCenter,
        ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(Radius.sheet)
                .background(Simplify.colors.surfaceAlt)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = Space.screen),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .padding(vertical = Space.base + Space.gutter)
                    .width(36.dp)
                    .heightIn(min = 4.dp)
                    .clip(Radius.pill)
                    .background(Simplify.colors.hairline)
            )
            content()
        }
        }
      }
    }
}

@Composable
fun Snackbar(message: String?, modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf("") }
    if (message != null) shown = message
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn(enterTween(Motion.CHROME)),
        exit = fadeOut(exitTween(Motion.CHROME)),
        modifier = modifier,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(Space.screen)
                .clip(Radius.control)
                .background(Simplify.colors.surfaceAlt)
                .padding(horizontal = Space.rowGap, vertical = Space.base + Space.gutter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoleText(shown, Simplify.type.body)
        }
    }
}

@Composable
fun SheetKicker(text: String, modifier: Modifier = Modifier.padding(horizontal = Space.screen, vertical = Space.base)) =
    RoleText(text.uppercase(), Simplify.type.section, modifier.fillMaxWidth(), color = Simplify.colors.textMuted)

@Composable
fun SheetButtons(
    confirm: String,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier.padding(horizontal = Space.screen, vertical = Space.base),
    onConfirm: () -> Unit,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.base)) {
        SimplifyButton(stringResource(R.string.action_cancel), onCancel, Modifier.weight(1f), ButtonKind.Secondary)
        SimplifyButton(confirm, onConfirm, Modifier.weight(1f), ButtonKind.Primary)
    }
}
