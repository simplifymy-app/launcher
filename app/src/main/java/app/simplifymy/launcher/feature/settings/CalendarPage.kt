package app.simplifymy.launcher.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.simplifymy.launcher.R
import app.simplifymy.launcher.container
import app.simplifymy.launcher.core.calendar.CalendarState
import app.simplifymy.launcher.core.settings.CalendarColours
import app.simplifymy.launcher.core.settings.CalendarTap
import app.simplifymy.launcher.core.settings.DAYS_AHEAD
import app.simplifymy.launcher.core.settings.WeekStart
import app.simplifymy.launcher.feature.calendar.rememberCalendarAccess
import app.simplifymy.launcher.feature.home.BuiltinWidget
import app.simplifymy.launcher.ui.component.ButtonKind
import app.simplifymy.launcher.ui.component.SettingsRow
import app.simplifymy.launcher.ui.component.SimplifyButton
import app.simplifymy.launcher.ui.component.Toggle
import app.simplifymy.launcher.ui.theme.RoleText
import app.simplifymy.launcher.ui.theme.Simplify
import app.simplifymy.launcher.ui.theme.Space
import app.simplifymy.launcher.ui.theme.States
import kotlinx.coroutines.flow.map

@Composable
private fun SectionNote(@StringRes title: Int, note: String?) {
    Row(
        Modifier.fillMaxWidth().padding(top = Space.section - Space.base).padding(horizontal = Space.screen, vertical = Space.base),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoleText(stringResource(title).uppercase(), Simplify.type.section, Modifier.weight(1f), color = Simplify.colors.textMuted)
        note?.let { RoleText(it.uppercase(), Simplify.type.section, color = Simplify.colors.textFaint, maxLines = 1) }
    }
}

@Composable
internal fun CalendarPage(back: () -> Unit, open: (Page) -> Unit) {
    val (s, update) = settings()
    val container = LocalContext.current.container
    val state by container.calendar.state.collectAsState()
    val placed by remember { container.workspace.items.map { items -> items.count { BuiltinWidget.of(it.provider)?.calendar == true } } }
        .collectAsState(0)
    val access = rememberCalendarAccess()
    onResume { container.calendar.reload() }
    val meta = "${stringResource(R.string.page_widgets)} · ${pluralStringResource(R.plurals.calendar_widgets_placed, placed, placed)}"
    SettingsPage(stringResource(R.string.page_calendar), back, meta = meta) {
        val ready = state as? CalendarState.Ready
        Section(R.string.section_access)
        SettingsRow(stringResource(R.string.calendar_access), subline = stringResource(R.string.calendar_access_hint)) {
            if (ready != null) {
                RoleText(stringResource(R.string.calendar_allowed), Simplify.type.value, color = Simplify.colors.accent)
            } else {
                SimplifyButton(stringResource(R.string.calendar_allow), access.ask, kind = ButtonKind.Primary)
            }
        }
        if (ready != null) {
            val calendars = ready.calendars
            val shown = calendars.count { it.id !in s.hiddenCalendars }
            SectionNote(R.string.section_calendars, pluralStringResource(R.plurals.calendar_shown, calendars.size, shown, calendars.size).takeIf { calendars.isNotEmpty() })
            if (calendars.isEmpty()) SettingsRow(stringResource(R.string.calendar_none))
            calendars.forEach { calendar ->
                val on = calendar.id !in s.hiddenCalendars
                val toggle = { v: Boolean ->
                    update { it.copy(hiddenCalendars = if (v) it.hiddenCalendars - calendar.id else it.hiddenCalendars + calendar.id) }
                }
                SettingsRow(calendar.name, subline = calendar.account.ifBlank { null }, onClick = { toggle(!on) }) { Toggle(on, toggle) }
            }
        }

        Section(R.string.section_show)
        ChoiceRow(R.string.calendar_days_ahead, DAYS_AHEAD, s.calendarDaysAhead, optionLabel = { "$it" }) { v ->
            update { it.copy(calendarDaysAhead = v) }
        }
        ToggleRow(R.string.calendar_all_day_events, s.calendarAllDay, stringResource(R.string.calendar_all_day_hint)) { v ->
            update { it.copy(calendarAllDay = v) }
        }
        ToggleRow(R.string.calendar_declined, s.calendarDeclined, stringResource(R.string.calendar_declined_hint)) { v ->
            update { it.copy(calendarDeclined = v) }
        }
        ToggleRow(R.string.calendar_location, s.calendarLocation, stringResource(R.string.calendar_location_hint)) { v ->
            update { it.copy(calendarLocation = v) }
        }

        val city = s.weatherCity?.takeIf { s.weather }
        SectionNote(R.string.section_weather, city?.let { "${it.name} · Open-Meteo" })
        if (city == null) {
            NavRow(R.string.page_weather, stringResource(R.string.calendar_weather_off)) { open(Page.Weather) }
        } else {
            ToggleRow(R.string.calendar_show_weather, s.calendarWeather, stringResource(R.string.calendar_show_weather_hint)) { v ->
                update { it.copy(calendarWeather = v) }
            }
            Box(Modifier.alpha(if (s.calendarWeather) 1f else States.DISABLED)) {
                ToggleRow(R.string.calendar_forecast, s.calendarForecast && s.calendarWeather, stringResource(R.string.calendar_forecast_hint)) { v ->
                    if (s.calendarWeather) update { it.copy(calendarForecast = v) }
                }
            }
        }

        Section(R.string.section_look)
        ChoiceRow(R.string.calendar_week_starts, WeekStart.entries, s.weekStart, optionLabel = { stringResource(it.label) }) { v ->
            update { it.copy(weekStart = v) }
        }
        ChoiceRow(
            R.string.calendar_colours, CalendarColours.entries, s.calendarColours,
            subline = stringResource(R.string.calendar_colours_hint),
            optionLabel = { stringResource(it.label) },
        ) { v -> update { it.copy(calendarColours = v) } }

        Section(R.string.section_tap)
        ChoiceRow(R.string.tap_event, CalendarTap.entries, s.eventTap, segmented = false, optionLabel = { stringResource(it.label) }) { v ->
            update { it.copy(eventTap = v) }
        }
        ChoiceRow(R.string.tap_day, CalendarTap.entries, s.dayTap, segmented = false, optionLabel = { stringResource(it.label) }) { v ->
            update { it.copy(dayTap = v) }
        }
    }
}
