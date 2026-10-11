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
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import app.simplifymy.launcher.ui.theme.PlexMono
import app.simplifymy.launcher.ui.theme.Radius
import app.simplifymy.launcher.ui.theme.RoleText
import app.simplifymy.launcher.ui.theme.Simplify
import app.simplifymy.launcher.ui.theme.Sizes
import app.simplifymy.launcher.ui.theme.Space
import java.time.Instant
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

enum class BuiltinWidget(val id: String, @StringRes val label: Int, val spanX: Int, val spanY: Int, val weather: Boolean = false) {
    Clock("clock", R.string.widget_clock, 4, 2),
    ClockDate("clock_date", R.string.widget_clock_date, 4, 2),
    Stacked("stacked", R.string.widget_stacked, 2, 2),
    Split("split", R.string.widget_split, 4, 2),
    Analog("analog", R.string.widget_analog, 2, 2),
    Line("line", R.string.widget_line, 4, 1),
    ClockAlarm("clock_alarm", R.string.widget_clock_alarm, 4, 2),
    World("world", R.string.widget_world, 4, 2),
    Weather("weather", R.string.widget_weather, 2, 2, weather = true),
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
    this.weather && weather !is WeatherState.Ready -> SettingsActivity.open(context, Page.Widgets)
    this == BuiltinWidget.ClockWeather -> Intent(AlarmClock.ACTION_SHOW_ALARMS)
    this.weather -> weatherApp(context) ?: SettingsActivity.open(context, Page.Widgets)
    this == BuiltinWidget.Date -> Intent(Intent.ACTION_VIEW, CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build())
    this == BuiltinWidget.Battery -> Intent(Intent.ACTION_POWER_USAGE_SUMMARY)
    else -> Intent(AlarmClock.ACTION_SHOW_ALARMS)
}

private fun weatherApp(context: Context): Intent? =
    context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        .firstOrNull { it.activityInfo.packageName.contains("weather", ignoreCase = true) }
        ?.let { context.packageManager.getLaunchIntentForPackage(it.activityInfo.packageName) }

data class WidgetStyle(val ink: Color, val highlight: Color, val weight: FontWeight, val icons: WeatherIcons = WeatherIcons.Both) {
    val soft: Color get() = ink.copy(alpha = 0.4f)
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
        BuiltinWidget.Weather -> WeatherWidget(modifier)
        BuiltinWidget.WeatherHourly -> HourlyWidget(modifier)
        BuiltinWidget.WeatherForecast -> ForecastWidget(modifier)
        BuiltinWidget.ClockWeather -> ClockWeatherWidget(modifier)
        BuiltinWidget.WeatherLine -> WeatherLineWidget(modifier)
        BuiltinWidget.Date -> DateWidget(modifier)
        BuiltinWidget.Battery -> BatteryWidget(modifier)
    }
}

fun zeros(text: String, highlight: Color, colon: Color): AnnotatedString = buildAnnotatedString {
    text.forEach { c ->
        when (c) {
            '0' -> withStyle(SpanStyle(color = highlight)) { append(c) }
            ':', '°' -> withStyle(SpanStyle(color = colon)) { append(c) }
            else -> append(c)
        }
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

private fun Density.fit(text: String, width: Dp, height: Dp, cap: TextUnit = TextUnit.Unspecified): TextUnit {
    val lines = text.lines()
    val byHeight = (height / (lines.size * LINE)).coerceAtLeast(1.dp).toSp()
    val byWidth = (width / (lines.maxOf { it.length } * ADVANCE)).coerceAtLeast(1.dp).toSp()
    return listOfNotNull(byHeight, byWidth, cap.takeIf { it.isSpecified }).minBy { it.value }
}

@Composable
private fun labelHeight(style: TextStyle): Dp = with(LocalDensity.current) { (style.fontSize * LABEL_LINE).toDp() }

@Composable
private fun Numerals(text: String, size: TextUnit, modifier: Modifier = Modifier) {
    val style = LocalWidgetStyle.current
    Text(zeros(text, style.highlight, style.soft), modifier, style = numeralStyle(size, style.ink, style.weight), softWrap = false)
}

@Composable
private fun FitNumerals(text: String, modifier: Modifier = Modifier, cap: TextUnit = TextUnit.Unspecified) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        Numerals(text, LocalDensity.current.fit(text, maxWidth, maxHeight, cap))
    }
}

@Composable
private fun Label(text: String, style: TextStyle = Simplify.type.section) {
    val locale = LocalConfiguration.current.locales[0]
    RoleText(if (style == Simplify.type.section) text.uppercase(locale) else text, style, color = LocalWidgetStyle.current.ink, maxLines = 1)
}

