package app.simplifymy.launcher.feature.weather

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.simplifymy.launcher.R
import app.simplifymy.launcher.core.settings.Settings
import app.simplifymy.launcher.core.weather.HourPoint
import app.simplifymy.launcher.core.weather.WeatherState
import app.simplifymy.launcher.core.weather.rainText
import app.simplifymy.launcher.core.weather.slots
import app.simplifymy.launcher.ui.component.DetailBar
import app.simplifymy.launcher.ui.component.Hairline
import app.simplifymy.launcher.ui.component.Icon
import app.simplifymy.launcher.ui.component.Segmented
import app.simplifymy.launcher.ui.component.icon
import app.simplifymy.launcher.ui.launcher.degrees
import app.simplifymy.launcher.ui.theme.RoleText
import app.simplifymy.launcher.ui.theme.Simplify
import app.simplifymy.launcher.ui.theme.Space
import java.time.LocalDate
import java.util.Locale

private enum class DayTab(@StringRes val label: Int) {
    Temperature(R.string.weather_temperature),
    Rain(R.string.weather_tab_rain),
    Wind(R.string.weather_wind),
}

private val CHART = 132.dp
private val BAR = 92.dp
private const val RAINY_MM = 0.1

@Composable
internal fun DayView(date: LocalDate, onBack: () -> Unit) {
    val (s, _) = weatherSettings()
    val city = s.weatherCity
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        DetailBar(capitalized(date, "EEEE"), onBack, meta = listOfNotNull(format(date, "d MMM"), city?.name).joinToString(" · "))
        val weather = city?.let { cityState(it) } as? WeatherState.Ready ?: return@Column
        val slots = remember(weather, date) { weather.points.slots(date) }
        val day = weather.days.firstOrNull { it.date == date } ?: return@Column
        val today = date == weather.local.toLocalDate()
        val rows = if (today) slots.filter { it.time.hour + 3 > weather.local.hour } else slots
        val imperial = s.temperatureUnit.imperial
        var tab by rememberSaveable { mutableStateOf(DayTab.Temperature) }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Row(
                Modifier.fillMaxWidth().padding(start = Space.screen, end = Space.screen, top = 18.dp, bottom = Space.section),
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.base)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(day.condition.icon, size = 26.dp, decorative = true)
                        RoleText(stringResource(day.condition.label), Simplify.type.title, maxLines = 1)
                    }
                    val from = weather.points.firstOrNull { it.time.toLocalDate() == date && it.rain >= RAINY_MM }
                    RoleText(
                        listOfNotNull(
                            "${day.rainChance}%",
                            rainText(day.rain, imperial),
                            from?.let { stringResource(R.string.weather_from, clock(it.time)) },
                        ).joinToString(" · "),
                        Simplify.type.value, color = Simplify.colors.textMuted,
                    )
                }
                Row {
                    Numerals(degrees(day.high), 56.sp, Modifier.alignByBaseline())
                    RoleText(" " + degrees(day.low), numeralStyle(28.sp, Simplify.colors.textMuted), Modifier.alignByBaseline(), maxLines = 1)
                }
            }
            Segmented(
                DayTab.entries, tab, { tab = it },
                Modifier.padding(start = Space.screen, end = Space.screen, bottom = 14.dp).fillMaxWidth(),
            ) { stringResource(it.label) }
            BarChart(slots, tab, imperial)
            SectionLine(R.string.weather_every_3, modifier = Modifier.padding(top = Space.rowGap))
            Table(rows, s)
            Spacer(Modifier.height(Space.section))
        }
    }
}

private fun DayTab.value(slot: HourPoint, imperial: Boolean): Double = when (this) {
    DayTab.Temperature -> slot.temperature.toDouble()
    DayTab.Rain -> if (imperial) slot.rain / 25.4 else slot.rain
    DayTab.Wind -> slot.wind.toDouble()
}

private fun DayTab.label(value: Double, imperial: Boolean): String = when (this) {
    DayTab.Temperature -> degrees(value.toInt())
    DayTab.Rain -> String.format(Locale.ROOT, if (imperial) "%.2f" else "%.1f", value)
    DayTab.Wind -> value.toInt().toString()
}

@Composable
private fun BarChart(slots: List<HourPoint>, tab: DayTab, imperial: Boolean) {
    if (slots.isEmpty()) return
    val values = slots.map { tab.value(it, imperial) }
    val floor = if (tab == DayTab.Temperature) minOf(0.0, values.min() - 1) else 0.0
    val top = values.max()
    val peak = values.indexOf(top).takeIf { top > floor }
    Column(Modifier.padding(horizontal = Space.screen)) {
        Row(Modifier.fillMaxWidth().height(CHART), horizontalArrangement = Arrangement.spacedBy(Space.base), verticalAlignment = Alignment.Bottom) {
            values.forEachIndexed { i, value ->
                val height = if (top > floor) BAR * ((value - floor) / (top - floor)).toFloat() else 0.dp
                Column(
                    Modifier.weight(1f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Bottom),
                ) {
                    RoleText(tab.label(value, imperial), Simplify.type.meta, color = Simplify.colors.textMuted, maxLines = 1)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(maxOf(height, 3.dp))
                            .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                            .background(if (i == peak) Simplify.colors.accent else Simplify.colors.surfaceAlt)
                    )
                }
            }
        }
        Hairline()
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(Space.base)) {
            slots.forEach {
                RoleText(format(it.time, "HH"), Simplify.type.meta, Modifier.weight(1f), color = Simplify.colors.textFaint, textAlign = TextAlign.Center)
            }
        }
    }
}

private val TIME = 58.dp
private val ICON = 28.dp

@Composable
private fun Table(rows: List<HourPoint>, s: Settings) {
    val imperial = s.temperatureUnit.imperial
    Column(Modifier.padding(horizontal = Space.screen)) {
        Row(Modifier.padding(vertical = Space.base)) {
            Heading(R.string.weather_col_time, Modifier.width(TIME + ICON), TextAlign.Start)
            Heading(R.string.weather_col_temp, Modifier.weight(1f))
            Heading(R.string.weather_col_rain, Modifier.weight(1f))
            Heading(R.string.weather_col_wind, Modifier.weight(1f))
        }
        rows.forEach { slot ->
            Row(Modifier.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                RoleText(hour(slot.time), Simplify.type.value, Modifier.width(TIME), color = Simplify.colors.textMuted, maxLines = 1)
                Box(Modifier.width(ICON)) { Icon(slot.condition.icon, size = 20.dp, label = stringResource(slot.condition.label)) }
                Cell(degrees(slot.temperature), Modifier.weight(1f))
                Cell(rainText(slot.rain, imperial), Modifier.weight(1f), muted = true)
                Cell("${slot.wind} ${s.windUnit.label}", Modifier.weight(1f), muted = true)
            }
        }
    }
}

@Composable
private fun Heading(@StringRes text: Int, modifier: Modifier, align: TextAlign = TextAlign.End) =
    RoleText(stringResource(text), Simplify.type.meta, modifier, color = Simplify.colors.textFaint, maxLines = 1, textAlign = align)

@Composable
private fun Cell(text: String, modifier: Modifier, muted: Boolean = false) =
    RoleText(text, Simplify.type.value, modifier, color = if (muted) Simplify.colors.textMuted else Simplify.colors.text, maxLines = 1, textAlign = TextAlign.End)
