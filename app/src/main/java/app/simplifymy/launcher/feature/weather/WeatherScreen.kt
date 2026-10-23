package app.simplifymy.launcher.feature.weather

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.simplifymy.launcher.R
import app.simplifymy.launcher.container
import app.simplifymy.launcher.core.settings.City
import app.simplifymy.launcher.core.settings.Settings
import app.simplifymy.launcher.core.weather.WeatherState
import app.simplifymy.launcher.core.weather.compassPoint
import app.simplifymy.launcher.ui.launcher.zeros
import app.simplifymy.launcher.ui.theme.PlexMono
import app.simplifymy.launcher.ui.theme.RoleText
import app.simplifymy.launcher.ui.theme.Simplify
import app.simplifymy.launcher.ui.theme.Space
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAccessor
import java.util.Locale
import kotlinx.coroutines.launch

enum class WeatherView { Today, Cities }

private const val TODAY = "today"
private const val CITIES = "cities"
private const val ADD = "add"
private const val DAY = "day:"

@Composable
fun WeatherScreen(initial: WeatherView, onExit: () -> Unit) {
    val stack = rememberSaveable(saver = listSaver({ it.toList() }, { it.toMutableStateList() })) {
        mutableStateListOf(if (initial == WeatherView.Cities) CITIES else TODAY)
    }
    val back: () -> Unit = { if (stack.size > 1) stack.removeAt(stack.lastIndex) else onExit() }
    BackHandler(stack.size > 1) { back() }
    val top = stack.last()
    Box(Modifier.fillMaxSize().background(Simplify.colors.background)) {
        when {
            top == TODAY -> TodayView(
                openCities = { stack.add(CITIES) },
                openDay = { stack.add(DAY + it) },
                addCity = { stack.add(ADD) },
            )
            top == CITIES -> CitiesView(
                onAdd = { stack.add(ADD) },
                onPick = { if (stack.size > 1) back() else stack[0] = TODAY },
            )
            top == ADD -> AddCityView(back)
            top.startsWith(DAY) -> DayView(LocalDate.parse(top.removePrefix(DAY)), back)
        }
    }
}

@Composable
internal fun weatherSettings(): Pair<Settings, ((Settings) -> Settings) -> Unit> {
    val container = LocalContext.current.container
    val current by container.settings.settings.collectAsState()
    val scope = rememberCoroutineScope()
    return current to { edit -> scope.launch { container.settings.update(edit) } }
}

@Composable
internal fun cityState(city: City): WeatherState {
    val weather = LocalContext.current.container.weather
    val state by remember(city.key) { weather.state(city) }.collectAsState(WeatherState.Loading)
    LaunchedEffect(city.key) { weather.refresh(city) }
    return state
}

@Composable
internal fun locale(): Locale = LocalConfiguration.current.locales[0]

@Composable
internal fun format(time: TemporalAccessor, pattern: String): String = DateTimeFormatter.ofPattern(pattern, locale()).format(time)

@Composable
private fun is24Hour(): Boolean = DateFormat.is24HourFormat(LocalContext.current)

@Composable
internal fun clock(time: TemporalAccessor): String = format(time, if (is24Hour()) "HH:mm" else "h:mm a")

@Composable
internal fun hour(time: TemporalAccessor): String = format(time, if (is24Hour()) "HH:mm" else "h a")

@Composable
internal fun capitalized(time: TemporalAccessor, pattern: String): String {
    val locale = locale()
    return format(time, pattern).replaceFirstChar { it.titlecase(locale) }
}

private val COMPASS = listOf(
    R.string.wind_n, R.string.wind_ne, R.string.wind_e, R.string.wind_se,
    R.string.wind_s, R.string.wind_sw, R.string.wind_w, R.string.wind_nw,
)

@Composable
internal fun compass(degrees: Int): String = stringResource(COMPASS[compassPoint(degrees)])

@Composable
internal fun SectionLine(@StringRes title: Int, modifier: Modifier = Modifier, note: String? = null) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = Space.screen, vertical = Space.base),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoleText(stringResource(title).uppercase(locale()), Simplify.type.section, Modifier.weight(1f), color = Simplify.colors.textMuted)
        note?.let { RoleText(it, Simplify.type.meta, color = Simplify.colors.textFaint, maxLines = 1) }
    }
}

private const val ADVANCE = 0.6f

internal fun numeralStyle(size: TextUnit, color: Color) = TextStyle(
    fontFamily = PlexMono,
    fontSize = size,
    letterSpacing = (-0.04).em,
    lineHeight = 0.9.em,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
    fontFeatureSettings = "tnum",
    color = color,
)

@Composable
internal fun Numerals(text: String, size: TextUnit, modifier: Modifier = Modifier, color: Color = Simplify.colors.text) {
    Text(zeros(text, Simplify.colors.accent, color.copy(alpha = 0.4f)), modifier, style = numeralStyle(size, color), maxLines = 1, softWrap = false)
}

@Composable
internal fun FitNumerals(text: String, cap: TextUnit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val byWidth = with(LocalDensity.current) { (maxWidth / (text.length * ADVANCE)).coerceAtLeast(1.dp).toSp() }
        Numerals(text, if (byWidth.value < cap.value) byWidth else cap)
    }
}