@Composable
private fun weatherLine(): String? {
    val state by LocalContext.current.container.weather.state.collectAsState()
    val ready = state as? WeatherState.Ready ?: return null
    return "${ready.now.temperature}° ${stringResource(ready.now.condition.label)}"
}

@Composable
private fun rememberNow(): ZonedDateTime {
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
private fun format(time: TemporalAccessor, pattern: String): String =
    DateTimeFormatter.ofPattern(pattern, LocalConfiguration.current.locales[0]).format(time)

@Composable
private fun hourPattern(): String = if (DateFormat.is24HourFormat(LocalContext.current)) "HH" else "hh"

@Composable
private fun hhmm(time: TemporalAccessor): String = format(time, hourPattern() + ":mm")

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
    val date = listOfNotNull(format(now, "EEE d MMM"), weatherLine()).joinToString("  ·  ")
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
            Label(format(now, "LLLL"), Simplify.type.label)
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

private fun City.zone(): ZoneId? = runCatching { ZoneId.of(timezone) }.getOrNull()

private fun localCityName(weatherCity: City?, zone: ZoneId): String =
    weatherCity?.takeIf { it.zone()?.rules == zone.rules }?.name
        ?: zone.id.substringAfterLast('/').replace('_', ' ')

@Composable
private fun WorldWidget(modifier: Modifier) {
    val now = rememberNow()
    val settings by LocalContext.current.container.settings.settings.collectAsState()
    val other = settings.worldCity?.let { city -> city.zone()?.let { city.name to now.withZoneSameInstant(it) } }
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
        Label("${shortDate(time)} · $note".uppercase(LocalConfiguration.current.locales[0]), Simplify.type.meta)
    }
}

@Composable
private fun weatherState(): WeatherState {
    val state by LocalContext.current.container.weather.state.collectAsState()
    return state
}

@Composable
private fun cityName(): String {
    val settings by LocalContext.current.container.settings.settings.collectAsState()
    return settings.weatherCity?.name.orEmpty()
}

private fun degrees(value: Int) = "$value°"

@Composable
private fun conditionWord(condition: Condition) = stringResource(condition.label)

@Composable
private fun highLow(now: WeatherNow) = stringResource(R.string.widget_high_low, degrees(now.high), degrees(now.low))

@Composable
private fun WeatherIcon(condition: Condition, size: Dp) {
    Icon(condition.icon, tint = LocalWidgetStyle.current.ink, size = size)
}

@Composable
private fun ConditionRow(condition: Condition, iconSize: Dp = 22.dp) {
    val icons = LocalWidgetStyle.current.icons
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (icons.showIcon) WeatherIcon(condition, iconSize)
        if (icons.showWord) Label(conditionWord(condition), Simplify.type.label)
    }
}

@Composable
private fun NoWeather(state: WeatherState, modifier: Modifier = Modifier) {
    val text = when (state) {
        WeatherState.Off -> R.string.widget_weather_off
        WeatherState.NoCity -> R.string.widget_choose_city
        else -> R.string.widget_no_connection
    }
    Box(modifier.fillMaxSize().padding(Space.base), contentAlignment = Alignment.Center) { Label(stringResource(text)) }
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
    val temperature = degrees(weather.now.temperature)
    val reserve = labelHeight(Simplify.type.section) + maxOf(22.dp, labelHeight(Simplify.type.label)) + labelHeight(Simplify.type.meta) + 24.dp
    BoxWithConstraints(modifier.fillMaxSize().padding(Space.base)) {
        val size = LocalDensity.current.fit(temperature, maxWidth, maxHeight - reserve, 72.sp)
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        ) {
            Label(cityName())
            Numerals(temperature, size)
            ConditionRow(weather.now.condition)
            Label(highLow(weather.now), Simplify.type.meta)
        }
    }
}

