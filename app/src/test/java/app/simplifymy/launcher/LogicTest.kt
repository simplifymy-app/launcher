package app.simplifymy.launcher

import app.simplifymy.launcher.core.actions.Action
import app.simplifymy.launcher.core.search.Calculator
import app.simplifymy.launcher.core.search.SearchEngine
import app.simplifymy.launcher.core.settings.Settings
import app.simplifymy.launcher.core.settings.SettingsJson
import app.simplifymy.launcher.core.settings.City
import app.simplifymy.launcher.core.settings.MAX_CITIES
import app.simplifymy.launcher.core.settings.PressureUnit
import app.simplifymy.launcher.core.settings.StoredSettings
import app.simplifymy.launcher.core.settings.TemperatureUnit
import app.simplifymy.launcher.core.settings.WindUnit
import app.simplifymy.launcher.core.weather.DailyForecast
import app.simplifymy.launcher.core.weather.SunTimes
import app.simplifymy.launcher.core.weather.compassPoint
import app.simplifymy.launcher.core.weather.parseDaily
import app.simplifymy.launcher.core.weather.rainText
import app.simplifymy.launcher.core.weather.ready
import app.simplifymy.launcher.core.weather.slots
import app.simplifymy.launcher.core.weather.sun
import app.simplifymy.launcher.core.weather.visibilityText
import app.simplifymy.launcher.core.weather.Condition
import app.simplifymy.launcher.core.weather.HourPoint
import app.simplifymy.launcher.core.weather.WeatherCache
import app.simplifymy.launcher.core.weather.daily
import app.simplifymy.launcher.core.weather.dominant
import app.simplifymy.launcher.core.weather.hourly
import app.simplifymy.launcher.core.weather.points
import app.simplifymy.launcher.core.weather.at
import app.simplifymy.launcher.core.weather.parseForecast
import app.simplifymy.launcher.core.workspace.ItemEntity
import app.simplifymy.launcher.core.workspace.ItemType
import app.simplifymy.launcher.core.workspace.Occupancy
import androidx.compose.ui.graphics.Color
import app.simplifymy.launcher.feature.home.BuiltinWidget
import app.simplifymy.launcher.feature.home.offsetLabel
import app.simplifymy.launcher.feature.weather.coordinates
import app.simplifymy.launcher.feature.weather.matched
import app.simplifymy.launcher.ui.launcher.zeros
import app.simplifymy.launcher.util.AppIcon
import app.simplifymy.launcher.util.IconTarget
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculatorTest {
    @Test fun precedence() = assertEquals("85", Calculator.evaluate("12*7+1"))
    @Test fun parentheses() = assertEquals("98", Calculator.evaluate("(12+2)*7"))
    @Test fun decimals() = assertEquals("0.5", Calculator.evaluate("1/2"))
    @Test fun unicodeOperators() = assertEquals("6", Calculator.evaluate("2×3"))
    @Test fun plainNumberIsNotACalculation() = assertNull(Calculator.evaluate("42"))
    @Test fun wordsAreNotACalculation() = assertNull(Calculator.evaluate("maps"))
    @Test fun brokenInput() = assertNull(Calculator.evaluate("2+*"))
}

class SearchScoreTest {
    @Test fun prefixBeatsSubstring() =
        assertTrue(SearchEngine.score("Camera", "cam") > SearchEngine.score("Webcam", "cam"))

    @Test fun wordStart() = assertEquals(80, SearchEngine.score("Google Maps", "maps"))
    @Test fun initials() = assertEquals(50, SearchEngine.score("YouTube Music", "ym"))
    @Test fun accentsFold() = assertEquals(100, SearchEngine.score("Café", "cafe"))
    @Test fun noMatch() = assertEquals(0, SearchEngine.score("Phone", "xyz"))
}

class OccupancyTest {
    private fun app(x: Int, y: Int, sx: Int = 1, sy: Int = 1) =
        ItemEntity(container = -100, x = x, y = y, spanX = sx, spanY = sy, type = ItemType.APP)

