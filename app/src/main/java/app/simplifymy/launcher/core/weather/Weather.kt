package app.simplifymy.launcher.core.weather

import android.content.Context
import androidx.annotation.StringRes
import app.simplifymy.launcher.R
import app.simplifymy.launcher.core.settings.City
import app.simplifymy.launcher.core.settings.SettingsStore
import app.simplifymy.launcher.core.settings.TemperatureUnit
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class Condition(@StringRes val label: Int) {
    Clear(R.string.weather_clear),
    Cloudy(R.string.weather_cloudy),
    Fog(R.string.weather_fog),
    Drizzle(R.string.weather_drizzle),
    Rain(R.string.weather_rain),
    Snow(R.string.weather_snow),
    Storm(R.string.weather_storm),
    ;

    companion object {
        fun of(wmo: Int): Condition = when (wmo) {
            0 -> Clear
            in 1..3 -> Cloudy
            45, 48 -> Fog
            in 51..57 -> Drizzle
            in 61..67, in 80..82 -> Rain
            in 71..77, 85, 86 -> Snow
            in 95..99 -> Storm
            else -> Cloudy
        }
    }
}

data class WeatherNow(val temperature: Int, val condition: Condition, val updated: Instant, val high: Int, val low: Int)

data class HourPoint(val time: LocalDateTime, val temperature: Int, val condition: Condition)

data class DayPoint(val date: LocalDate, val condition: Condition, val low: Int, val high: Int)

sealed interface WeatherState {
    data object Off : WeatherState
    data object NoCity : WeatherState
    data object Offline : WeatherState
    data class Ready(val now: WeatherNow, val hours: List<HourPoint>, val days: List<DayPoint>) : WeatherState
}

@Serializable
data class HourlyForecast(
    val time: List<String>,
    @SerialName("temperature_2m") val temperature: List<Double>,
    @SerialName("weather_code") val code: List<Int>,
)

@Serializable
private data class ForecastResponse(val hourly: HourlyForecast)

@Serializable
data class WeatherCache(val cityKey: String, val unit: TemperatureUnit, val fetchedAt: Long, val hourly: HourlyForecast)

@Serializable
private data class GeocodingResponse(val results: List<GeocodingResult> = emptyList())

@Serializable
private data class GeocodingResult(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String = "",
    val admin1: String = "",
    val timezone: String = "",
)

private val json = Json { ignoreUnknownKeys = true }
private const val UPDATE_MS = 2 * 60 * 60 * 1000L
private const val RETRY_MS = 15 * 60 * 1000L
private const val TICK_MS = 60 * 1000L
private const val DAYS = 6

private fun City.key() = String.format(Locale.ROOT, "%.2f,%.2f", latitude, longitude)

fun parseForecast(body: String): HourlyForecast = json.decodeFromString(ForecastResponse.serializer(), body).hourly

private const val DAY_START = 9
private const val DAY_END = 21

fun WeatherCache.points(zone: ZoneId): List<HourPoint> = hourly.time.indices.map { i ->
    HourPoint(
        LocalDateTime.parse(hourly.time[i]).atOffset(ZoneOffset.UTC).atZoneSameInstant(zone).toLocalDateTime(),
        hourly.temperature[i].roundToInt(),
        Condition.of(hourly.code[i]),
    )
}

fun List<HourPoint>.hourly(from: LocalDateTime, count: Int = 6): List<HourPoint> = filter { it.time.isAfter(from) }.take(count)

fun List<HourPoint>.daily(from: LocalDate, count: Int = 5): List<DayPoint> =
    filter { !it.time.toLocalDate().isBefore(from) }
        .groupBy { it.time.toLocalDate() }
        .entries.take(count)
        .map { (date, hours) ->
            val daytime = hours.filter { it.time.hour in DAY_START until DAY_END }.ifEmpty { hours }
            DayPoint(date, dominant(daytime.map { it.condition }), hours.minOf { it.temperature }, hours.maxOf { it.temperature })
        }

fun dominant(conditions: List<Condition>): Condition =
    conditions.groupingBy { it }.eachCount().entries.maxWith(compareBy({ it.value }, { it.key.ordinal })).key

