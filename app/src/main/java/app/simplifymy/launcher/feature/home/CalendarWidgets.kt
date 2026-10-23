package app.simplifymy.launcher.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.simplifymy.launcher.R
import app.simplifymy.launcher.container
import app.simplifymy.launcher.core.calendar.CalendarState
import app.simplifymy.launcher.core.calendar.Event
import app.simplifymy.launcher.core.calendar.GRID_DAYS
import app.simplifymy.launcher.core.calendar.agenda
import app.simplifymy.launcher.core.calendar.countdown
import app.simplifymy.launcher.core.calendar.firstDay
import app.simplifymy.launcher.core.calendar.laterToday
import app.simplifymy.launcher.core.calendar.monthGrid
import app.simplifymy.launcher.core.calendar.nextDay
import app.simplifymy.launcher.core.calendar.upcoming
import app.simplifymy.launcher.core.settings.Settings
import app.simplifymy.launcher.core.weather.DayPoint
import app.simplifymy.launcher.core.weather.WeatherState
import app.simplifymy.launcher.ui.component.Icon
import app.simplifymy.launcher.ui.component.icon
import app.simplifymy.launcher.ui.launcher.InkOnDark
import app.simplifymy.launcher.ui.launcher.InkOnLight
import app.simplifymy.launcher.ui.theme.Simplify
import app.simplifymy.launcher.ui.theme.Space
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.TextStyle as DateStyle

class CalendarTaps(val event: (Event) -> Unit, val day: (LocalDate) -> Unit, val weather: () -> Unit, val clock: () -> Unit)

val LocalCalendarTaps = staticCompositionLocalOf<CalendarTaps?> { null }

private const val MAX_LABEL_CHARS = 20
private const val HEADER_ALPHA = 0.55f
private const val PLACE_ALPHA = 0.6f
private const val FADED_ALPHA = 0.3f
private const val DOT_ALPHA = 0.6f
private const val FORECAST_ALPHA = 0.75f
private val ROW_GAP = 9.dp

private fun Modifier.onTap(action: (() -> Unit)?): Modifier = if (action == null) this else composed {
    val current by rememberUpdatedState(action)
    pointerInput(Unit) { detectTapGestures(onTap = { current() }) }
}

@Composable
private fun launcherSettings(): Settings {
    val settings by LocalContext.current.container.settings.settings.collectAsState()
    return settings
}

@Composable
private fun calendarState(): CalendarState {
    val state by LocalContext.current.container.calendar.state.collectAsState()
    return state
}

@Composable
private fun calendarWeather(settings: Settings): WeatherState.Ready? =
    (weatherState() as? WeatherState.Ready)?.takeIf { settings.calendarWeather }

@Composable
private fun placeStyle(): TextStyle = Simplify.type.label.copy(fontWeight = FontWeight.Normal)

@Composable
private fun startLabel(event: Event, today: LocalDate): String = when {
    event.allDay -> stringResource(R.string.calendar_all_day)
    event.start.toLocalDate() == today -> hhmm(event.start)
    else -> format(event.start, "EEE ") + hhmm(event.start)
}

@Composable
private fun dayName(date: LocalDate, today: LocalDate): String =
    if (date == today.plusDays(1)) stringResource(R.string.calendar_tomorrow) else format(date, "EEE d")

@Composable
private fun TemperatureNow(weather: WeatherState.Ready) {
    val taps = LocalCalendarTaps.current
    Row(
        Modifier.onTap(taps?.weather),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (LocalWidgetStyle.current.icons.showIcon) WeatherIcon(weather.now.condition, 14.dp)
        Label(temperatureLabel(weather.now.temperature))
    }
}

@Composable
private fun forecastLabel(day: DayPoint): AnnotatedString = buildAnnotatedString {
    append(" · ")
    append(stringResource(day.condition.label).uppercase(locale()))
    append(" · ")
    append(temperatureLabel(day.high))
    append(" / ")
    append(temperatureLabel(day.low))
}

@Composable
private fun AccessNeeded(modifier: Modifier = Modifier) {
    val style = LocalWidgetStyle.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Label(stringResource(R.string.calendar_access_needed), color = style.secondary)
        Label(stringResource(R.string.calendar_tap_to_allow), color = style.ink.copy(alpha = HEADER_ALPHA))
    }
}