    @Test fun widgetBlocksItsCells() {
        val grid = Occupancy.of(listOf(app(0, 0, 2, 2)), 4, 4)
        assertFalse(grid.fits(1, 1, 1, 1))
        assertTrue(grid.fits(2, 0, 2, 2))
    }

    @Test fun firstFreeSkipsUsed() =
        assertEquals(1 to 0, Occupancy.of(listOf(app(0, 0)), 4, 4).firstFree())

    @Test fun nearestIsAdjacentToTheTakenCell() {
        val spot = Occupancy.of(listOf(app(3, 2)), 4, 4).nearest(3, 2, 1, 1)!!
        assertEquals(1, Math.abs(spot.first - 3) + Math.abs(spot.second - 2))
    }

    @Test fun outOfBoundsNeverFits() = assertFalse(Occupancy(4, 4).fits(3, 3, 2, 1))
}

class SettingsJsonTest {
    @Test fun roundTripKeepsActions() {
        val settings = Settings(
            desktopColumns = 6,
            swipeDown = Action.OpenApp("com.example/.Main", 0),
            doubleTap = Action.LockScreen,
        )
        val decoded = SettingsJson.decodeFromString(Settings.serializer(), SettingsJson.encodeToString(Settings.serializer(), settings))
        assertEquals(settings, decoded)
    }

    @Test fun oneCityBecomesAListOfOne() {
        val decoded = SettingsJson.decodeFromString(
            StoredSettings,
            """{"weather":true,"weatherCity":{"name":"Kyiv","region":"Kyiv, Ukraine","latitude":50.45,"longitude":30.52,"timezone":"Europe/Kyiv"}}""",
        )
        assertEquals(listOf("Kyiv"), decoded.weatherCities.map { it.name })
        assertEquals("Kyiv", decoded.weatherCity?.name)
        assertEquals("Ukraine", decoded.weatherCity?.countryName)
    }

    @Test fun noCityStaysEmpty() =
        assertTrue(SettingsJson.decodeFromString(StoredSettings, """{"weatherCity":null}""").weatherCities.isEmpty())

    @Test fun migratedSettingsRoundTrip() {
        val settings = Settings(weatherCities = listOf(kyiv, lviv), currentCity = 1, windUnit = WindUnit.Mph, weatherRefresh = 6)
        assertEquals(settings, SettingsJson.decodeFromString(StoredSettings, SettingsJson.encodeToString(StoredSettings, settings)))
    }

    @Test fun unknownKeysFromNewerVersionsAreIgnored() {
        val decoded = SettingsJson.decodeFromString(Settings.serializer(), """{"desktopColumns":7,"somethingNew":true}""")
        assertEquals(7, decoded.desktopColumns)
    }
}

private val kyiv = City("Kyiv", "Ukraine", 50.45, 30.52, "Europe/Kyiv", "Ukraine")
private val lviv = City("Lviv", "Ukraine", 49.84, 24.03, "Europe/Kyiv", "Ukraine")
private val lisbon = City("Lisbon", "Portugal", 38.72, -9.14, "Europe/Lisbon", "Portugal")

class CitiesTest {
    @Test fun theFirstCityIsCurrentAndLaterOnesAreNot() {
        val settings = Settings().addCity(kyiv).addCity(lviv)
        assertEquals(listOf(kyiv, lviv), settings.weatherCities)
        assertEquals(kyiv, settings.weatherCity)
    }

    @Test fun addingTwiceChangesNothing() {
        val settings = Settings().addCity(kyiv).addCity(lviv).selectCity(lviv)
        assertEquals(settings, settings.addCity(kyiv))
    }

    @Test fun stopsAtTen() {
        val full = (0 until 12).fold(Settings()) { s, i -> s.addCity(City("C$i", "", i.toDouble(), 0.0)) }
        assertEquals(MAX_CITIES, full.weatherCities.size)
    }

    @Test fun removingKeepsTheCurrentCity() {
        val settings = Settings().addCity(kyiv).addCity(lviv).addCity(lisbon).selectCity(lviv).removeCity(kyiv)
        assertEquals(lviv, settings.weatherCity)
        assertEquals(listOf(lviv, lisbon), settings.weatherCities)
        assertEquals(lisbon, settings.removeCity(lviv).weatherCity)
    }

