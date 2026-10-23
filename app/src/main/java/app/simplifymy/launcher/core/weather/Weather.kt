package app.simplifymy.launcher.core.weather

import android.content.Context
import androidx.annotation.StringRes
import app.simplifymy.launcher.R
import app.simplifymy.launcher.core.settings.City
import app.simplifymy.launcher.core.settings.Settings
import app.simplifymy.launcher.core.settings.SettingsStore
import app.simplifymy.launcher.core.settings.TemperatureUnit
import app.simplifymy.launcher.core.settings.WindUnit
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

data class HourPoint(
    val time: LocalDateTime,
    val temperature: Int,
    val condition: Condition,
    val feels: Int = temperature,
    val rainChance: Int = 0,
    val rain: Double = 0.0,
    val wind: Int = 0,
    val windDirection: Int = 0,
    val humidity: Int = 0,
    val pressure: Double = 0.0,
    val uv: Int = 0,
    val visibility: Double = 0.0,
)

data class DayPoint(
    val date: LocalDate,
    val condition: Condition,
    val low: Int,
    val high: Int,
    val rainChance: Int = 0,
    val rain: Double = 0.0,
)

data class SunTimes(val date: LocalDate, val sunrise: LocalTime, val sunset: LocalTime)

sealed interface WeatherState {
    data object Off : WeatherState
    data object NoCity : WeatherState
    data object Loading : WeatherState
    data object Offline : WeatherState
    data class Ready(
        val now: WeatherNow,
        val current: HourPoint,
        val points: List<HourPoint>,
        val days: List<DayPoint>,
        val sun: List<SunTimes>,
        val local: LocalDateTime,
    ) : WeatherState {
        val hours: List<HourPoint> get() = points.hourly(local)
        val today: DayPoint? get() = days.firstOrNull { it.date == local.toLocalDate() }
        fun next(count: Int, step: Int = 1): List<HourPoint> = points.hourly(local, count, step)
        fun sunOn(date: LocalDate): SunTimes? = sun.firstOrNull { it.date == date }
    }
}

@Serializable
data class HourlyForecast(
    val time: List<String>,
    @SerialName("temperature_2m") val temperature: List<Double>,
    @SerialName("weather_code") val code: List<Int>,
    @SerialName("apparent_temperature") val feels: List<Double?> = emptyList(),
    @SerialName("precipitation_probability") val rainChance: List<Double?> = emptyList(),
    @SerialName("precipitation") val rain: List<Double?> = emptyList(),
    @SerialName("wind_speed_10m") val wind: List<Double?> = emptyList(),
    @SerialName("wind_direction_10m") val windDirection: List<Double?> = emptyList(),
    @SerialName("relative_humidity_2m") val humidity: List<Double?> = emptyList(),
    @SerialName("pressure_msl") val pressure: List<Double?> = emptyList(),
    @SerialName("uv_index") val uv: List<Double?> = emptyList(),
    @SerialName("visibility") val visibility: List<Double?> = emptyList(),
)

@Serializable
data class DailyForecast(
    val time: List<String> = emptyList(),
    val sunrise: List<String> = emptyList(),
    val sunset: List<String> = emptyList(),
)

@Serializable
private data class ForecastResponse(val hourly: HourlyForecast, val daily: DailyForecast = DailyForecast())

@Serializable
data class WeatherCache(
    val cityKey: String,
    val unit: TemperatureUnit,
    val fetchedAt: Long,
    val hourly: HourlyForecast,
    val daily: DailyForecast = DailyForecast(),
    val wind: WindUnit = WindUnit.Kmh,
)

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
private const val HOUR_MS = 60 * 60 * 1000L
private const val RETRY_MS = 15 * 60 * 1000L
private const val TICK_MS = 60 * 1000L
private const val DAYS = 6
private const val DAY_START = 9
private const val DAY_END = 21
private const val HOURLY = "temperature_2m,apparent_temperature,weather_code,precipitation_probability,precipitation," +
    "wind_speed_10m,wind_direction_10m,relative_humidity_2m,pressure_msl,uv_index,visibility"
private const val MM_PER_INCH = 25.4
private const val METRES_PER_MILE = 1609.344

private fun parse(body: String): ForecastResponse = json.decodeFromString(ForecastResponse.serializer(), body)

fun parseForecast(body: String): HourlyForecast = parse(body).hourly

fun parseDaily(body: String): DailyForecast = parse(body).daily

private fun List<Double?>.number(i: Int): Double = getOrNull(i) ?: 0.0

