package app.simplifymy.launcher.feature.weather

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.simplifymy.launcher.R
import app.simplifymy.launcher.container
import app.simplifymy.launcher.core.settings.City
import app.simplifymy.launcher.core.settings.MAX_CITIES
import app.simplifymy.launcher.core.weather.WeatherState
import app.simplifymy.launcher.ui.component.ButtonKind
import app.simplifymy.launcher.ui.component.Icon
import app.simplifymy.launcher.ui.component.IconAction
import app.simplifymy.launcher.ui.component.Icons
import app.simplifymy.launcher.ui.component.Menu
import app.simplifymy.launcher.ui.component.MenuItem
import app.simplifymy.launcher.ui.component.SimplifyButton
import app.simplifymy.launcher.ui.component.TopLevelBar
import app.simplifymy.launcher.ui.component.icon
import app.simplifymy.launcher.ui.component.pressable
import app.simplifymy.launcher.ui.launcher.degrees
import app.simplifymy.launcher.ui.theme.RoleText
import app.simplifymy.launcher.ui.theme.Simplify
import app.simplifymy.launcher.ui.theme.Space
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.launch

@Composable
internal fun CitiesView(onAdd: () -> Unit, onPick: () -> Unit) {
    val (s, update) = weatherSettings()
    val weather = LocalContext.current.container.weather
    val scope = rememberCoroutineScope()
    val cities = s.weatherCities
    val room = cities.size < MAX_CITIES
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        TopLevelBar(stringResource(R.string.weather_cities)) {
            IconAction(Icons.Plus, onAdd, enabled = room, label = stringResource(R.string.weather_add_city))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            SectionLine(R.string.weather_saved, note = stringResource(R.string.weather_count, cities.size, MAX_CITIES))
            cities.forEachIndexed { index, city ->
                key(city.key) {
                    CityRow(
                        city,
                        current = city.key == s.weatherCity?.key,
                        canMoveUp = index > 0,
                        canMoveDown = index < cities.lastIndex,
                        onPick = { update { it.selectCity(city) }; onPick() },
                        onMove = { by -> update { it.moveCity(city, by) } },
                        onRemove = {
                            update { it.removeCity(city) }
                            scope.launch { weather.forget(city) }
                        },
                    )
                }
            }
            RoleText(
                stringResource(R.string.weather_cities_hint), Simplify.type.meta,
                Modifier.padding(start = Space.screen, end = Space.screen, top = 14.dp), color = Simplify.colors.textFaint,
            )
        }
        Column(Modifier.padding(horizontal = Space.screen, vertical = Space.section)) {
            SimplifyButton(
                stringResource(R.string.weather_add_city), onAdd, Modifier.fillMaxWidth().height(48.dp),
                kind = ButtonKind.Primary, enabled = room, icon = Icons.Plus,
            )
            RoleText(
                stringResource(R.string.weather_add_hint), Simplify.type.meta,
                Modifier.fillMaxWidth().padding(top = Space.toolbarInset), color = Simplify.colors.textFaint, textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun CityRow(
    city: City,
    current: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onPick: () -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    val ready = cityState(city) as? WeatherState.Ready
    var menu by remember { mutableStateOf(false) }
    val meta = listOfNotNull(
        city.countryName.takeIf { it.isNotBlank() },
        clock(ZonedDateTime.now(city.zone ?: ZoneId.systemDefault())),
        ready?.let { stringResource(R.string.weather_high_low, degrees(it.now.high), degrees(it.now.low)) },
    ).joinToString(" · ")
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .height(72.dp)
                .pressable(onPick, shape = RoundedCornerShape(0.dp), selected = current, onLongClick = { menu = true })
                .padding(horizontal = Space.screen),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.rowGap),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                RoleText(city.name, Simplify.type.label, maxLines = 1)
                RoleText(meta, Simplify.type.meta, color = Simplify.colors.textMuted, maxLines = 1)
            }
            ready?.let { Icon(it.now.condition.icon, size = 20.dp, label = stringResource(it.now.condition.label)) }
            Box(Modifier.width(76.dp), contentAlignment = Alignment.CenterEnd) {
                ready?.let { Numerals(degrees(it.now.temperature), 30.sp) }
            }
        }
        Menu(
            menu,
            listOfNotNull(
                MenuItem(stringResource(R.string.weather_move_up)) { onMove(-1) }.takeIf { canMoveUp },
                MenuItem(stringResource(R.string.weather_move_down)) { onMove(1) }.takeIf { canMoveDown },
                MenuItem(stringResource(R.string.icon_remove), danger = true, onClick = onRemove),
            ),
        ) { menu = false }
    }
}
