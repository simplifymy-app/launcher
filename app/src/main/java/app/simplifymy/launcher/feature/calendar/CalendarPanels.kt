package app.simplifymy.launcher.feature.calendar

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings as AndroidSettings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.ActivityCompat
import androidx.core.net.toUri
import app.simplifymy.launcher.R
import app.simplifymy.launcher.container
import app.simplifymy.launcher.core.calendar.CalendarPanel
import app.simplifymy.launcher.core.calendar.Event
import app.simplifymy.launcher.core.calendar.agenda
import app.simplifymy.launcher.core.calendar.calendarIntent
import app.simplifymy.launcher.core.calendar.dayIntent
import app.simplifymy.launcher.core.calendar.eventIntent
import app.simplifymy.launcher.core.calendar.newEventIntent
import app.simplifymy.launcher.core.settings.CalendarColours
import app.simplifymy.launcher.core.weather.WeatherState
import app.simplifymy.launcher.ui.Localized
import app.simplifymy.launcher.ui.component.BottomSheet
import app.simplifymy.launcher.ui.component.ButtonKind
import app.simplifymy.launcher.ui.component.Icon
import app.simplifymy.launcher.ui.component.IconAction
import app.simplifymy.launcher.ui.component.Icons
import app.simplifymy.launcher.ui.component.SimplifyButton
import app.simplifymy.launcher.ui.component.icon
import app.simplifymy.launcher.ui.component.pressable
import app.simplifymy.launcher.ui.launcher.degrees
import app.simplifymy.launcher.ui.theme.CornerRadius
import app.simplifymy.launcher.ui.theme.Radius
import app.simplifymy.launcher.ui.theme.RoleText
import app.simplifymy.launcher.ui.theme.Simplify
import app.simplifymy.launcher.ui.theme.Space
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAccessor

class CalendarAccess(val blocked: Boolean, val ask: () -> Unit)

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

@Composable
fun rememberCalendarAccess(onResult: (granted: Boolean, blocked: Boolean) -> Unit = { _, _ -> }): CalendarAccess {
    val context = LocalContext.current
    val container = context.container
    var blocked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        container.calendar.reload()
        val activity = context.activity()
        blocked = !granted && activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.READ_CALENDAR)
        onResult(granted, blocked)
    }
    return CalendarAccess(blocked) {
        if (blocked) {
            container.actions.start(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()))
        } else {
            launcher.launch(Manifest.permission.READ_CALENDAR)
        }
    }
}

@Composable
fun CalendarPanels() {
    val container = LocalContext.current.container
    val panel by container.calendarPanel.collectAsState()
    val close = { container.calendarPanel.value = null }
    val access = rememberCalendarAccess { granted, blocked -> if (granted || !blocked) close() }
    when (val p = panel) {
        CalendarPanel.Access -> AccessDialog(access, close)
        is CalendarPanel.Day -> AgendaSheet(p.date, close)
        null -> Unit
    }
}