@Composable
private fun HourlyWidget(modifier: Modifier) = WeatherContent(modifier) { weather ->
    val temperature = degrees(weather.now.temperature)
    val style = LocalWidgetStyle.current
    val pattern = hourPattern()
    val locale = LocalConfiguration.current.locales[0]
    val hourRow = labelHeight(Simplify.type.meta) + labelHeight(Simplify.type.value) + 8.dp + if (style.icons.showIcon) 24.dp else 0.dp
    BoxWithConstraints(modifier.fillMaxSize().padding(16.dp)) {
        val size = LocalDensity.current.fit(temperature, maxWidth * 0.5f, maxHeight - hourRow - 33.dp, 72.sp)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Numerals(temperature, size)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Label(cityName())
                    ConditionRow(weather.now.condition)
                }
            }
            Box(Modifier.fillMaxWidth().height(Sizes.hairline).background(style.rule))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                weather.hours.forEach { hour ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        RoleText(DateTimeFormatter.ofPattern(pattern, locale).format(hour.time), Simplify.type.meta, color = style.soft, maxLines = 1)
                        if (style.icons.showIcon) WeatherIcon(hour.condition, 20.dp)
                        RoleText(degrees(hour.temperature), Simplify.type.value, color = style.ink, maxLines = 1)
                    }
                }
            }
        }
    }
}

private val FORECAST_ROW = 30.dp

@Composable
private fun ForecastWidget(modifier: Modifier) = WeatherContent(modifier) { weather ->
    val style = LocalWidgetStyle.current
    val today = stringResource(R.string.widget_today)
    val header = listOf(cityName(), "${degrees(weather.now.temperature)} ${conditionWord(weather.now.condition)}").joinToString(" · ")
    val headerHeight = labelHeight(Simplify.type.section) + 6.dp
    BoxWithConstraints(modifier.fillMaxSize().padding(16.dp)) {
        val rows = ((maxHeight - headerHeight) / (FORECAST_ROW + 4.dp)).toInt().coerceIn(0, weather.days.size)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)) {
            Box(Modifier.padding(bottom = 6.dp)) { Label(header) }
            weather.days.take(rows).forEachIndexed { index, day ->
                Row(Modifier.fillMaxWidth().height(FORECAST_ROW), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.width(64.dp)) { Label(if (index == 0) today else format(day.date, "EEE")) }
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (style.icons.showIcon) WeatherIcon(day.condition, 20.dp)
                        if (style.icons.showWord) Label(conditionWord(day.condition), Simplify.type.label)
                    }
                    RoleText("${degrees(day.low)} / ${degrees(day.high)}", Simplify.type.value, color = style.ink, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun ClockWeatherWidget(modifier: Modifier) {
    val now = rememberNow()
    val state = weatherState()
    val city = cityName()
    val date = shortDate(now)
    val reserve = labelHeight(Simplify.type.section) * 2 + maxOf(22.dp, labelHeight(Simplify.type.label)) + 18.dp
    SplitClock(now, modifier) {
        when (state) {
            is WeatherState.Ready -> {
                val temperature = degrees(state.now.temperature)
                val size = LocalDensity.current.fit(temperature, maxWidth, maxHeight - reserve, 56.sp)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Label(listOf(city, date).joinToString(" · "))
                    Numerals(temperature, size)
                    ConditionRow(state.now.condition)
                    Label(highLow(state.now))
                }
            }
            else -> NoWeather(state)
        }
    }
}

@Composable
private fun WeatherLineWidget(modifier: Modifier) = WeatherContent(modifier) { weather ->
    val temperature = degrees(weather.now.temperature)
    val icons = LocalWidgetStyle.current.icons
    val title = listOfNotNull(cityName(), conditionWord(weather.now.condition).takeIf { icons.showWord }).joinToString(" · ")
    BoxWithConstraints(modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = Space.base)) {
        val size = LocalDensity.current.fit(temperature, maxWidth * 0.4f, maxHeight, 64.sp)
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Numerals(temperature, size)
            if (icons.showIcon) WeatherIcon(weather.now.condition, 32.dp)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Label(title)
                Label(highLow(weather.now))
            }
        }
    }
}

@Composable
private fun DateWidget(modifier: Modifier) {
    val now = rememberNow()
    Column(modifier.fillMaxSize().padding(Space.base), horizontalAlignment = Alignment.CenterHorizontally) {
        Label(format(now, "EEEE"))
        FitNumerals(format(now, "dd"), Modifier.fillMaxWidth().weight(1f))
        Label(format(now, "LLLL"), Simplify.type.label)
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
    CompositionLocalProvider(LocalWidgetStyle provides widgetStyle(settings, LocalLabelColor.current)) {
    BuiltinWidgetContent(
        widget,
        modifier
            .trackBounds(bounds)
            .itemGestures(
                key = item.id,
                onTap = { container.actions.start(widget.tapIntent(context, container.weather.state.value)) },
                onLongPress = { at -> if (!locked) host.startDrag(item, Origin.Desktop, null, bounds.rect, at) },
                onDrag = host::dragTo,
                onDragEnd = host::endDrag,
            ),
    )
    }
}