@Composable
private fun NothingPlanned() =
    EventTitle(stringResource(R.string.calendar_nothing_planned), color = LocalWidgetStyle.current.secondary)

@Composable
private fun EventTitle(title: String, modifier: Modifier = Modifier, color: Color = LocalWidgetStyle.current.ink, declined: Boolean = false) {
    Text(
        title.ifBlank { "—" }, modifier, color = color, style = Simplify.type.label,
        textDecoration = if (declined) TextDecoration.LineThrough else null,
        maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun EventRow(event: Event, today: LocalDate, location: Boolean) {
    val style = LocalWidgetStyle.current
    val taps = LocalCalendarTaps.current
    Row(Modifier.fillMaxWidth().onTap(taps?.let { { it.event(event) } }), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            startLabel(event, today), Modifier.width(54.dp), color = style.secondary, style = Simplify.type.value,
            maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
        )
        Column(Modifier.weight(1f)) {
            EventTitle(event.title, declined = event.declined)
            if (location && event.location.isNotBlank()) {
                Label(event.location, style = placeStyle(), color = style.ink.copy(alpha = PLACE_ALPHA))
            }
        }
    }
}

private fun <T> List<T>.fitting(space: Dp, height: (T) -> Dp): List<T> {
    var used = 0.dp
    return takeWhile {
        val next = used + height(it) + if (used > 0.dp) ROW_GAP else 0.dp
        (next <= space).also { fits -> if (fits) used = next }
    }
}

@Composable
internal fun CalendarAgendaWidget(modifier: Modifier) {
    val now = rememberNow()
    val settings = launcherSettings()
    val weather = calendarWeather(settings)
    val style = LocalWidgetStyle.current
    Row(modifier.fillMaxSize().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.width(64.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Label(format(now, "EEE"))
            Numerals(format(now, "d"), 52.sp)
            Label(format(now, "MMM"), color = style.secondary)
            weather?.let { TemperatureNow(it) }
        }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            when (val state = calendarState()) {
                CalendarState.NoAccess -> AccessNeeded(Modifier.align(Alignment.CenterStart))
                is CalendarState.Ready -> AgendaColumn(state.events, now.toLocalDateTime(), settings, weather)
            }
        }
    }
}

@Composable
private fun AgendaColumn(events: List<Event>, now: LocalDateTime, settings: Settings, weather: WeatherState.Ready?) {
    val style = LocalWidgetStyle.current
    val today = now.toLocalDate()
    val todays = events.agenda(today, settings).filter { it.allDay || it.end.isAfter(now) }.take(3)
    val next = events.nextDay(today, settings)
    val title = labelHeight(Simplify.type.label)
    val place = labelHeight(placeStyle())
    val header = labelHeight(Simplify.type.section)
    val height = { e: Event -> title + if (settings.calendarLocation && e.location.isNotBlank()) place else 0.dp }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val nextHeight = next?.let { (_, list) -> header + ROW_GAP + height(list.first()) + ROW_GAP } ?: 0.dp
        val withNext = todays.fitting(maxHeight - nextHeight, height)
        val showNext = next != null && (todays.isEmpty() || withNext.isNotEmpty())
        val shown = if (showNext) withNext else todays.fitting(maxHeight, height)
        Column(verticalArrangement = Arrangement.spacedBy(ROW_GAP)) {
            if (todays.isEmpty()) NothingPlanned()
            shown.forEach { EventRow(it, today, settings.calendarLocation) }
            if (showNext && next != null) {
                val (date, list) = next
                val forecast = weather?.days?.firstOrNull { it.date == date }
                Label(
                    buildAnnotatedString {
                        append(dayName(date, today).uppercase(locale()))
                        forecast?.let { append(forecastLabel(it)) }
                    },
                    color = style.ink.copy(alpha = HEADER_ALPHA),
                )
                EventRow(list.first(), date, settings.calendarLocation)
            }
        }
    }
}

