package app.simplifymy.launcher.feature.home

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.text.format.DateFormat
import androidx.annotation.StringRes
import app.simplifymy.launcher.ui.component.icon
import app.simplifymy.launcher.ui.component.Icon
import app.simplifymy.launcher.feature.settings.Page
import app.simplifymy.launcher.core.weather.WeatherNow
import app.simplifymy.launcher.core.weather.Condition
import app.simplifymy.launcher.core.settings.WeatherIcons
import app.simplifymy.launcher.SettingsActivity
import app.simplifymy.launcher.WeatherActivity
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import app.simplifymy.launcher.core.weather.HourPoint
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import app.simplifymy.launcher.R
import app.simplifymy.launcher.container
import app.simplifymy.launcher.core.settings.City
import app.simplifymy.launcher.core.settings.Settings
import app.simplifymy.launcher.core.settings.WidgetColor
import app.simplifymy.launcher.core.settings.WidgetWeight
import app.simplifymy.launcher.core.weather.WeatherState
import app.simplifymy.launcher.core.workspace.ItemEntity
import app.simplifymy.launcher.ui.launcher.BoundsRef
import app.simplifymy.launcher.ui.launcher.InkOnDark
import app.simplifymy.launcher.ui.launcher.InkOnLight
import app.simplifymy.launcher.ui.launcher.LocalLabelColor
import app.simplifymy.launcher.ui.launcher.trackBounds
import app.simplifymy.launcher.ui.launcher.degrees
import app.simplifymy.launcher.ui.launcher.zeros
import app.simplifymy.launcher.ui.theme.PlexMono
import app.simplifymy.launcher.ui.theme.Radius
import app.simplifymy.launcher.ui.theme.RoleText
import app.simplifymy.launcher.ui.theme.Simplify
import app.simplifymy.launcher.ui.theme.Sizes
import app.simplifymy.launcher.ui.theme.Space
import app.simplifymy.launcher.feature.calendar.openDay
import app.simplifymy.launcher.feature.calendar.openEvent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAccessor
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay

private const val PREFIX = "builtin:"

enum class BuiltinWidget(
    val id: String,
    @StringRes val label: Int,
    val spanX: Int,
    val spanY: Int,
    val weather: Boolean = false,
    val calendar: Boolean = false,
) {
    Clock("clock", R.string.widget_clock, 4, 2),
    ClockDate("clock_date", R.string.widget_clock_date, 4, 2),
    Stacked("stacked", R.string.widget_stacked, 2, 2),
    Split("split", R.string.widget_split, 4, 2),
    Analog("analog", R.string.widget_analog, 2, 2),
    Line("line", R.string.widget_line, 4, 1),
    ClockAlarm("clock_alarm", R.string.widget_clock_alarm, 4, 2),
    World("world", R.string.widget_world, 4, 2),
    CalendarAgenda("calendar_agenda", R.string.widget_calendar_agenda, 4, 2, calendar = true),
    CalendarClock("calendar_clock", R.string.widget_calendar_clock, 4, 2, calendar = true),
    CalendarToday("calendar_today", R.string.widget_calendar_today, 2, 2, calendar = true),
    CalendarNext("calendar_next", R.string.widget_calendar_next, 4, 1, calendar = true),
    CalendarMonth("calendar_month", R.string.widget_calendar_month, 4, 3, calendar = true),
    Weather("weather", R.string.widget_weather, 2, 2, weather = true),
    WeatherNext("weather_next", R.string.widget_weather_next, 2, 2, weather = true),
    WeatherHourly("weather_hourly", R.string.widget_weather_hourly, 4, 2, weather = true),
    WeatherForecast("weather_forecast", R.string.widget_weather_forecast, 4, 2, weather = true),
    ClockWeather("clock_weather", R.string.widget_clock_weather, 4, 2, weather = true),
    WeatherLine("weather_line", R.string.widget_weather_line, 4, 1, weather = true),
    Date("date", R.string.widget_date, 2, 2),
    Battery("battery", R.string.widget_battery, 2, 2),
    ;

    val provider: String get() = PREFIX + id

    companion object {
        fun of(provider: String?): BuiltinWidget? =
            provider?.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)?.let { id -> entries.firstOrNull { it.id == id } }
    }
}

