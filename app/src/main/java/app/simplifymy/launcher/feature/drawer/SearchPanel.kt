package app.simplifymy.launcher.feature.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import app.simplifymy.launcher.R
import app.simplifymy.launcher.container
import app.simplifymy.launcher.core.apps.AppInfo
import app.simplifymy.launcher.feature.home.HomeState
import app.simplifymy.launcher.ui.component.IconAction
import app.simplifymy.launcher.ui.component.Icons
import app.simplifymy.launcher.ui.component.SectionHeader
import app.simplifymy.launcher.ui.theme.Simplify
import app.simplifymy.launcher.ui.theme.Space

private const val SUGGESTED_ICON_PERCENT = 72

@Composable
fun SearchPanel(state: HomeState, host: DrawerHost, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val container = LocalContext.current.container
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        runCatching { focus.requestFocus() }
        keyboard?.show()
    }
    Column(
        modifier
            .fillMaxSize()
            .background(Simplify.colors.surface)
            .windowInsetsPadding(WindowInsets.statusBars)
            .imePadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = Space.gutter, end = Space.toolbarInset, top = Space.base, bottom = Space.base),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconAction(Icons.Back, onClose)
            SearchInput(
                query,
                { query = it },
                onSubmit = {
                    container.search.search(query, state.searchable(), false).apps.firstOrNull()?.let { host.launch(it, Rect.Zero) }
                },
                modifier = Modifier.weight(1f),
                focus = focus,
            )
        }
        if (query.isBlank()) {
            SectionHeader(stringResource(R.string.section_suggested))
            AppGrid(suggestions(state), state, host, labels = true, iconPercent = SUGGESTED_ICON_PERCENT)
        } else {
            SearchResultsList(query, state, host)
        }
    }
}

private fun suggestions(state: HomeState): List<AppInfo> =
    (state.frequent + state.drawer).distinct().take(state.settings.drawerColumns * 2)
