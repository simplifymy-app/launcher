package app.simplifymy.launcher.feature.weather

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import app.simplifymy.launcher.R
import app.simplifymy.launcher.container
import app.simplifymy.launcher.core.settings.City
import app.simplifymy.launcher.core.settings.MAX_CITIES
import app.simplifymy.launcher.feature.settings.rememberCitySearch
import app.simplifymy.launcher.ui.component.Icon
import app.simplifymy.launcher.ui.component.IconAction
import app.simplifymy.launcher.ui.component.Icons
import app.simplifymy.launcher.ui.component.InputField
import app.simplifymy.launcher.ui.component.Snackbar
import app.simplifymy.launcher.ui.component.pressable
import app.simplifymy.launcher.ui.theme.Radius
import app.simplifymy.launcher.ui.theme.RoleText
import app.simplifymy.launcher.ui.theme.Simplify
import app.simplifymy.launcher.ui.theme.Space
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SNACKBAR_MS = 4000L

fun matched(name: String, query: String, accent: Color): AnnotatedString = buildAnnotatedString {
    append(name)
    val needle = query.trim()
    val at = name.indexOf(needle, ignoreCase = true)
    if (needle.isNotEmpty() && at >= 0) addStyle(SpanStyle(color = accent), at, at + needle.length)
}

fun coordinates(city: City): String =
    String.format(Locale.ROOT, "%.2f, %.2f", city.latitude, city.longitude).replace('-', '−')

@Composable
internal fun AddCityView(onBack: () -> Unit) {
    val (s, update) = weatherSettings()
    val weather = LocalContext.current.container.weather
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    val results = rememberCitySearch(query)
    val focus = remember { FocusRequester() }
    var added by remember { mutableStateOf<City?>(null) }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(added) { if (added != null) { delay(SNACKBAR_MS); added = null } }
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime))) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.padding(start = Space.base, end = Space.screen, top = Space.base, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.gutter),
            ) {
                IconAction(Icons.Back, onBack)
                InputField(
                    query, { query = it }, Modifier.weight(1f),
                    hint = stringResource(R.string.weather_city_name), focus = focus, icon = Icons.Search, focusRing = true,
                )
            }
            SectionLine(R.string.weather_results, note = stringResource(R.string.weather_geocoding))
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                if (query.isNotBlank() && results.isEmpty()) {
                    item {
                        RoleText(
                            stringResource(R.string.weather_no_cities), Simplify.type.meta,
                            Modifier.padding(horizontal = Space.screen, vertical = Space.base), color = Simplify.colors.textMuted,
                        )
                    }
                }
                items(results, key = { it.key }) { city ->
                    ResultRow(city, query, s.hasCity(city), s.weatherCities.size >= MAX_CITIES) {
                        update { it.addCity(city) }
                        added = city
                    }
                }
                item { Privacy() }
            }
        }
        Snackbar(
            added?.let { stringResource(R.string.weather_added, it.name) },
            Modifier.align(Alignment.BottomCenter),
            action = stringResource(R.string.action_undo),
        ) {
            added?.let { city ->
                update { it.removeCity(city) }
                scope.launch { weather.forget(city) }
            }
            added = null
        }
    }
}

@Composable
private fun ResultRow(city: City, query: String, added: Boolean, full: Boolean, onAdd: () -> Unit) {
    val colors = Simplify.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .pressable({ if (!added) onAdd() }, enabled = added || !full, shape = RoundedCornerShape(0.dp))
            .padding(horizontal = Space.screen),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(matched(city.name, query, colors.accent), style = Simplify.type.label, color = colors.text, maxLines = 1)
            RoleText(city.region, Simplify.type.body, color = colors.textMuted, maxLines = 1)
        }
        RoleText(coordinates(city), Simplify.type.meta, color = colors.textFaint, maxLines = 1)
        Box(
            Modifier.size(32.dp).clip(Radius.control).background(if (added) colors.accent else colors.surfaceAlt),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (added) Icons.Check else Icons.Plus, tint = if (added) colors.onAccent else colors.text, size = 18.dp)
        }
    }
}

@Composable
private fun Privacy() {
    Row(
        Modifier
            .padding(start = Space.screen, end = Space.screen, top = 18.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Simplify.colors.surface)
            .padding(horizontal = Space.rowGap, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(Space.toolbarInset),
    ) {
        Icon(Icons.Info, tint = Simplify.colors.textMuted, decorative = true)
        RoleText(stringResource(R.string.weather_privacy), Simplify.type.body, color = Simplify.colors.textMuted)
    }
}