fun BuiltinWidget.tapIntent(context: Context, weather: WeatherState): Intent = when {
    this.weather && weather !is WeatherState.Ready -> SettingsActivity.open(context, Page.Weather)
    this.weather -> WeatherActivity.open(context)
    this == BuiltinWidget.Date -> Intent(Intent.ACTION_VIEW, CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build())
    this == BuiltinWidget.Battery -> Intent(Intent.ACTION_POWER_USAGE_SUMMARY)
    else -> Intent(AlarmClock.ACTION_SHOW_ALARMS)
}

data class WidgetStyle(val ink: Color, val highlight: Color, val weight: FontWeight, val icons: WeatherIcons = WeatherIcons.Both) {
    val soft: Color get() = ink.copy(alpha = 0.4f)
    val secondary: Color get() = ink.copy(alpha = 0.7f)
    val rule: Color get() = ink.copy(alpha = 0.25f)
}

val LocalWidgetStyle = staticCompositionLocalOf { WidgetStyle(InkOnDark, InkOnDark, FontWeight.Normal) }

@Composable
fun widgetStyle(settings: Settings, auto: Color): WidgetStyle {
    val accent = Simplify.colors.accent
    val ink = when (settings.widgetColor) {
        WidgetColor.Auto -> auto
        WidgetColor.White -> InkOnDark
        WidgetColor.Black -> InkOnLight
        WidgetColor.Accent -> accent
    }
    val weight = if (settings.widgetWeight == WidgetWeight.Regular) FontWeight.Normal else FontWeight.Medium
    return WidgetStyle(ink, if (settings.widgetColor == WidgetColor.Accent) auto else accent, weight, settings.weatherIcons)
}

@Composable
fun BuiltinWidgetContent(widget: BuiltinWidget, modifier: Modifier = Modifier) {
    when (widget) {
        BuiltinWidget.Clock -> ClockWidget(modifier)
        BuiltinWidget.ClockDate -> ClockDateWidget(modifier)
        BuiltinWidget.Stacked -> StackedWidget(modifier)
        BuiltinWidget.Split -> SplitWidget(modifier)
        BuiltinWidget.Analog -> AnalogWidget(modifier)
        BuiltinWidget.Line -> LineWidget(modifier)
        BuiltinWidget.ClockAlarm -> ClockAlarmWidget(modifier)
        BuiltinWidget.World -> WorldWidget(modifier)
        BuiltinWidget.CalendarAgenda -> CalendarAgendaWidget(modifier)
        BuiltinWidget.CalendarClock -> CalendarClockWidget(modifier)
        BuiltinWidget.CalendarToday -> CalendarTodayWidget(modifier)
        BuiltinWidget.CalendarNext -> CalendarNextWidget(modifier)
        BuiltinWidget.CalendarMonth -> CalendarMonthWidget(modifier)
        BuiltinWidget.Weather -> WeatherWidget(modifier)
        BuiltinWidget.WeatherNext -> NextHoursWidget(modifier)
        BuiltinWidget.WeatherHourly -> HourlyWidget(modifier)
        BuiltinWidget.WeatherForecast -> ForecastWidget(modifier)
        BuiltinWidget.ClockWeather -> ClockWeatherWidget(modifier)
        BuiltinWidget.WeatherLine -> WeatherLineWidget(modifier)
        BuiltinWidget.Date -> DateWidget(modifier)
        BuiltinWidget.Battery -> BatteryWidget(modifier)
    }
}

fun offsetLabel(localSeconds: Int, otherSeconds: Int): String {
    val diff = otherSeconds - localSeconds
    val minutes = abs(diff) / 60
    val sign = if (diff < 0) "\u2212" else if (diff > 0) "+" else ""
    val rest = minutes % 60
    return sign + "${minutes / 60}H" + if (rest == 0) "" else String.format(Locale.ROOT, "%02d", rest)
}

private const val LINE = 0.9f
private const val ADVANCE = 0.62f
private const val LABEL_LINE = 1.4f

private fun numeralStyle(size: TextUnit, color: Color, weight: FontWeight) = TextStyle(
    fontFamily = PlexMono,
    fontWeight = weight,
    fontSize = size,
    letterSpacing = (-0.02).em,
    lineHeight = LINE.em,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
    fontFeatureSettings = "tnum",
    color = color,
    textAlign = TextAlign.Center,
)

