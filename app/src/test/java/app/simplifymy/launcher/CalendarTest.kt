package app.simplifymy.launcher

import app.simplifymy.launcher.core.calendar.Event
import app.simplifymy.launcher.core.calendar.agenda
import app.simplifymy.launcher.core.calendar.countdown
import app.simplifymy.launcher.core.calendar.firstDay
import app.simplifymy.launcher.core.calendar.laterToday
import app.simplifymy.launcher.core.calendar.monthGrid
import app.simplifymy.launcher.core.calendar.nextDay
import app.simplifymy.launcher.core.calendar.upcoming
import app.simplifymy.launcher.core.settings.CalendarColours
import app.simplifymy.launcher.core.settings.CalendarTap
import app.simplifymy.launcher.core.settings.Settings
import app.simplifymy.launcher.core.settings.SettingsJson
import app.simplifymy.launcher.core.settings.StoredSettings
import app.simplifymy.launcher.core.settings.WeekStart
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MonthGridTest {
    @Test fun octoberFromMonday() {
        val grid = monthGrid(YearMonth.of(2026, 10), DayOfWeek.MONDAY)
        assertEquals(35, grid.size)
        assertEquals(LocalDate.of(2026, 9, 28), grid.first())
        assertEquals(LocalDate.of(2026, 11, 1), grid.last())
    }

    @Test fun octoberFromSunday() {
        val grid = monthGrid(YearMonth.of(2026, 10), DayOfWeek.SUNDAY)
        assertEquals(LocalDate.of(2026, 9, 27), grid.first())
        assertEquals(DayOfWeek.SUNDAY, grid.first().dayOfWeek)
        assertEquals(35, grid.size)
    }

    @Test fun everyMonthStartsOnTheWeekStartAndHoldsTheWholeMonth() {
        for (year in 2024..2028) for (month in 1..12) for (start in listOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY, DayOfWeek.SATURDAY)) {
            val ym = YearMonth.of(year, month)
            val grid = monthGrid(ym, start)
            assertEquals(start, grid.first().dayOfWeek)
            assertEquals(0, grid.size % 7)
            assertTrue(grid.size in 28..42)
            assertTrue(grid.contains(ym.atDay(1)) && grid.contains(ym.atEndOfMonth()))
            assertTrue(grid.takeLast(7).any { YearMonth.from(it) == ym })
        }
    }

    @Test fun februaryThatFitsFourWeeks() = assertEquals(28, monthGrid(YearMonth.of(2027, 2), DayOfWeek.MONDAY).size)

    @Test fun weekStartFollowsSettingOrLocale() {
        assertEquals(DayOfWeek.MONDAY, WeekStart.Auto.firstDay(Locale.forLanguageTag("uk-UA")))
        assertEquals(DayOfWeek.SUNDAY, WeekStart.Auto.firstDay(Locale.US))
        assertEquals(DayOfWeek.SUNDAY, WeekStart.Sunday.firstDay(Locale.forLanguageTag("uk-UA")))
    }
}

class AgendaTest {
    private val day = LocalDate.of(2026, 10, 23)
    private fun at(h: Int, m: Int = 0, d: LocalDate = day) = d.atTime(h, m)
    private fun event(id: Long, start: LocalDateTime, end: LocalDateTime, allDay: Boolean = false, declined: Boolean = false, title: String = "E$id") =
        Event(id, 1, title, "", start, end, allDay, declined)

    private val dentist = event(3, at(16, 30), at(17, 30), title = "Dentist")
    private val standup = event(1, at(10), at(10, 30), title = "Standup")
    private val lunch = event(2, at(13), at(14), title = "Lunch")
    private val trip = event(4, day.atStartOfDay(), day.plusDays(1).atStartOfDay(), allDay = true, title = "Trip")
    private val skipped = event(5, at(11), at(12), declined = true)
    private val tomorrow = event(6, at(9, d = day.plusDays(1)), at(10, d = day.plusDays(1)))
    private val events = listOf(dentist, tomorrow, standup, skipped, trip, lunch)

    @Test fun allDayFirstThenByStart() =
        assertEquals(listOf(trip, standup, lunch, dentist), events.agenda(day, Settings()))

    @Test fun declinedShownWhenAsked() =
        assertEquals(listOf(trip, standup, skipped, lunch, dentist), events.agenda(day, Settings(calendarDeclined = true)))

    @Test fun allDayHiddenWhenOff() =
        assertEquals(listOf(standup, lunch, dentist), events.agenda(day, Settings(calendarAllDay = false)))

    @Test fun allDayEndIsExclusive() = assertTrue(trip !in events.agenda(day.plusDays(1), Settings()))

    @Test fun timedEventAcrossMidnightShowsOnBothDays() {
        val late = event(7, at(23), at(1, d = day.plusDays(1)))
        assertEquals(listOf(late), listOf(late).agenda(day, Settings()))
        assertEquals(listOf(late), listOf(late).agenda(day.plusDays(1), Settings()))
    }

    @Test fun upcomingSkipsPastAndAllDay() =
        assertEquals(listOf(lunch, dentist, tomorrow), events.upcoming(at(11, 30), Settings()))

    @Test fun ongoingEventIsStillNext() = assertEquals(standup, events.upcoming(at(10, 15), Settings()).first())

    @Test fun moreTodayCountsTheRestOfToday() {
        assertEquals(2, events.laterToday(at(9, 41), Settings()))
        assertEquals(0, events.laterToday(at(15), Settings()))
        assertEquals(0, events.laterToday(at(18), Settings()))
    }

    @Test fun nextDayLooksAsFarAsDaysAhead() {
        val later = event(8, at(9, d = day.plusDays(5)), at(10, d = day.plusDays(5)))
        assertEquals(day.plusDays(1), events.nextDay(day, Settings())?.first)
        assertNull(listOf(later).nextDay(day, Settings(calendarDaysAhead = 3)))
        assertEquals(day.plusDays(5), listOf(later).nextDay(day, Settings(calendarDaysAhead = 7))?.first)
    }

    @Test fun countdownText() {
        fun c(minutes: Long) = countdown(minutes, "%d d", "%d h", "%d m")
        assertEquals("6 h 49 m", c(6 * 60 + 49))
        assertEquals("2 h", c(120))
        assertEquals("49 m", c(49))
        assertEquals("0 m", c(0))
        assertEquals("1 d 3 h", c(27 * 60 + 10))
        assertEquals("2 d", c(48 * 60 + 5))
    }
}

class CalendarSettingsTest {
    @Test fun roundTrip() {
        val settings = Settings(
            hiddenCalendars = setOf(3L, 9L), calendarDaysAhead = 7, calendarAllDay = false, calendarDeclined = true,
            calendarLocation = false, calendarWeather = false, calendarForecast = false, weekStart = WeekStart.Sunday,
            calendarColours = CalendarColours.Dots, eventTap = CalendarTap.Agenda, dayTap = CalendarTap.Calendar,
        )
        assertEquals(settings, SettingsJson.decodeFromString(StoredSettings, SettingsJson.encodeToString(StoredSettings, settings)))
    }

    @Test fun oldSettingsGetCalendarDefaults() {
        val decoded = SettingsJson.decodeFromString(StoredSettings, """{"desktopColumns":6}""")
        assertEquals(Settings(desktopColumns = 6), decoded)
        assertEquals(CalendarColours.Off, decoded.calendarColours)
        assertEquals(3, decoded.calendarDaysAhead)
    }
}