@Composable
internal fun CalendarClockWidget(modifier: Modifier) {
    val now = rememberNow()
    val local = now.toLocalDateTime()
    val time = hhmm(now)
    val settings = launcherSettings()
    val weather = calendarWeather(settings)
    val style = LocalWidgetStyle.current
    val date = format(now, "EEE d MMM").uppercase(locale())
    val temperature = weather?.let { buildAnnotatedString { append(" · "); append(temperatureLabel(it.now.temperature)) } }
    val upcoming = (calendarState() as? CalendarState.Ready)?.events?.upcoming(local, settings)
    val next = upcoming?.firstOrNull()
    val more = upcoming?.let { it.laterToday(local, settings) } ?: 0
    val lines: List<Pair<String, Color>> = when {
        upcoming == null -> listOf(
            stringResource(R.string.calendar_access_needed) to style.secondary,
            stringResource(R.string.calendar_tap_to_allow) to style.ink.copy(alpha = HEADER_ALPHA),
        )
        next == null -> listOf(stringResource(R.string.calendar_nothing_planned) to style.secondary)
        else -> listOfNotNull(
            "${startLabel(next, local.toLocalDate())} ${next.title}" to style.highlight,
            if (more > 0) pluralStringResource(R.plurals.calendar_more_today, more, more) to style.secondary else null,
        )
    }
    val section = Simplify.type.section
    val taps = LocalCalendarTaps.current
    BoxWithConstraints(modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = Space.base)) {
        val chars = (lines.map { it.first.length } + (date.length + (temperature?.length ?: 0))).max().coerceAtMost(MAX_LABEL_CHARS)
        val labels = with(LocalDensity.current) { (section.fontSize * (LABEL_ADVANCE + section.letterSpacing.value) * chars).toDp() }
        val size = LocalDensity.current.fit(time, maxWidth - labels - 16.dp, maxHeight, 80.sp)
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.onTap(taps?.clock)) { Numerals(time, size) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row {
                    Label(date)
                    temperature?.let { Label(it, Modifier.onTap(taps?.weather)) }
                }
                lines.forEachIndexed { i, (text, color) ->
                    val tap = if (i == 0 && next != null) taps?.let { { it.event(next) } } else null
                    Label(text, Modifier.onTap(tap), color = color)
                }
            }
        }
    }
}

@Composable
internal fun CalendarTodayWidget(modifier: Modifier) {
    val now = rememberNow()
    val local = now.toLocalDateTime()
    val settings = launcherSettings()
    val weather = calendarWeather(settings)
    val style = LocalWidgetStyle.current
    val state = calendarState()
    val events = (state as? CalendarState.Ready)?.events
    val next = events?.upcoming(local, settings)?.firstOrNull()
    val taps = LocalCalendarTaps.current
    Column(modifier.fillMaxSize().padding(WIDGET_PADDING)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Label(format(now, "EEE"), Modifier.weight(1f))
            when {
                weather != null -> TemperatureNow(weather)
                events != null -> events.agenda(local.toLocalDate(), settings).size.let { count ->
                    Label(pluralStringResource(R.plurals.calendar_events, count, count), color = style.secondary)
                }
            }
        }
        FitNumerals(format(now, "d"), Modifier.fillMaxWidth().weight(1f), cap = 72.sp, alignment = Alignment.CenterStart)
        when {
            events == null -> AccessNeeded()
            next == null -> NothingPlanned()
            else -> Column(Modifier.onTap(taps?.let { { it.event(next) } }), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(startLabel(next, local.toLocalDate()), color = style.highlight, style = Simplify.type.value, maxLines = 1, softWrap = false)
                EventTitle(next.title, declined = next.declined)
            }
        }
    }
}

@Composable
internal fun CalendarNextWidget(modifier: Modifier) {
    val now = rememberNow()
    val local = now.toLocalDateTime()
    val settings = launcherSettings()
    val style = LocalWidgetStyle.current
    val events = (calendarState() as? CalendarState.Ready)?.events
    val next = events?.upcoming(local, settings)?.firstOrNull()
    val taps = LocalCalendarTaps.current
    Column(
        modifier.fillMaxSize().padding(horizontal = 14.dp).onTap(next?.let { e -> taps?.let { { it.event(e) } } }),
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        when {
            events == null -> AccessNeeded()
            next == null -> {
                Label(format(now, "EEE d MMM"), color = style.secondary)
                NothingPlanned()
            }
            else -> {
                Label("${stringResource(R.string.calendar_next)} · ${startLabel(next, local.toLocalDate())}", color = style.highlight)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EventTitle(next.title, Modifier.weight(1f), declined = next.declined)
                    Label(untilLabel(local, next.start), color = style.secondary)
                }
            }
        }
    }
}