internal fun Density.fit(text: String, width: Dp, height: Dp, cap: TextUnit = TextUnit.Unspecified): TextUnit {
    val lines = text.lines()
    val byHeight = (height / (lines.size * LINE)).coerceAtLeast(1.dp).toSp()
    val byWidth = (width / (lines.maxOf { it.length } * ADVANCE)).coerceAtLeast(1.dp).toSp()
    return listOfNotNull(byHeight, byWidth, cap.takeIf { it.isSpecified }).minBy { it.value }
}

@Composable
internal fun labelHeight(style: TextStyle): Dp = with(LocalDensity.current) { (style.fontSize * LABEL_LINE).toDp() }

@Composable
internal fun Numerals(text: String, size: TextUnit, modifier: Modifier = Modifier) {
    val style = LocalWidgetStyle.current
    Text(zeros(text, style.highlight, style.soft), modifier, style = numeralStyle(size, style.ink, style.weight), softWrap = false)
}

@Composable
internal fun FitNumerals(text: String, modifier: Modifier = Modifier, cap: TextUnit = TextUnit.Unspecified, alignment: Alignment = Alignment.Center) {
    BoxWithConstraints(modifier, contentAlignment = alignment) {
        Numerals(text, LocalDensity.current.fit(text, maxWidth, maxHeight, cap))
    }
}

@Composable
internal fun Label(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    style: TextStyle = Simplify.type.section,
    color: Color = LocalWidgetStyle.current.ink,
) {
    Text(text, modifier, color = color, style = style, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
}

@Composable
internal fun Label(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = Simplify.type.section,
    color: Color = LocalWidgetStyle.current.ink,
) {
    Label(AnnotatedString(if (style == Simplify.type.section) text.uppercase(locale()) else text), modifier, style, color)
}

@Composable
internal fun locale(): Locale = LocalConfiguration.current.locales[0]

@Composable
internal fun temperatureLabel(temperature: Int, rest: String = ""): AnnotatedString {
    val style = LocalWidgetStyle.current
    return zeros(degrees(temperature), style.highlight, style.soft) + AnnotatedString(rest.uppercase(locale()))
}

@Composable
private fun nowLabel(weather: WeatherState.Ready, extra: String? = null): AnnotatedString {
    val word = conditionWord(weather.now.condition).takeIf { LocalWidgetStyle.current.icons.showWord }
    return temperatureLabel(weather.now.temperature, listOfNotNull(word, extra).joinToString(" · ").let { if (it.isEmpty()) "" else " $it" })
}

@Composable
private fun weatherLine(): AnnotatedString? = (weatherState() as? WeatherState.Ready)?.let { nowLabel(it) }

@Composable
private fun dateWithWeather(date: String): AnnotatedString {
    val weather = weatherLine()
    return buildAnnotatedString {
        append(date.uppercase(locale()))
        weather?.let { append("  ·  "); append(it) }
    }
}

@Composable
internal fun rememberNow(): ZonedDateTime {
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = ZonedDateTime.now()
            delay((60 - now.second) * 1000L - now.nano / 1_000_000L + 50L)
        }
    }
    return now
}

@Composable
private fun rememberNextAlarm(): Long? {
    val context = LocalContext.current
    val alarms = remember(context) { context.getSystemService(AlarmManager::class.java) }
    var next by remember { mutableStateOf(alarms.nextAlarmClock?.triggerTime) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) { next = alarms.nextAlarmClock?.triggerTime }
        }
        context.registerReceiver(receiver, IntentFilter(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED))
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    return next
}

@Composable
internal fun format(time: TemporalAccessor, pattern: String): String =
    DateTimeFormatter.ofPattern(pattern, LocalConfiguration.current.locales[0]).format(time)

@Composable
private fun hourPattern(): String = if (DateFormat.is24HourFormat(LocalContext.current)) "HH" else "hh"

@Composable
internal fun hhmm(time: TemporalAccessor): String = format(time, hourPattern() + ":mm")

@Composable
private fun stacked(time: TemporalAccessor): String = format(time, hourPattern() + "\nmm")

@Composable
private fun shortDate(time: TemporalAccessor): String = format(time, "EEE d")

@Composable
private fun ClockWidget(modifier: Modifier) {
    FitNumerals(hhmm(rememberNow()), modifier.fillMaxSize().padding(Space.base), cap = 88.sp)
}

