package app.simplifymy.launcher.feature.weather

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import app.simplifymy.launcher.R
import app.simplifymy.launcher.SettingsActivity
import app.simplifymy.launcher.container
import app.simplifymy.launcher.core.settings.City
import app.simplifymy.launcher.core.settings.Settings
import app.simplifymy.launcher.core.weather.DayPoint
import app.simplifymy.launcher.core.weather.HourPoint
import app.simplifymy.launcher.core.weather.WeatherState
import app.simplifymy.launcher.core.weather.rainText
import app.simplifymy.launcher.core.weather.visibilityText
import app.simplifymy.launcher.feature.settings.Page
import app.simplifymy.launcher.ui.component.EmptyState
import app.simplifymy.launcher.ui.component.Hairline
import app.simplifymy.launcher.ui.component.Icon
import app.simplifymy.launcher.ui.component.IconAction
import app.simplifymy.launcher.ui.component.Icons
import app.simplifymy.launcher.ui.component.Menu
import app.simplifymy.launcher.ui.component.MenuItem
import app.simplifymy.launcher.ui.component.TopLevelBar
import app.simplifymy.launcher.ui.component.icon
import app.simplifymy.launcher.ui.component.pressable
import app.simplifymy.launcher.ui.launcher.degrees
import app.simplifymy.launcher.ui.theme.RoleText
import app.simplifymy.launcher.ui.theme.Simplify
import app.simplifymy.launcher.ui.theme.Space
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.launch

private const val HOURS = 12
private val HOUR_WIDTH = 52.dp
private const val OPEN_METEO = "https://open-meteo.com"

@Composable
internal fun TodayView(openCities: () -> Unit, openDay: (String) -> Unit, addCity: () -> Unit) {
    val (s, _) = weatherSettings()
    val city = s.weatherCity
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        if (city == null) {
            TopLevelBar(stringResource(R.string.weather_label)) { IconAction(Icons.Cities, openCities) }
            EmptyState(
                stringResource(R.string.weather_choose_city), stringResource(R.string.weather_no_city_hint),
                action = stringResource(R.string.weather_add_city), onAction = addCity,
            )
            return@Column
        }
        val state = cityState(city)
        val ready = state as? WeatherState.Ready
        TopLevelBar(
            city.name,
            meta = ready?.let {
                stringResource(R.string.weather_updated_at, clock(it.now.updated.atZone(ZoneId.systemDefault()))) + " · " +
                    pluralStringResource(R.plurals.weather_cached_days, it.days.size, it.days.size)
            },
        ) {
            IconAction(Icons.Cities, openCities)
            Overflow(city)
        }
        when (state) {
            is WeatherState.Ready -> TodayContent(city, state, s) { openDay(it.toString()) }
            WeatherState.Offline -> {
                val weather = LocalContext.current.container.weather
                val scope = rememberCoroutineScope()
                EmptyState(
                    stringResource(R.string.weather_offline), stringResource(R.string.weather_offline_hint),
                    action = stringResource(R.string.weather_refresh_now), onAction = { scope.launch { weather.refresh(city, force = true) } },
                )
            }
            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                RoleText(stringResource(R.string.weather_loading), Simplify.type.meta, color = Simplify.colors.textMuted)
            }
        }
    }
}

@Composable
private fun Overflow(city: City) {
    val context = LocalContext.current
    val weather = context.container.weather
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    Box {
        IconAction(Icons.More, { open = true })
        Menu(
            open,
            listOf(
                MenuItem(stringResource(R.string.weather_refresh_now)) { scope.launch { weather.refresh(city, force = true) } },
                MenuItem(stringResource(R.string.weather_settings)) { context.startActivity(SettingsActivity.open(context, Page.Weather)) },
            ),
        ) { open = false }
    }
}