    @Test fun movingKeepsTheCurrentCity() {
        val settings = Settings().addCity(kyiv).addCity(lviv).addCity(lisbon).selectCity(lisbon).moveCity(lisbon, -2)
        assertEquals(listOf(lisbon, kyiv, lviv), settings.weatherCities)
        assertEquals(lisbon, settings.weatherCity)
        assertEquals(settings, settings.moveCity(lisbon, -1))
    }
}

class UnitsTest {
    @Test fun pressureInMillimetres() {
        assertEquals(1016, PressureUnit.Hpa.of(1016.2))
        assertEquals(762, PressureUnit.Mmhg.of(1016.0))
    }

    @Test fun windUnitsGoInTheRequest() =
        assertEquals(listOf("kmh", "ms", "mph"), WindUnit.entries.map { it.query })

    @Test fun compassPoints() {
        assertEquals(listOf(0, 0, 1, 2, 4, 7, 0), listOf(0, 22, 23, 90, 180, 315, 359).map(::compassPoint))
    }

    @Test fun rainAndVisibility() {
        assertEquals("2.1 mm", rainText(2.1, imperial = false))
        assertEquals("0.08 in", rainText(2.1, imperial = true))
        assertEquals("24 km", visibilityText(24_140.0, imperial = false))
        assertEquals("15 mi", visibilityText(24_140.0, imperial = true))
    }
}

class AddCityTest {
    @Test fun theTypedPartIsHighlightedWherever() {
        val spans = matched("Port Louis", "lou", Color.Blue).spanStyles
        assertEquals(listOf(5 to 8), spans.map { it.start to it.end })
    }

    @Test fun noMatchNoHighlight() = assertTrue(matched("Porto", "xyz", Color.Blue).spanStyles.isEmpty())

    @Test fun coordinatesUseTheMinusSign() =
        assertEquals("\u221220.16, 57.50", coordinates(City("Port Louis", "", -20.16, 57.5)))
}

class IconTargetTest {
    @Test fun aliasNamesMatchTheManifest() {
        assertEquals(".Indigo", IconTarget.Launcher.alias(AppIcon.Indigo))
        assertEquals(".WeatherTeal", IconTarget.Weather.alias(AppIcon.Teal))
    }

    @Test fun weatherOffersTheSixBackgroundsTealFirst() {
        assertEquals(AppIcon.Teal, IconTarget.Weather.choices.first())
        assertEquals(6, IconTarget.Weather.choices.size)
        assertFalse(AppIcon.Indigo in IconTarget.Weather.choices)
        assertEquals(AppIcon.Teal, Settings().weatherIcon)
    }
}

class BuiltinWidgetTest {
    @Test fun providerRoundTrips() =
        BuiltinWidget.entries.forEach {
            assertEquals(it, BuiltinWidget.of(it.provider))
        }

    @Test fun systemWidgetsAreNotBuiltin() =
        assertNull(BuiltinWidget.of("com.example/.Widget"))
}

class NumeralTintTest {
    private val highlight = Color.Blue
    private val colon = Color.Red

    private fun tints(text: String) = zeros(text, highlight, colon).spanStyles.associate { it.start to it.item.color }

    @Test fun everyZeroTakesTheHighlight() =
        assertEquals(mapOf(0 to highlight, 1 to highlight, 2 to colon, 3 to highlight), tints("00:08"))

    @Test fun onlyZerosAndColonAreTinted() =
        assertEquals(mapOf(1 to highlight, 2 to colon, 3 to highlight), tints("10:08"))

    @Test fun otherDigitsKeepTheInk() = assertTrue(tints("12\n34").isEmpty())
}

class OffsetLabelTest {
    @Test fun behind() = assertEquals("\u22127H", offsetLabel(3 * 3600, -4 * 3600))
    @Test fun aheadWithMinutes() = assertEquals("+5H30", offsetLabel(0, 5 * 3600 + 1800))
    @Test fun same() = assertEquals("0H", offsetLabel(7200, 7200))
}