@Composable
private fun ClockDateWidget(modifier: Modifier) {
    val now = rememberNow()
    val time = hhmm(now)
    val date = dateWithWeather(format(now, "EEE d MMM"))
    val label = labelHeight(Simplify.type.section)
    BoxWithConstraints(modifier.fillMaxSize().padding(Space.base)) {
        val size = LocalDensity.current.fit(time, maxWidth, maxHeight - label - 8.dp, 80.sp)
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        ) {
            Numerals(time, size)
            Label(date)
        }
    }
}

@Composable
private fun StackedWidget(modifier: Modifier) {
    val now = rememberNow()
    val time = stacked(now)
    val label = labelHeight(Simplify.type.section)
    BoxWithConstraints(modifier.fillMaxSize().padding(Space.base)) {
        val size = LocalDensity.current.fit(time, maxWidth, maxHeight - label - 8.dp, 88.sp)
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        ) {
            Numerals(time, size)
            Label(shortDate(now))
        }
    }
}

@Composable
private fun SplitClock(now: ZonedDateTime, modifier: Modifier, right: @Composable BoxWithConstraintsScope.() -> Unit) {
    val time = stacked(now)
    val style = LocalWidgetStyle.current
    BoxWithConstraints(modifier.fillMaxSize().padding(Space.base)) {
        val size = LocalDensity.current.fit(time, maxWidth * 0.4f, maxHeight, 88.sp)
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Numerals(time, size)
            Box(Modifier.padding(vertical = 24.dp).width(Sizes.hairline).fillMaxHeight().background(style.rule))
            BoxWithConstraints(Modifier.weight(1f), contentAlignment = Alignment.CenterStart, content = right)
        }
    }
}

@Composable
private fun SplitWidget(modifier: Modifier) {
    val now = rememberNow()
    val day = format(now, "dd")
    val weather = weatherLine()
    SplitClock(now, modifier) {
        val daySize = LocalDensity.current.fit(day, maxWidth, maxHeight, 56.sp)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Label(format(now, "EEEE"))
            Numerals(day, daySize)
            Label(format(now, "LLLL"), style = Simplify.type.label)
            weather?.let { Label(it) }
        }
    }
}

@Composable
private fun AnalogWidget(modifier: Modifier) {
    val now = rememberNow()
    val style = LocalWidgetStyle.current
    val label = labelHeight(Simplify.type.section)
    BoxWithConstraints(modifier.fillMaxSize().padding(Space.base)) {
        val dial = minOf(136.dp, maxWidth, maxHeight - label - 10.dp).coerceAtLeast(1.dp)
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        ) {
            Canvas(Modifier.size(dial)) { drawDial(now, style) }
            Label(shortDate(now))
        }
    }
}

private fun DrawScope.drawDial(now: ZonedDateTime, style: WidgetStyle) {
    val unit = size.minDimension / 136f
    val centre = Offset(68f, 68f) * unit
    fun at(angle: Double, radius: Float) = Offset(68f + radius * sin(angle).toFloat(), 68f - radius * cos(angle).toFloat()) * unit
    drawCircle(style.rule, 66f * unit, centre, style = Stroke(1.5f * unit))
    (1..11).forEach { i ->
        val angle = i * PI / 6
        drawLine(style.ink, at(angle, 58f), at(angle, if (i % 3 == 0) 50f else 54f), 1.5f * unit, StrokeCap.Round)
    }
    drawCircle(style.highlight, 3.5f * unit, Offset(68f, 9f) * unit)
    val hour = (now.hour % 12 + now.minute / 60.0) * PI / 6
    drawLine(style.ink, centre, at(hour, 36f), 4f * unit, StrokeCap.Round)
    drawLine(style.ink, centre, at(now.minute * PI / 30, 54f), 2.5f * unit, StrokeCap.Round)
    drawCircle(style.ink, 4f * unit, centre)
}

@Composable
private fun LineWidget(modifier: Modifier) {
    val now = rememberNow()
    val time = hhmm(now)
    val weather = weatherLine()
    BoxWithConstraints(modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = Space.base)) {
        val size = LocalDensity.current.fit(time, maxWidth * 0.65f, maxHeight, 64.sp)
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Numerals(time, size)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Label(format(now, "EEE d MMM"))
                weather?.let { Label(it) }
            }
        }
    }
}