private fun gmt(time: String, zone: ZoneId): LocalDateTime =
    LocalDateTime.parse(time).atOffset(ZoneOffset.UTC).atZoneSameInstant(zone).toLocalDateTime()

fun WeatherCache.points(zone: ZoneId): List<HourPoint> = hourly.time.indices.map { i ->
    val temperature = hourly.temperature[i].roundToInt()
    HourPoint(
        time = gmt(hourly.time[i], zone),
        temperature = temperature,
        condition = Condition.of(hourly.code[i]),
        feels = hourly.feels.getOrNull(i)?.roundToInt() ?: temperature,
        rainChance = hourly.rainChance.number(i).roundToInt(),
        rain = hourly.rain.number(i),
        wind = hourly.wind.number(i).roundToInt(),
        windDirection = hourly.windDirection.number(i).roundToInt(),
        humidity = hourly.humidity.number(i).roundToInt(),
        pressure = hourly.pressure.number(i),
        uv = hourly.uv.number(i).roundToInt(),
        visibility = hourly.visibility.number(i),
    )
}

fun WeatherCache.sun(zone: ZoneId): List<SunTimes> = daily.sunrise.indices.mapNotNull { i ->
    val rise = daily.sunrise.getOrNull(i)?.let { runCatching { gmt(it, zone) }.getOrNull() } ?: return@mapNotNull null
    val set = daily.sunset.getOrNull(i)?.let { runCatching { gmt(it, zone) }.getOrNull() } ?: return@mapNotNull null
    SunTimes(rise.toLocalDate(), rise.toLocalTime(), set.toLocalTime())
}

fun List<HourPoint>.hourly(from: LocalDateTime, count: Int = 6, step: Int = 1): List<HourPoint> =
    filter { it.time.isAfter(from) && it.time.hour % step == 0 }.take(count)

fun List<HourPoint>.daily(from: LocalDate, count: Int = DAYS): List<DayPoint> =
    filter { !it.time.toLocalDate().isBefore(from) }
        .groupBy { it.time.toLocalDate() }
        .entries.take(count)
        .map { (date, hours) ->
            val daytime = hours.filter { it.time.hour in DAY_START until DAY_END }.ifEmpty { hours }
            DayPoint(
                date, dominant(daytime.map { it.condition }), hours.minOf { it.temperature }, hours.maxOf { it.temperature },
                hours.maxOf { it.rainChance }, hours.sumOf { it.rain },
            )
        }

fun List<HourPoint>.slots(date: LocalDate, hours: Int = 3): List<HourPoint> =
    filter { it.time.toLocalDate() == date }
        .groupBy { it.time.hour / hours }
        .map { (slot, points) ->
            points.first().copy(
                time = date.atTime(slot * hours, 0),
                rain = points.sumOf { it.rain },
                rainChance = points.maxOf { it.rainChance },
            )
        }

fun dominant(conditions: List<Condition>): Condition =
    conditions.groupingBy { it }.eachCount().entries.maxWith(compareBy({ it.value }, { it.key.ordinal })).key

private fun WeatherCache.index(instant: Instant): Int {
    val hour = LocalDateTime.ofInstant(instant.truncatedTo(ChronoUnit.HOURS), ZoneOffset.UTC).toString().take(16)
    return hourly.time.indexOf(hour)
}

fun WeatherCache.at(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): WeatherNow? {
    val index = index(instant).takeIf { it >= 0 } ?: return null
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
    val current = points[index(instant)]
    return WeatherState.Ready(now, current, points, points.daily(local.toLocalDate()), sun(zone), local)
}

fun compassPoint(degrees: Int): Int = Math.floorMod((degrees / 45.0).roundToInt(), 8)

fun rainText(mm: Double, imperial: Boolean): String =
    if (imperial) String.format(Locale.ROOT, "%.2f in", mm / MM_PER_INCH) else String.format(Locale.ROOT, "%.1f mm", mm)

fun visibilityText(metres: Double, imperial: Boolean): String =
    if (imperial) "${(metres / METRES_PER_MILE).roundToInt()} mi" else "${(metres / 1000).roundToInt()} km"

private fun WeatherCache.matches(settings: Settings) = unit == settings.temperatureUnit && wind == settings.windUnit

class WeatherRepository(context: Context, private val settings: SettingsStore, scope: CoroutineScope) {
    private val dir = File(context.filesDir, "weather")
    private val legacy = File(context.filesDir, "weather.json")
    private val caches = MutableStateFlow<Map<String, WeatherCache>>(emptyMap())
    private val failed = MutableStateFlow<Map<String, Long>>(emptyMap())
    private val clock = MutableStateFlow(Instant.now())
    private val lock = Mutex()