@Composable
private fun untilLabel(now: LocalDateTime, start: LocalDateTime): String {
    val minutes = Duration.between(now, start).toMinutes()
    if (minutes <= 0) return stringResource(R.string.calendar_now)
    val text = countdown(
        minutes,
        stringResource(R.string.calendar_days_short),
        stringResource(R.string.calendar_hours_short),
        stringResource(R.string.calendar_minutes_short),
    )
    return stringResource(R.string.calendar_in, text)
}

@Composable
internal fun CalendarMonthWidget(modifier: Modifier) {
    val now = rememberNow()
    val today = now.toLocalDate()
    val settings = launcherSettings()
    val weather = calendarWeather(settings)
    val style = LocalWidgetStyle.current
    val locale = locale()
    val state = calendarState()
    val month = YearMonth.from(today)
    val grid = remember(month, settings.weekStart, locale) { monthGrid(month, settings.weekStart.firstDay(locale)) }
    val events = (state as? CalendarState.Ready)?.events
    val busy = remember(events, grid, settings) { events?.let { list -> grid.filter { list.agenda(it, settings).isNotEmpty() }.toSet() }.orEmpty() }
    val forecast = weather?.takeIf { settings.calendarForecast }?.days?.associateBy { it.date }.orEmpty()
    Column(modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Label(format(today, "LLLL yyyy"), Modifier.weight(1f))
            when {
                events == null -> Label(stringResource(R.string.calendar_access_needed), color = style.secondary)
                weather != null -> Label(
                    modifier = Modifier.onTap(LocalCalendarTaps.current?.weather),
                    text = buildAnnotatedString {
                        append(cityName().uppercase(locale))
                        append(" · ")
                        append(temperatureLabel(weather.now.temperature, " " + conditionWord(weather.now.condition)))
                    },
                    color = style.secondary,
                )
                else -> {
                    val count = grid.filter { YearMonth.from(it) == month }.flatMap { events.agenda(it, settings) }.distinct().size
                    Label(pluralStringResource(R.plurals.calendar_events, count, count), color = style.secondary)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            grid.take(GRID_DAYS).forEach { day ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Label(day.dayOfWeek.getDisplayName(DateStyle.NARROW_STANDALONE, locale), color = style.ink.copy(alpha = HEADER_ALPHA))
                }
            }
        }
        grid.chunked(GRID_DAYS).forEach { week ->
            Row(Modifier.fillMaxWidth().weight(1f)) {
                week.forEach { day ->
                    DayCell(day, today, YearMonth.from(day) == month, day in busy, forecast[day], Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, today: LocalDate, inMonth: Boolean, busy: Boolean, forecast: DayPoint?, modifier: Modifier) {
    val style = LocalWidgetStyle.current
    val taps = LocalCalendarTaps.current
    val isToday = date == today
    val onToday = when {
        style.highlight == Simplify.colors.accent -> Simplify.colors.onAccent
        style.highlight.luminance() > 0.5f -> InkOnLight
        else -> InkOnDark
    }
    Column(
        modifier.onTap(taps?.let { { it.day(date) } }),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        Box(
            Modifier.size(26.dp).clip(CircleShape).background(if (isToday) style.highlight else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${date.dayOfMonth}",
                color = when {
                    isToday -> onToday
                    inMonth -> style.ink
                    else -> style.ink.copy(alpha = FADED_ALPHA)
                },
                style = Simplify.type.value,
                fontWeight = if (isToday) FontWeight.Medium else null,
                maxLines = 1,
                softWrap = false,
            )
        }
        Box(Modifier.height(11.dp), contentAlignment = Alignment.Center) {
            if (forecast != null && inMonth) {
                Icon(forecast.condition.icon, tint = style.ink.copy(alpha = FORECAST_ALPHA), size = 11.dp, decorative = true)
            } else {
                Box(
                    Modifier.size(4.dp).clip(CircleShape)
                        .background(if (busy && inMonth && !isToday) style.ink.copy(alpha = DOT_ALPHA) else Color.Transparent)
                )
            }
        }
    }
}