@Composable
private fun TodayContent(city: City, weather: WeatherState.Ready, s: Settings, openDay: (LocalDate) -> Unit) {
    val hours = remember(weather) { weather.points.filter { !it.time.isBefore(weather.current.time) }.take(HOURS) }
    var selected by rememberSaveable(city.key) { mutableIntStateOf(0) }
    val hour = hours.getOrElse(selected) { weather.current }
    val day = weather.days.firstOrNull { it.date == hour.time.toLocalDate() }
    val low = weather.days.minOfOrNull { it.low } ?: 0
    val high = weather.days.maxOfOrNull { it.high } ?: 0
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Hero(hour, day, selected == 0, weather)
        SectionLine(R.string.weather_next_hours, note = stringResource(R.string.weather_tap_hour))
        HourStrip(hours, selected) { selected = it }
        Spacer(Modifier.height(Space.section))
        SectionLine(R.string.weather_days, note = "${degrees(low)} – ${degrees(high)}")
        weather.days.forEach { d ->
            val today = d.date == weather.local.toLocalDate()
            DayRow(d, today, if (today) weather.now.temperature else null, low, high) { openDay(d.date) }
        }
        Spacer(Modifier.height(Space.section))
        SectionLine(R.string.weather_right_now)
        Details(weather, s)
        Attribution()
    }
}

@Composable
private fun Hero(hour: HourPoint, day: DayPoint?, isNow: Boolean, weather: WeatherState.Ready) {
    val label = stringResource(hour.condition.label)
    val whenText = when {
        isNow -> format(weather.local, "EEE d MMM")
        hour.time.toLocalDate() == weather.local.toLocalDate() -> stringResource(R.string.weather_today_at, clock(hour.time))
        else -> capitalized(hour.time, "EEE") + " " + clock(hour.time)
    }
    Column(Modifier.padding(start = Space.screen, end = Space.screen, top = 18.dp, bottom = 30.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(hour.condition.icon, size = 26.dp, decorative = true)
            RoleText(label, Simplify.type.title, Modifier.weight(1f), maxLines = 1)
            RoleText(whenText, Simplify.type.meta, color = Simplify.colors.textMuted, maxLines = 1)
        }
        FitNumerals(degrees(hour.temperature), 132.sp, Modifier.padding(top = 16.dp))
        RoleText(
            stringResource(
                R.string.weather_hero_line,
                degrees(hour.feels), degrees(day?.high ?: hour.temperature), degrees(day?.low ?: hour.temperature), "${hour.rainChance}%",
            ),
            Simplify.type.value, Modifier.padding(top = 14.dp), color = Simplify.colors.textMuted,
        )
    }
}

@Composable
private fun HourStrip(hours: List<HourPoint>, selected: Int, onPick: (Int) -> Unit) {
    val faint = Simplify.colors.textFaint
    val accent = Simplify.colors.accent
    val now = stringResource(R.string.weather_now)
    Column(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Space.toolbarInset)) {
        Row {
            hours.forEachIndexed { i, point ->
                val time = if (i == 0) now else hour(point.time)
                val description = "$time, ${stringResource(point.condition.label)}, ${degrees(point.temperature)}, ${point.rainChance}%"
                Column(
                    Modifier
                        .width(HOUR_WIDTH)
                        .pressable({ onPick(i) }, selected = i == selected)
                        .clearAndSetSemantics { contentDescription = description }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Space.base),
                ) {
                    RoleText(time, Simplify.type.meta, color = Simplify.colors.textMuted, maxLines = 1)
                    Icon(point.condition.icon, size = 20.dp, decorative = true)
                    RoleText(degrees(point.temperature), Simplify.type.value, maxLines = 1)
                    RoleText("${point.rainChance}%", Simplify.type.meta, color = Simplify.colors.textMuted, maxLines = 1)
                }
            }
        }
        Canvas(Modifier.padding(top = Space.gutter).width(HOUR_WIDTH * hours.size).height(44.dp)) {
            if (hours.isEmpty()) return@Canvas
            val low = hours.minOf { it.temperature }
            val span = (hours.maxOf { it.temperature } - low).coerceAtLeast(1)
            fun at(i: Int) = Offset(
                (i * HOUR_WIDTH.toPx()) + HOUR_WIDTH.toPx() / 2,
                36.dp.toPx() - (hours[i].temperature - low).toFloat() / span * 28.dp.toPx(),
            )
            val line = Path().apply {
                moveTo(at(0).x, at(0).y)
                hours.indices.drop(1).forEach { lineTo(at(it).x, at(it).y) }
            }
            drawPath(line, faint, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawCircle(accent, 4.dp.toPx(), at(selected.coerceIn(hours.indices)))
        }
    }
}