fun WeatherCache.at(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): WeatherNow? {
    val hour = LocalDateTime.ofInstant(instant.truncatedTo(ChronoUnit.HOURS), ZoneOffset.UTC).toString().take(16)
    val index = hourly.time.indexOf(hour).takeIf { it >= 0 } ?: return null
    val today = instant.atZone(zone).toLocalDate()
    val temperatures = points(zone).filter { it.time.toLocalDate() == today }.map { it.temperature }
    val temperature = hourly.temperature[index].roundToInt()
    return WeatherNow(
        temperature, Condition.of(hourly.code[index]), Instant.ofEpochMilli(fetchedAt),
        temperatures.maxOrNull() ?: temperature, temperatures.minOrNull() ?: temperature,
    )
}

fun WeatherCache.ready(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): WeatherState.Ready? {
    val now = at(instant, zone) ?: return null
    val points = points(zone)
    val local = LocalDateTime.ofInstant(instant, zone)
    return WeatherState.Ready(now, points.hourly(local), points.daily(local.toLocalDate()))
}

class WeatherRepository(context: Context, settings: SettingsStore, scope: CoroutineScope) {
    private val file = File(context.filesDir, "weather.json")
    private val _state = MutableStateFlow<WeatherState>(WeatherState.Off)
    val state: StateFlow<WeatherState> = _state

    init {
        scope.launch {
            settings.settings.map { Triple(it.weather, it.weatherCity, it.temperatureUnit) }.distinctUntilChanged()
                .collectLatest { (on, city, unit) ->
                    when {
                        !on -> _state.value = WeatherState.Off
                        city == null -> _state.value = WeatherState.NoCity
                        else -> keepFresh(city, unit)
                    }
                }
        }
    }

    private suspend fun keepFresh(city: City, unit: TemperatureUnit) {
        var cache = readCache()?.takeIf { it.cityKey == city.key() && it.unit == unit }
        var nextFetch = cache?.let { it.fetchedAt + UPDATE_MS } ?: 0L
        while (true) {
            if (System.currentTimeMillis() >= nextFetch) {
                val fresh = fetch(city, unit)
                if (fresh != null) {
                    cache = fresh
                    writeCache(fresh)
                    nextFetch = fresh.fetchedAt + UPDATE_MS
                } else {
                    nextFetch = System.currentTimeMillis() + RETRY_MS
                }
            }
            _state.value = cache?.ready(Instant.now()) ?: WeatherState.Offline
            delay(TICK_MS)
        }
    }

    private suspend fun readCache(): WeatherCache? = withContext(Dispatchers.IO) {
        runCatching { json.decodeFromString(WeatherCache.serializer(), file.readText()) }.getOrNull()
    }

    private suspend fun writeCache(cache: WeatherCache) = withContext(Dispatchers.IO) {
        runCatching { file.writeText(json.encodeToString(WeatherCache.serializer(), cache)) }
    }

    private suspend fun fetch(city: City, unit: TemperatureUnit): WeatherCache? = withContext(Dispatchers.IO) {
        runCatching {
            val body = get(
                String.format(
                    Locale.ROOT,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f&hourly=temperature_2m,weather_code&forecast_days=%d&timezone=GMT&temperature_unit=%s",
                    city.latitude, city.longitude, DAYS, unit.query,
                )
            )
            WeatherCache(city.key(), unit, System.currentTimeMillis(), parseForecast(body))
        }.getOrNull()
    }

    suspend fun searchCities(query: String, language: String): List<City> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        runCatching {
            val body = get(
                "https://geocoding-api.open-meteo.com/v1/search?count=8&language=$language&name=" +
                    URLEncoder.encode(query.trim(), "UTF-8")
            )
            json.decodeFromString(GeocodingResponse.serializer(), body).results.map {
                City(it.name, listOf(it.admin1, it.country).filter(String::isNotBlank).joinToString(", "), it.latitude, it.longitude, it.timezone)
            }
        }.getOrDefault(emptyList())
    }

    private fun get(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
        }
        return connection.inputStream.use { it.readBytes().decodeToString() }
    }
}