@Composable
private fun AccessDialog(access: CalendarAccess, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Localized {
            Column(
                Modifier
                    .padding(Space.screen)
                    .widthIn(max = 420.dp)
                    .clip(Radius.content(CornerRadius.L))
                    .background(Simplify.colors.surfaceAlt)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(Space.rowGap),
            ) {
                Icon(Icons.Calendar, size = 28.dp, decorative = true)
                RoleText(stringResource(R.string.calendar_access_title), Simplify.type.headline)
                RoleText(stringResource(R.string.calendar_access_body), Simplify.type.body, color = Simplify.colors.textMuted)
                Column(
                    Modifier.fillMaxWidth().clip(Radius.card).background(Simplify.colors.surface).padding(Space.rowGap),
                    verticalArrangement = Arrangement.spacedBy(Space.base + Space.gutter),
                ) {
                    listOf(R.string.calendar_read_only, R.string.calendar_stays, R.string.calendar_choose).forEach {
                        RoleText(stringResource(it).uppercase(), Simplify.type.value)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = Space.base), horizontalArrangement = Arrangement.spacedBy(Space.base)) {
                    SimplifyButton(stringResource(R.string.calendar_not_now), onDismiss, Modifier.weight(1f), ButtonKind.Ghost)
                    SimplifyButton(stringResource(R.string.calendar_continue), access.ask, Modifier.weight(1f), ButtonKind.Primary)
                }
                RoleText(
                    stringResource(if (access.blocked) R.string.calendar_android_settings else R.string.calendar_android_asks),
                    Simplify.type.meta, Modifier.fillMaxWidth(), color = Simplify.colors.textFaint, textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun format(time: TemporalAccessor, pattern: String): String =
    DateTimeFormatter.ofPattern(pattern, LocalConfiguration.current.locales[0]).format(time)

@Composable
private fun clock(time: LocalDateTime): String =
    format(time, if (DateFormat.is24HourFormat(LocalContext.current)) "HH:mm" else "h:mm a")

@Composable
private fun AgendaSheet(start: LocalDate, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val container = context.container
    val settings by container.settings.settings.collectAsState()
    val calendar by container.calendar.state.collectAsState()
    val weather by container.weather.state.collectAsState()
    var date by remember(start) { mutableStateOf(start) }
    val events by produceState<List<Event>?>(null, date, calendar) { value = container.calendar.events(date, date.plusDays(1)) }
    val agenda = events?.agenda(date, settings)
    val today = LocalDate.now()
    val title = when (date) {
        today -> stringResource(R.string.calendar_today)
        today.plusDays(1) -> stringResource(R.string.calendar_tomorrow)
        else -> format(date, "EEEE").replaceFirstChar { it.titlecase(LocalConfiguration.current.locales[0]) }
    }
    val count = agenda?.size ?: 0
    val meta = listOf(format(date, "EEEE"), format(date, "d MMM"), pluralStringResource(R.plurals.calendar_events, count, count))
        .joinToString(" · ")
    val forecast = (weather as? WeatherState.Ready)?.takeIf { settings.calendarWeather }?.days?.firstOrNull { it.date == date }
    BottomSheet(visible = true, onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Space.screen)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.gutter)) {
                    RoleText(title, Simplify.type.display, maxLines = 1)
                    RoleText(meta, Simplify.type.meta, color = Simplify.colors.textMuted, maxLines = 1)
                }
                IconAction(Icons.PreviousDay, { date = date.minusDays(1) })
                IconAction(Icons.NextDay, { date = date.plusDays(1) })
            }
            if (forecast != null) {
                Row(
                    Modifier.padding(top = Space.base),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.base),
                ) {
                    Icon(forecast.condition.icon, size = 18.dp, decorative = true)
                    RoleText(
                        listOfNotNull(
                            stringResource(forecast.condition.label),
                            "${degrees(forecast.high)} / ${degrees(forecast.low)}",
                            "${forecast.rainChance}%",
                            settings.weatherCity?.name,
                        ).joinToString(" · "),
                        Simplify.type.value,
                        maxLines = 1,
                    )
                }
            }
            Column(
                Modifier.fillMaxWidth().padding(vertical = Space.base).heightIn(min = 160.dp, max = 420.dp).verticalScroll(rememberScrollState()),
            ) {
                when {
                    agenda == null -> Unit
                    agenda.isEmpty() -> RoleText(
                        stringResource(R.string.calendar_nothing_planned), Simplify.type.body,
                        Modifier.padding(vertical = Space.rowGap), color = Simplify.colors.textMuted,
                    )
                    else -> agenda.forEach { event ->
                        AgendaRow(event, settings.calendarLocation, settings.calendarColours == CalendarColours.Dots) {
                            container.actions.start(calendarIntent(context, eventIntent(event)))
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = Space.base), horizontalArrangement = Arrangement.spacedBy(Space.base)) {
                SimplifyButton(
                    stringResource(R.string.agenda_open_calendar), { container.actions.start(calendarIntent(context, dayIntent(date))); onDismiss() },
                    Modifier.weight(1f).clip(Radius.control).background(Simplify.colors.surface), ButtonKind.Ghost,
                )
                SimplifyButton(
                    stringResource(R.string.agenda_new_event), { container.actions.start(calendarIntent(context, newEventIntent(date))); onDismiss() },
                    Modifier.weight(1f), ButtonKind.Primary, icon = Icons.Plus,
                )
            }
        }
    }
}

@Composable
private fun AgendaRow(event: Event, location: Boolean, dots: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().pressable(onClick, shape = Radius.control).padding(vertical = Space.base + Space.gutter),
        horizontalArrangement = Arrangement.spacedBy(Space.rowGap),
    ) {
        Column(Modifier.width(64.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (event.allDay) {
                RoleText(stringResource(R.string.calendar_all_day), Simplify.type.value, maxLines = 1)
            } else {
                RoleText(clock(event.start), Simplify.type.value, maxLines = 1)
                RoleText(clock(event.end), Simplify.type.meta, color = Simplify.colors.textMuted, maxLines = 1)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            RoleText(
                event.title.ifBlank { "—" },
                Simplify.type.body.copy(
                    fontWeight = FontWeight.Medium,
                    textDecoration = if (event.declined) TextDecoration.LineThrough else null,
                ),
                maxLines = 1,
            )
            if (location && event.location.isNotBlank()) {
                RoleText(event.location, Simplify.type.body, color = Simplify.colors.textMuted, maxLines = 1)
            }
        }
        Row(
            Modifier.padding(top = 3.dp).widthIn(max = 120.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (dots) Box(Modifier.size(6.dp).clip(CircleShape).background(Color(event.color).copy(alpha = 1f)))
            RoleText(event.calendar.uppercase(), Simplify.type.section, color = Simplify.colors.textFaint, maxLines = 1)
        }
    }
}