@Composable
private fun ClockAlarmWidget(modifier: Modifier) {
    val now = rememberNow()
    val time = hhmm(now)
    val alarm = rememberNextAlarm()?.let { hhmm(Instant.ofEpochMilli(it).atZone(now.zone)) }
    val label = labelHeight(Simplify.type.section)
    val style = LocalWidgetStyle.current
    BoxWithConstraints(modifier.fillMaxSize().padding(Space.base)) {
        val size = LocalDensity.current.fit(time, maxWidth, maxHeight - label * 2 - 16.dp, 80.sp)
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        ) {
            Label(format(now, "EEEE"))
            Numerals(time, size)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Label(shortDate(now))
                if (alarm != null) {
                    Box(Modifier.size(3.dp).clip(CircleShape).background(style.soft))
                    Label(stringResource(R.string.widget_alarm, alarm))
                }
            }
        }
    }
}

private fun localCityName(weatherCity: City?, zone: ZoneId): String =
    weatherCity?.takeIf { it.zone?.rules == zone.rules }?.name
        ?: zone.id.substringAfterLast('/').replace('_', ' ')

@Composable
private fun WorldWidget(modifier: Modifier) {
    val now = rememberNow()
    val settings by LocalContext.current.container.settings.settings.collectAsState()
    val other = settings.worldCity?.let { city -> city.zone?.let { city.name to now.withZoneSameInstant(it) } }
    val style = LocalWidgetStyle.current
    val local = stringResource(R.string.widget_local)
    BoxWithConstraints(modifier.fillMaxSize().padding(Space.base)) {
        val column = if (other == null) maxWidth else (maxWidth - 33.dp) / 2
        val size = LocalDensity.current.fit(hhmm(now), column, maxHeight, 48.sp)
        Row(
            Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        ) {
            WorldColumn(localCityName(settings.weatherCity, now.zone), now, local, size, Modifier.weight(1f, fill = other != null))
            if (other != null) {
                Box(Modifier.width(Sizes.hairline).height(96.dp).background(style.rule))
                val (name, time) = other
                WorldColumn(name, time, offsetLabel(now.offset.totalSeconds, time.offset.totalSeconds), size, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun WorldColumn(city: String, time: ZonedDateTime, note: String, size: TextUnit, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Label(city)
        Numerals(hhmm(time), size)
        Label("${shortDate(time)} · $note".uppercase(locale()), style = Simplify.type.meta, color = LocalWidgetStyle.current.secondary)
    }
}

@Composable
internal fun weatherState(): WeatherState {
    val state by LocalContext.current.container.weather.state.collectAsState()
    return state
}

@Composable
internal fun cityName(): String {
    val settings by LocalContext.current.container.settings.settings.collectAsState()
    return settings.weatherCity?.name.orEmpty()
}

@Composable
internal fun conditionWord(condition: Condition) = stringResource(condition.label)

internal fun highLow(now: WeatherNow) = "${degrees(now.high)} / ${degrees(now.low)}"

@Composable
private fun hourLabel(time: TemporalAccessor): String =
    format(time, if (DateFormat.is24HourFormat(LocalContext.current)) "HH:mm" else "h a")

@Composable
private fun largeValue(): TextStyle = Simplify.type.value.let { it.copy(fontSize = it.fontSize * LARGE_VALUE) }

private const val LARGE_VALUE = 15f / 13f
internal const val LABEL_ADVANCE = 0.6f
internal val WIDGET_PADDING = PaddingValues(horizontal = 14.dp, vertical = 12.dp)

@Composable
internal fun WeatherIcon(condition: Condition, size: Dp) {
    Icon(condition.icon, tint = LocalWidgetStyle.current.ink, size = size)
}

@Composable
private fun noWeatherText(state: WeatherState): String? = when (state) {
    WeatherState.Off -> R.string.widget_weather_off
    WeatherState.NoCity -> R.string.widget_choose_city
    WeatherState.Loading, is WeatherState.Ready -> null
    WeatherState.Offline -> R.string.widget_no_connection
}?.let { stringResource(it) }

@Composable
private fun NoWeather(state: WeatherState, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(Space.base), contentAlignment = Alignment.Center) { noWeatherText(state)?.let { Label(it) } }
}

@Composable
private fun WeatherContent(modifier: Modifier, content: @Composable (WeatherState.Ready) -> Unit) {
    when (val state = weatherState()) {
        is WeatherState.Ready -> content(state)
        else -> NoWeather(state, modifier)
    }
}

@Composable
private fun WeatherWidget(modifier: Modifier) = WeatherContent(modifier) { weather ->
    val style = LocalWidgetStyle.current
    val footer = listOfNotNull(conditionWord(weather.now.condition).takeIf { style.icons.showWord }, highLow(weather.now)).joinToString(" · ")
    Column(modifier.fillMaxSize().padding(WIDGET_PADDING), verticalArrangement = Arrangement.SpaceBetween) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Label(cityName(), Modifier.weight(1f))
            if (style.icons.showIcon) WeatherIcon(weather.now.condition, 22.dp)
        }
        FitNumerals(degrees(weather.now.temperature), Modifier.fillMaxWidth().weight(1f), cap = 72.sp, alignment = Alignment.CenterStart)
        Label(footer, color = style.secondary)
    }
}

@Composable
private fun HourRow(hour: HourPoint, modifier: Modifier = Modifier) {
    val style = LocalWidgetStyle.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Label(hourLabel(hour.time), Modifier.weight(1f), style = Simplify.type.value, color = style.secondary)
        if (style.icons.showIcon) WeatherIcon(hour.condition, 18.dp)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { Label(degrees(hour.temperature), style = largeValue()) }
    }
}