class WeatherParseTest {
    private val sample = """{"hourly":{"time":["2026-10-11T12:00","2026-10-11T13:00"],"temperature_2m":[14.4,15.6],"weather_code":[3,61]}}"""

    @Test fun parsesHourlyForecast() {
        val hourly = parseForecast(sample)
        assertEquals(2, hourly.time.size)
    }

    @Test fun picksTheCurrentHourFromTheCache() {
        val cache = WeatherCache("50.45,30.52", TemperatureUnit.Celsius, 0L, parseForecast(sample))
        val now = cache.at(Instant.parse("2026-10-11T13:40:00Z"))!!
        assertEquals(16, now.temperature)
        assertEquals(Condition.Rain, now.condition)
    }

    @Test fun outsideTheCacheIsNothing() {
        val cache = WeatherCache("50.45,30.52", TemperatureUnit.Celsius, 0L, parseForecast(sample))
        assertNull(cache.at(Instant.parse("2026-10-20T10:00:00Z")))
    }

    @Test fun wmoCodesMapToConditions() {
        assertEquals(Condition.Clear, Condition.of(0))
        assertEquals(Condition.Rain, Condition.of(63))
        assertEquals(Condition.Snow, Condition.of(73))
        assertEquals(Condition.Storm, Condition.of(95))
    }
}

class ForecastTest {
    private val sample = """{"hourly":{"time":["2026-10-11T12:00"],"temperature_2m":[14.0],"weather_code":[3]}}"""

    private fun point(time: String, temperature: Int, condition: Condition) = HourPoint(LocalDateTime.parse(time), temperature, condition)

    @Test fun dayTakesHighAndLowFromEveryHour() {
        val day = listOf(
            point("2026-10-12T03:00", 6, Condition.Clear),
            point("2026-10-12T14:00", 21, Condition.Cloudy),
            point("2026-10-12T23:00", 9, Condition.Clear),
        ).daily(LocalDate.parse("2026-10-12")).single()
        assertEquals(6, day.low)
        assertEquals(21, day.high)
    }

    @Test fun conditionIsTheMostFrequentDaytimeOne() {
        val day = listOf(
            point("2026-10-12T02:00", 8, Condition.Storm),
            point("2026-10-12T03:00", 8, Condition.Storm),
            point("2026-10-12T10:00", 12, Condition.Cloudy),
            point("2026-10-12T11:00", 13, Condition.Cloudy),
            point("2026-10-12T12:00", 14, Condition.Rain),
        ).daily(LocalDate.parse("2026-10-12")).single()
        assertEquals(Condition.Cloudy, day.condition)
    }

    @Test fun tiesGoToTheWorseCondition() {
        assertEquals(Condition.Rain, dominant(listOf(Condition.Clear, Condition.Rain, Condition.Rain, Condition.Clear)))
        assertEquals(Condition.Storm, dominant(listOf(Condition.Snow, Condition.Storm)))
    }

    @Test fun daysStartTodayAndStopAtTheCount() {
        val points = (0 until 8 * 24).map { point(LocalDateTime.parse("2026-10-11T00:00").plusHours(it.toLong()).toString(), 10, Condition.Clear) }
        val days = points.daily(LocalDate.parse("2026-10-12"))
        assertEquals(6, days.size)
        assertEquals(LocalDate.parse("2026-10-12"), days.first().date)
    }

    @Test fun sixHoursRunAcrossMidnight() {
        val points = (0 until 24).map { point(LocalDateTime.parse("2026-10-11T12:00").plusHours(it.toLong()).toString(), it, Condition.Clear) }
        val hours = points.hourly(LocalDateTime.parse("2026-10-11T21:40")).map { it.time.hour }
        assertEquals(listOf(22, 23, 0, 1, 2, 3), hours)
    }

    @Test fun cacheHoursAreConvertedToTheLocalZone() {
        val cache = WeatherCache("50.45,30.52", TemperatureUnit.Celsius, 0L, parseForecast(
            """{"hourly":{"time":["2026-10-11T21:00","2026-10-11T22:00"],"temperature_2m":[10.0,9.0],"weather_code":[0,0]}}"""
        ))
        val local = cache.points(ZoneId.of("Europe/Kyiv")).map { it.time }
        assertEquals(listOf(LocalDateTime.parse("2026-10-12T00:00"), LocalDateTime.parse("2026-10-12T01:00")), local)
    }