@Composable
private fun DayRow(day: DayPoint, today: Boolean, now: Int?, low: Int, high: Int, onClick: () -> Unit) {
    val name = if (today) stringResource(R.string.widget_today) else capitalized(day.date, "EEE")
    val date = if (today) capitalized(day.date, "EEE d") else format(day.date, "d")
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Space.settingsRow)
            .pressable(onClick, shape = RoundedCornerShape(0.dp), selected = today)
            .padding(horizontal = Space.screen, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.width(74.dp)) {
            RoleText(name, Simplify.type.label, maxLines = 1)
            RoleText(date, Simplify.type.meta, color = Simplify.colors.textMuted, maxLines = 1)
        }
        Icon(day.condition.icon, size = 20.dp, label = stringResource(day.condition.label))
        RoleText(
            if (day.rainChance > 0) "${day.rainChance}%" else "", Simplify.type.meta, Modifier.width(40.dp),
            color = Simplify.colors.textMuted, maxLines = 1, textAlign = TextAlign.End,
        )
        RoleText(degrees(day.low), Simplify.type.value, Modifier.width(34.dp), color = Simplify.colors.textMuted, maxLines = 1, textAlign = TextAlign.End)
        RangeBar(day.low, day.high, now, low, high, Modifier.weight(1f))
        RoleText(degrees(day.high), Simplify.type.value, Modifier.width(34.dp), maxLines = 1)
    }
}

@Composable
private fun RangeBar(from: Int, to: Int, now: Int?, low: Int, high: Int, modifier: Modifier) {
    val colors = Simplify.colors
    Canvas(modifier.height(10.dp)) {
        val track = 4.dp.toPx()
        val top = (size.height - track) / 2
        val radius = CornerRadius(track / 2)
        fun x(value: Int) = (value - low).toFloat() / (high - low).coerceAtLeast(1) * size.width
        drawRoundRect(colors.surfaceAlt, Offset(0f, top), Size(size.width, track), radius)
        drawRoundRect(colors.textMuted, Offset(x(from), top), Size((x(to) - x(from)).coerceAtLeast(track), track), radius)
        now?.let {
            val dot = 5.dp.toPx()
            val centre = Offset(x(it).coerceIn(dot, size.width - dot), size.height / 2)
            drawCircle(colors.background, dot, centre)
            drawCircle(colors.accent, 3.dp.toPx(), centre)
        }
    }
}

@Composable
private fun Details(weather: WeatherState.Ready, s: Settings) {
    val now = weather.current
    val sun = weather.sunOn(weather.local.toLocalDate())
    val imperial = s.temperatureUnit.imperial
    val rows = listOf(
        stringResource(R.string.weather_wind) to "${now.wind} ${s.windUnit.label} ${compass(now.windDirection)}",
        stringResource(R.string.weather_humidity) to "${now.humidity}%",
        stringResource(R.string.weather_pressure) to "${s.pressureUnit.of(now.pressure)} ${s.pressureUnit.label}",
        stringResource(R.string.weather_uv) to "${now.uv}",
        stringResource(R.string.weather_sunrise) to (sun?.let { clock(it.sunrise) } ?: "—"),
        stringResource(R.string.weather_sunset) to (sun?.let { clock(it.sunset) } ?: "—"),
        stringResource(R.string.weather_rain_today) to rainText(weather.today?.rain ?: 0.0, imperial),
        stringResource(R.string.weather_visibility) to visibilityText(now.visibility, imperial),
    )
    Column(
        Modifier
            .padding(horizontal = Space.screen)
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Simplify.colors.surface)
            .padding(horizontal = Space.rowGap, vertical = 6.dp),
    ) {
        rows.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(Space.screen)) {
                pair.forEach { (label, value) ->
                    Column(Modifier.weight(1f)) {
                        Row(Modifier.padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                            RoleText(label, Simplify.type.body, Modifier.weight(1f), color = Simplify.colors.textMuted, maxLines = 1)
                            RoleText(value, Simplify.type.value, Modifier.padding(start = Space.gutter), maxLines = 1)
                        }
                        Hairline()
                    }
                }
            }
        }
    }
}

@Composable
private fun Attribution() {
    val context = LocalContext.current
    RoleText(
        stringResource(R.string.weather_attribution),
        Simplify.type.meta,
        Modifier
            .padding(horizontal = Space.base, vertical = Space.base)
            .pressable({ context.startActivity(Intent(Intent.ACTION_VIEW, OPEN_METEO.toUri())) })
            .defaultMinSize(minHeight = Space.hitTarget)
            .padding(horizontal = Space.toolbarInset, vertical = 14.dp),
        color = Simplify.colors.textFaint,
    )
}