@Composable
private fun NextHoursWidget(modifier: Modifier) = WeatherContent(modifier) { weather ->
    val row = labelHeight(largeValue())
    BoxWithConstraints(modifier.fillMaxSize().padding(WIDGET_PADDING)) {
        val count = ((maxHeight - labelHeight(Simplify.type.section)) / (row + 4.dp)).toInt().coerceIn(0, 4)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Label(stringResource(R.string.widget_weather_next))
            weather.next(count, 3).forEach { HourRow(it) }
        }
    }
}

@Composable
private fun HourlyWidget(modifier: Modifier) = WeatherContent(modifier) { weather ->
    val style = LocalWidgetStyle.current
    Column(modifier.fillMaxSize().padding(WIDGET_PADDING), verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Label(listOf(cityName(), stringResource(R.string.widget_weather_next)).joinToString(" · "), Modifier.weight(1f))
            Label(nowLabel(weather))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            weather.next(6, 3).forEach { hour ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Label(hourLabel(hour.time), style = Simplify.type.value, color = style.secondary)
                    if (style.icons.showIcon) WeatherIcon(hour.condition, 22.dp)
                    Label(degrees(hour.temperature), style = largeValue())
                }
            }
        }
    }
}

@Composable
private fun ForecastWidget(modifier: Modifier) = WeatherContent(modifier) { weather ->
    val style = LocalWidgetStyle.current
    val today = weather.local.toLocalDate()
    val updated = stringResource(R.string.weather_updated, hhmm(weather.now.updated.atZone(ZoneId.systemDefault())))
    Column(modifier.fillMaxSize().padding(WIDGET_PADDING), verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Label(stringResource(R.string.widget_days, cityName()), Modifier.weight(1f))
            Label(updated, color = style.secondary)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            weather.days.take(6).forEach { day ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Label(format(day.date, "EEE"), color = if (day.date == today) style.highlight else style.secondary)
                    if (style.icons.showIcon) WeatherIcon(day.condition, 22.dp)
                    Label(degrees(day.high), style = largeValue())
                    Label(degrees(day.low), style = Simplify.type.value, color = style.ink.copy(alpha = 0.6f))
                }
            }
        }
    }
}

