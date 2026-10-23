package app.simplifymy.launcher.core.calendar

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
import androidx.core.content.ContextCompat
import app.simplifymy.launcher.core.settings.Settings
import app.simplifymy.launcher.core.settings.SettingsStore
import app.simplifymy.launcher.core.settings.WeekStart
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

data class CalendarInfo(val id: Long, val name: String, val account: String, val color: Int)

data class Event(
    val id: Long,
    val calendarId: Long,
    val title: String,
    val location: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val allDay: Boolean = false,
    val declined: Boolean = false,
    val color: Int = 0,
    val calendar: String = "",
) {
    fun on(date: LocalDate): Boolean =
        if (allDay) !date.isBefore(start.toLocalDate()) && date.isBefore(end.toLocalDate().coerceAtLeast(start.toLocalDate().plusDays(1)))
        else !date.isBefore(start.toLocalDate()) && (end.isAfter(date.atStartOfDay()) || start.toLocalDate() == date)
}

sealed interface CalendarPanel {
    data object Access : CalendarPanel
    data class Day(val date: LocalDate) : CalendarPanel
}

fun Event.millis(time: LocalDateTime): Long = time.atZone(if (allDay) ZoneOffset.UTC else ZoneId.systemDefault()).toInstant().toEpochMilli()

fun eventIntent(event: Event): Intent =
    Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(Events.CONTENT_URI, event.id))
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.millis(event.start))
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.millis(event.end))

fun dayIntent(date: LocalDate): Intent = Intent(
    Intent.ACTION_VIEW,
    ContentUris.withAppendedId(
        CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build(),
        date.atTime(LocalTime.NOON).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
    ),
)

fun calendarIntent(context: Context, wanted: Intent): Intent {
    val packages = context.packageManager
    val app = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR)
    val calendar = packages.resolveActivity(app, 0)?.activityInfo?.packageName?.takeIf { it != SYSTEM_PACKAGE }
    return when {
        calendar != null && packages.resolveActivity(Intent(wanted).setPackage(calendar), 0) != null -> Intent(wanted).setPackage(calendar)
        calendar != null -> app
        packages.resolveActivity(wanted, 0) != null -> wanted
        else -> app
    }
}

private const val SYSTEM_PACKAGE = "android"

fun newEventIntent(date: LocalDate, now: LocalDateTime = LocalDateTime.now()): Intent {
    val start = if (date == now.toLocalDate()) now.truncatedTo(ChronoUnit.HOURS).plusHours(1) else date.atTime(NEW_EVENT_HOUR, 0)
    val zone = ZoneId.systemDefault()
    return Intent(Intent.ACTION_INSERT, Events.CONTENT_URI)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start.atZone(zone).toInstant().toEpochMilli())
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, start.plusHours(1).atZone(zone).toInstant().toEpochMilli())
}

private const val NEW_EVENT_HOUR = 9

sealed interface CalendarState {
    data object NoAccess : CalendarState
    data class Ready(val calendars: List<CalendarInfo>, val events: List<Event>) : CalendarState
}

const val GRID_DAYS = 7

fun WeekStart.firstDay(locale: Locale): DayOfWeek = when (this) {
    WeekStart.Auto -> WeekFields.of(locale).firstDayOfWeek
    WeekStart.Monday -> DayOfWeek.MONDAY
    WeekStart.Sunday -> DayOfWeek.SUNDAY
}

fun monthGrid(month: YearMonth, firstDay: DayOfWeek): List<LocalDate> {
    val first = month.atDay(1)
    val start = first.minusDays(Math.floorMod(first.dayOfWeek.value - firstDay.value, GRID_DAYS).toLong())
    val cells = Math.floorMod(first.dayOfWeek.value - firstDay.value, GRID_DAYS) + month.lengthOfMonth()
    val weeks = (cells + GRID_DAYS - 1) / GRID_DAYS
    return (0 until weeks * GRID_DAYS).map { start.plusDays(it.toLong()) }
}

fun List<Event>.agenda(date: LocalDate, settings: Settings): List<Event> =
    filter { it.on(date) && (settings.calendarAllDay || !it.allDay) && (settings.calendarDeclined || !it.declined) }
        .sortedWith(compareBy({ !it.allDay }, { it.start }, { it.title }))

fun List<Event>.upcoming(now: LocalDateTime, settings: Settings): List<Event> =
    filter { !it.allDay && it.end.isAfter(now) && (settings.calendarDeclined || !it.declined) }
        .filter { it.start.toLocalDate().isBefore(now.toLocalDate().plusDays(settings.calendarDaysAhead.toLong() + 1)) }
        .sortedWith(compareBy({ it.start }, { it.title }))

fun List<Event>.laterToday(now: LocalDateTime, settings: Settings): Int =
    upcoming(now, settings).count { it.start.toLocalDate() == now.toLocalDate() }.minus(1).coerceAtLeast(0)