    val state: StateFlow<WeatherState> = combine(settings.settings, caches, failed, clock) { s, c, f, now ->
        val city = s.weatherCity
        when {
            !s.weather -> WeatherState.Off
            city == null -> WeatherState.NoCity
            else -> stateOf(city, s, c, f, now)
        }
    }.stateIn(scope, SharingStarted.Eagerly, WeatherState.Off)

    init {
        scope.launch {
            while (true) {
                clock.value = Instant.now()
                delay(TICK_MS)
            }
        }
        scope.launch {
            settings.settings.map { s -> listOf(s.weather, s.weatherCity, s.temperatureUnit, s.windUnit, s.weatherRefresh) }
                .distinctUntilChanged()
                .collectLatest {
                    val s = settings.settings.value
                    val city = s.weatherCity
                    if (s.weather && city != null) {
                        while (true) {
                            refresh(city)
                            delay(TICK_MS)
                        }
                    }
                }
        }
    }

    fun state(city: City): Flow<WeatherState> =
        combine(settings.settings, caches, failed, clock) { s, c, f, now -> stateOf(city, s, c, f, now) }

    fun lastFetch(city: City): Instant? = caches.value[city.key]?.let { Instant.ofEpochMilli(it.fetchedAt) }

    private fun stateOf(city: City, s: Settings, c: Map<String, WeatherCache>, f: Map<String, Long>, now: Instant): WeatherState {
        val cache = c[city.key]?.takeIf { it.matches(s) }
        return when {
            cache != null -> cache.ready(now, city.zone ?: ZoneId.systemDefault()) ?: WeatherState.Offline
            city.key in f -> WeatherState.Offline
            else -> WeatherState.Loading
        }
    }

    suspend fun refresh(city: City, force: Boolean = false) = lock.withLock {
        val s = settings.settings.value
        val now = System.currentTimeMillis()
        val cache = load(city.key)?.takeIf { it.matches(s) }
        if (!force && cache != null && now < cache.fetchedAt + s.weatherRefresh * HOUR_MS) return@withLock
        if (!force && now < (failed.value[city.key] ?: 0L) + RETRY_MS) return@withLock
        val fresh = fetch(city, s)
        if (fresh != null) {
            save(fresh)
            failed.update { it - city.key }
        } else {
            failed.update { it + (city.key to now) }
        }
    }

    suspend fun forget(city: City) = lock.withLock {
        caches.update { it - city.key }
        failed.update { it - city.key }
        withContext(Dispatchers.IO) { runCatching { file(city.key).delete() } }
    }

    private fun file(key: String) = File(dir, "$key.json")

    private suspend fun load(key: String): WeatherCache? {
        caches.value[key]?.let { return it }
        val cache = withContext(Dispatchers.IO) {
            if (legacy.exists()) {
                dir.mkdirs()
                runCatching { json.decodeFromString(WeatherCache.serializer(), legacy.readText()) }.getOrNull()
                    ?.let { old -> if (!file(old.cityKey).exists()) legacy.copyTo(file(old.cityKey)) }
                legacy.delete()
            }
            runCatching { json.decodeFromString(WeatherCache.serializer(), file(key).readText()) }.getOrNull()
        } ?: return null
        caches.update { it + (key to cache) }
        return cache
    }

    private suspend fun save(cache: WeatherCache) {
        caches.update { it + (cache.cityKey to cache) }
        withContext(Dispatchers.IO) {
            runCatching {
                dir.mkdirs()
                file(cache.cityKey).writeText(json.encodeToString(WeatherCache.serializer(), cache))
            }
        }
    }

    private suspend fun fetch(city: City, s: Settings): WeatherCache? = withContext(Dispatchers.IO) {
        runCatching {
            val body = get(
                String.format(
                    Locale.ROOT,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f&hourly=%s&daily=sunrise,sunset" +
                        "&forecast_days=%d&timezone=GMT&temperature_unit=%s&wind_speed_unit=%s",
                    city.latitude, city.longitude, HOURLY, DAYS, s.temperatureUnit.query, s.windUnit.query,
                )
            )
            val response = parse(body)
            WeatherCache(city.key, s.temperatureUnit, System.currentTimeMillis(), response.hourly, response.daily, s.windUnit)
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
                City(
                    it.name, listOf(it.country, it.admin1).filter(String::isNotBlank).distinct().joinToString(" · "),
                    it.latitude, it.longitude, it.timezone, it.country,
                )
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