@Composable
private fun ClockWeatherWidget(modifier: Modifier) {
    val now = rememberNow()
    val time = hhmm(now)
    val state = weatherState()
    val style = LocalWidgetStyle.current
    val ready = state as? WeatherState.Ready
    val date = format(now, "EEE d MMM")
    val condition = ready?.let { nowLabel(it) } ?: AnnotatedString(noWeatherText(state).orEmpty().uppercase(locale()))
    val place = ready?.let { listOf(cityName(), highLow(it.now)).joinToString(" · ") }
    val icon = ready != null && style.icons.showIcon
    val section = Simplify.type.section
    BoxWithConstraints(modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = Space.base)) {
        val chars = maxOf(date.length, condition.length, place?.length ?: 0)
        val labels = with(LocalDensity.current) { (section.fontSize * (LABEL_ADVANCE + section.letterSpacing.value) * chars).toDp() } +
            if (icon) 22.dp else 0.dp
        val size = LocalDensity.current.fit(time, maxWidth - labels - 18.dp, maxHeight, 80.sp)
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Numerals(time, size)
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Label(date)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (icon) WeatherIcon(ready.now.condition, 16.dp)
                    Label(condition)
                }
                place?.let { Label(it, color = style.secondary) }
            }
        }
    }
}

@Composable
private fun WeatherLineWidget(modifier: Modifier) {
    val now = rememberNow()
    val state = weatherState()
    val icons = LocalWidgetStyle.current.icons
    Row(
        modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = Space.base),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Label(format(now, "EEE d MMM"), Modifier.weight(1f))
        when (state) {
            is WeatherState.Ready -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (icons.showIcon && !icons.showWord) WeatherIcon(state.now.condition, 16.dp)
                Label(nowLabel(state, cityName()))
            }
            else -> noWeatherText(state)?.let { Label(it) }
        }
    }
}

@Composable
private fun DateWidget(modifier: Modifier) {
    val now = rememberNow()
    Column(modifier.fillMaxSize().padding(Space.base), horizontalAlignment = Alignment.CenterHorizontally) {
        Label(format(now, "EEEE"))
        FitNumerals(format(now, "dd"), Modifier.fillMaxWidth().weight(1f))
        Label(format(now, "LLLL"), style = Simplify.type.label)
        weatherLine()?.let { Label(it) }
    }
}

private data class BatteryState(val percent: Int, val charging: Boolean)

@Composable
private fun rememberBattery(): BatteryState {
    val context = LocalContext.current
    var state by remember { mutableStateOf(readBattery(context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)))) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) { state = readBattery(intent) }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    return state
}

private fun readBattery(intent: Intent?): BatteryState {
    val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
    val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    val percent = if (level < 0 || scale <= 0) 0 else level * 100 / scale
    return BatteryState(percent, status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL)
}

@Composable
private fun BatteryWidget(modifier: Modifier) {
    val battery = rememberBattery()
    val color = LocalWidgetStyle.current.ink
    Column(
        modifier.fillMaxSize().padding(Space.base + Space.gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.gutter),
    ) {
        FitNumerals("${battery.percent}%", Modifier.fillMaxWidth().weight(1f))
        Box(Modifier.fillMaxWidth(0.8f).height(3.dp).clip(Radius.pill).background(color.copy(alpha = 0.25f))) {
            Box(Modifier.fillMaxWidth(battery.percent / 100f).fillMaxHeight().clip(Radius.pill).background(color))
        }
        Label(stringResource(if (battery.charging) R.string.widget_charging else R.string.widget_battery))
    }
}

@Composable
fun BuiltinWidgetItem(widget: BuiltinWidget, item: ItemEntity, host: ItemHost, modifier: Modifier, locked: Boolean) {
    val context = LocalContext.current
    val container = context.container
    val bounds = remember { BoundsRef() }
    val settings by container.settings.settings.collectAsState()
    CompositionLocalProvider(
        LocalWidgetStyle provides widgetStyle(settings, LocalLabelColor.current),
        LocalCalendarTaps provides remember(container) {
            CalendarTaps(
                event = container::openEvent,
                day = container::openDay,
                weather = { container.actions.start(BuiltinWidget.Weather.tapIntent(context, container.weather.state.value)) },
                clock = { container.actions.start(BuiltinWidget.Clock.tapIntent(context, container.weather.state.value)) },
            )
        },
    ) {
    BuiltinWidgetContent(
        widget,
        modifier
            .trackBounds(bounds)
            .itemGestures(
                key = item.id,
                onTap = {
                    if (widget.calendar) container.openDay(LocalDate.now())
                    else container.actions.start(widget.tapIntent(context, container.weather.state.value))
                },
                onLongPress = { at -> if (!locked) host.startDrag(item, Origin.Desktop, null, bounds.rect, at) },
                onDrag = host::dragTo,
                onDragEnd = host::endDrag,
            ),
    )
    }
}