fun List<Event>.nextDay(today: LocalDate, settings: Settings): Pair<LocalDate, List<Event>>? =
    (1..settings.calendarDaysAhead).asSequence()
        .map { today.plusDays(it.toLong()) }
        .map { it to agenda(it, settings) }
        .firstOrNull { it.second.isNotEmpty() }

fun countdown(minutes: Long, days: String, hours: String, mins: String): String {
    val d = minutes / (24 * 60)
    val h = minutes % (24 * 60) / 60
    val m = minutes % 60
    val parts = when {
        d > 0 -> listOf(days.format(d), hours.format(h).takeIf { h > 0 })
        h > 0 -> listOf(hours.format(h), mins.format(m).takeIf { m > 0 })
        else -> listOf(mins.format(m))
    }
    return parts.filterNotNull().joinToString(" ")
}

class CalendarRepository(private val context: Context, private val settings: SettingsStore, scope: CoroutineScope) {
    private val changes = MutableStateFlow(0)
    private var observing = false

    val state: StateFlow<CalendarState> = combine(
        settings.settings.map { it.hiddenCalendars to it.calendarDaysAhead }.distinctUntilChanged(),
        changes,
    ) { (hidden, ahead), _ -> withContext(Dispatchers.IO) { load(hidden, ahead) } }
        .stateIn(scope, SharingStarted.Eagerly, CalendarState.NoAccess)

    init {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) = reload()
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    val granted: Boolean
        get() = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun reload() = changes.update { it + 1 }

    suspend fun events(from: LocalDate, until: LocalDate): List<Event> = withContext(Dispatchers.IO) {
        if (!granted) emptyList() else read(from, until, settings.settings.value.hiddenCalendars)
    }

    private fun load(hidden: Set<Long>, ahead: Int): CalendarState {
        if (!granted) return CalendarState.NoAccess
        observe()
        val today = LocalDate.now()
        val grid = monthGrid(YearMonth.from(today), DayOfWeek.MONDAY)
        val from = minOf(grid.first(), today).minusDays(GRID_DAYS.toLong())
        val until = maxOf(grid.last(), today.plusDays(ahead.toLong())).plusDays(GRID_DAYS.toLong())
        return runCatching { CalendarState.Ready(calendars(), read(from, until, hidden)) }.getOrDefault(CalendarState.NoAccess)
    }

    private fun observe() {
        if (observing) return
        observing = runCatching {
            context.contentResolver.registerContentObserver(Events.CONTENT_URI, true, object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) = reload()
            })
        }.isSuccess
    }

    private fun calendars(): List<CalendarInfo> = context.contentResolver.query(
        Calendars.CONTENT_URI,
        arrayOf(Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.ACCOUNT_NAME, Calendars.CALENDAR_COLOR),
        "${Calendars.VISIBLE} = 1", null, "${Calendars.ACCOUNT_NAME}, ${Calendars.CALENDAR_DISPLAY_NAME}",
    )?.use { c ->
        buildList {
            while (c.moveToNext()) add(CalendarInfo(c.getLong(0), c.getString(1).orEmpty(), c.getString(2).orEmpty(), c.getInt(3)))
        }
    }.orEmpty()

    private fun read(from: LocalDate, until: LocalDate, hidden: Set<Long>): List<Event> {
        val zone = ZoneId.systemDefault()
        val uri = Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, from.atStartOfDay(zone).toInstant().toEpochMilli())
            ContentUris.appendId(it, until.atStartOfDay(zone).toInstant().toEpochMilli())
        }.build()
        val projection = arrayOf(
            Instances.EVENT_ID, Instances.CALENDAR_ID, Instances.TITLE, Instances.EVENT_LOCATION, Instances.BEGIN, Instances.END,
            Instances.ALL_DAY, Instances.SELF_ATTENDEE_STATUS, Instances.DISPLAY_COLOR, Instances.CALENDAR_DISPLAY_NAME,
        )
        val selection = "${Instances.VISIBLE} = 1 AND (${Instances.STATUS} IS NULL OR ${Instances.STATUS} != ${Events.STATUS_CANCELED})"
        return context.contentResolver.query(uri, projection, selection, null, "${Instances.BEGIN} ASC")?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    if (c.getLong(1) in hidden) continue
                    val allDay = c.getInt(6) == 1
                    val clock = if (allDay) ZoneOffset.UTC else zone
                    add(
                        Event(
                            id = c.getLong(0),
                            calendarId = c.getLong(1),
                            title = c.getString(2).orEmpty(),
                            location = c.getString(3).orEmpty(),
                            start = LocalDateTime.ofInstant(Instant.ofEpochMilli(c.getLong(4)), clock),
                            end = LocalDateTime.ofInstant(Instant.ofEpochMilli(c.getLong(5)), clock),
                            allDay = allDay,
                            declined = c.getInt(7) == Attendees.ATTENDEE_STATUS_DECLINED,
                            color = c.getInt(8),
                            calendar = c.getString(9).orEmpty(),
                        )
                    )
                }
            }
        }.orEmpty()
    }
}
