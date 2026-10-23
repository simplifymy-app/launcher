package app.simplifymy.launcher.feature.settings

import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.simplifymy.launcher.R
import app.simplifymy.launcher.container
import app.simplifymy.launcher.core.settings.City
import app.simplifymy.launcher.ui.component.BottomSheet
import app.simplifymy.launcher.ui.component.Icon
import app.simplifymy.launcher.ui.component.Icons
import app.simplifymy.launcher.ui.component.InputField
import app.simplifymy.launcher.ui.component.SettingsRow
import app.simplifymy.launcher.ui.component.SheetKicker
import app.simplifymy.launcher.ui.theme.RoleText
import app.simplifymy.launcher.ui.theme.Simplify
import app.simplifymy.launcher.ui.theme.Space
import kotlinx.coroutines.delay

private const val DEBOUNCE_MS = 350L

@Composable
fun rememberCitySearch(query: String, key: Any? = null): List<City> {
    val container = LocalContext.current.container
    val language = LocalConfiguration.current.locales[0].language
    var results by remember(key) { mutableStateOf(emptyList<City>()) }
    LaunchedEffect(query) {
        delay(DEBOUNCE_MS)
        results = container.weather.searchCities(query, language)
    }
    return results
}

@Composable
fun CityPicker(
    visible: Boolean,
    onPick: (City) -> Unit,
    onDismiss: () -> Unit,
    title: String = stringResource(R.string.weather_city),
    current: City? = null,
    onRemove: (() -> Unit)? = null,
) {
    var query by remember(visible) { mutableStateOf("") }
    val results = rememberCitySearch(query, visible)
    val focus = remember { FocusRequester() }
    LaunchedEffect(visible) { if (visible) runCatching { delay(DEBOUNCE_MS); focus.requestFocus() } }
    BottomSheet(visible, onDismiss) {
        Column(Modifier.fillMaxWidth()) {
            SheetKicker(title)
            if (current != null && onRemove != null) {
                SettingsRow(
                    stringResource(R.string.weather_remove_city, current.name), subline = current.region,
                    onClick = { onRemove(); onDismiss() },
                ) { Icon(Icons.Remove, tint = Simplify.colors.textMuted, decorative = true) }
            }
            InputField(
                query, { query = it }, Modifier.padding(horizontal = Space.screen, vertical = Space.base),
                hint = stringResource(R.string.weather_search_city), focus = focus,
            )
            if (query.isNotBlank() && results.isEmpty()) {
                RoleText(
                    stringResource(R.string.weather_no_cities), Simplify.type.meta, color = Simplify.colors.textMuted,
                    modifier = Modifier.padding(horizontal = Space.screen, vertical = Space.base),
                )
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                items(results, key = { "${it.latitude},${it.longitude}" }) { city ->
                    SettingsRow(city.name, subline = city.region, onClick = { onPick(city); onDismiss() })
                }
            }
        }
    }
}
