package app.simplifymy.launcher.feature.calendar

import app.simplifymy.launcher.AppContainer
import app.simplifymy.launcher.core.calendar.CalendarPanel
import app.simplifymy.launcher.core.calendar.Event
import app.simplifymy.launcher.core.calendar.calendarIntent
import app.simplifymy.launcher.core.calendar.dayIntent
import app.simplifymy.launcher.core.calendar.eventIntent
import app.simplifymy.launcher.core.settings.CalendarTap
import java.time.LocalDate

fun AppContainer.openDay(date: LocalDate) {
    when {
        !calendar.granted -> calendarPanel.value = CalendarPanel.Access
        settings.settings.value.dayTap == CalendarTap.Agenda -> calendarPanel.value = CalendarPanel.Day(date)
        else -> actions.start(calendarIntent(app, dayIntent(date)))
    }
}

fun AppContainer.openEvent(event: Event) {
    when (settings.settings.value.eventTap) {
        CalendarTap.Calendar -> actions.start(calendarIntent(app, eventIntent(event)))
        CalendarTap.Agenda -> calendarPanel.value = CalendarPanel.Day(event.start.toLocalDate())
    }
}