    @Test fun slotsEveryThreeHoursSumTheRain() {
        val points = (0 until 24).map { point(LocalDateTime.parse("2026-10-24T00:00").plusHours(it.toLong()).toString(), it, Condition.Rain) }
            .map { it.copy(rain = 0.5, rainChance = it.temperature) }
        val slots = points.slots(LocalDate.parse("2026-10-24"))
        assertEquals(listOf(0, 3, 6, 9, 12, 15, 18, 21), slots.map { it.time.hour })
        assertEquals(listOf(0, 3, 6, 9, 12, 15, 18, 21), slots.map { it.temperature })
        assertEquals(1.5, slots.first().rain, 1e-9)
        assertEquals(2, slots.first().rainChance)
    }

    @Test fun nextHoursStepByThree() {
        val points = (0 until 24).map { point(LocalDateTime.parse("2026-10-23T00:00").plusHours(it.toLong()).toString(), it, Condition.Clear) }
        assertEquals(listOf(12, 15, 18, 21), points.hourly(LocalDateTime.parse("2026-10-23T09:41"), 4, 3).map { it.time.hour })
    }

    @Test fun sixDaysInTheLocalZone() {
        val start = LocalDateTime.parse("2026-10-23T00:00")
        val hours = (0 until 6 * 24).map { start.plusHours(it.toLong()) }
        val body = """{"hourly":{"time":[${hours.joinToString { "\"$it\"" }}],""" +
            """"temperature_2m":[${hours.joinToString { "${it.hour}.0" }}],"weather_code":[${hours.joinToString { "0" }}]}}"""
        val cache = WeatherCache("50.45,30.52", TemperatureUnit.Celsius, 0L, parseForecast(body))
        val days = cache.ready(Instant.parse("2026-10-23T06:30:00Z"), ZoneId.of("Europe/Kyiv"))!!.days
        assertEquals(6, days.size)
        assertEquals(LocalDate.parse("2026-10-23"), days.first().date)
        assertEquals(0, days.first().low)
        assertEquals(20, days.first().high)
        assertEquals(0, days[1].low)
        assertEquals(23, days[1].high)
    }

    @Test fun newFieldsParseAndOldCachesStillLoad() {
        val body = """{"hourly":{"time":["2026-10-23T06:00"],"temperature_2m":[10.2],"weather_code":[3],""" +
            """"apparent_temperature":[8.4],"precipitation_probability":[10],"precipitation":[0.2],"wind_speed_10m":[14.0],""" +
            """"wind_direction_10m":[315],"relative_humidity_2m":[78],"pressure_msl":[1016.0],"uv_index":[2.1],"visibility":[24000.0]},""" +
            """"daily":{"time":["2026-10-23"],"sunrise":["2026-10-23T04:41"],"sunset":["2026-10-23T14:58"]}}"""
        val zone = ZoneId.of("Europe/Kyiv")
        val cache = WeatherCache("50.45,30.52", TemperatureUnit.Celsius, 0L, parseForecast(body), parseDaily(body))
        val hour = cache.points(zone).single()
        assertEquals(listOf(8, 10, 14, 315, 78, 2), listOf(hour.feels, hour.rainChance, hour.wind, hour.windDirection, hour.humidity, hour.uv))
        assertEquals(SunTimes(LocalDate.parse("2026-10-23"), LocalTime.parse("07:41"), LocalTime.parse("17:58")), cache.sun(zone).single())
        val old = cache.copy(hourly = parseForecast(sample), daily = DailyForecast())
        assertEquals(old.hourly.temperature[0].toInt(), old.points(zone).first().feels)
    }

    @Test fun degreeSignIsSoftened() =
        assertEquals(mapOf(1 to Color.Blue, 2 to Color.Red), zeros("10°", Color.Blue, Color.Red).spanStyles.associate { it.start to it.item.color })
}

