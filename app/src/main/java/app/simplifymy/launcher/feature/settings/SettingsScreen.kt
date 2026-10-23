package app.simplifymy.launcher.feature.settings

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.simplifymy.launcher.BuildConfig
import app.simplifymy.launcher.WeatherActivity
import app.simplifymy.launcher.feature.weather.WeatherView
import app.simplifymy.launcher.ui.component.Icon
import app.simplifymy.launcher.ui.component.Icons
import androidx.core.net.toUri
import java.time.Duration
import app.simplifymy.launcher.R
import app.simplifymy.launcher.container
import app.simplifymy.launcher.core.actions.Action
import app.simplifymy.launcher.core.badges.Badges
import app.simplifymy.launcher.core.icons.IconPack
import app.simplifymy.launcher.core.settings.BadgeStyle
import app.simplifymy.launcher.core.settings.DockBackground
import app.simplifymy.launcher.core.settings.DrawerPosition
import app.simplifymy.launcher.core.settings.DrawerSort
import app.simplifymy.launcher.core.settings.DrawerStyle
import app.simplifymy.launcher.core.settings.FolderPreview
import app.simplifymy.launcher.core.settings.FolderStyle
import app.simplifymy.launcher.core.settings.HomeAction
import app.simplifymy.launcher.core.settings.IconShape
import app.simplifymy.launcher.core.settings.Orientation
import app.simplifymy.launcher.core.settings.PageIndicator
import app.simplifymy.launcher.core.settings.ScrollEffect
import app.simplifymy.launcher.core.settings.SearchBarPlacement
import app.simplifymy.launcher.core.settings.Settings
import app.simplifymy.launcher.core.settings.MAX_CITIES
import app.simplifymy.launcher.core.settings.PressureUnit
import app.simplifymy.launcher.core.settings.REFRESH_HOURS
import app.simplifymy.launcher.core.settings.TemperatureUnit
import app.simplifymy.launcher.core.settings.WindUnit
import app.simplifymy.launcher.core.settings.WeatherIcons
import app.simplifymy.launcher.core.settings.WidgetColor
import app.simplifymy.launcher.core.settings.WidgetWeight
import app.simplifymy.launcher.core.weather.WeatherState
import app.simplifymy.launcher.ui.component.AppIconPicker
import app.simplifymy.launcher.ui.component.ButtonKind
import app.simplifymy.launcher.ui.component.SettingsRow
import app.simplifymy.launcher.ui.component.SimplifyButton
import app.simplifymy.launcher.ui.component.Snackbar
import app.simplifymy.launcher.ui.component.SwatchRow
import app.simplifymy.launcher.ui.component.TopLevelBar
import app.simplifymy.launcher.ui.launcher.ActionPicker
import app.simplifymy.launcher.ui.launcher.actionLabel
import app.simplifymy.launcher.ui.theme.AccentPreset
import app.simplifymy.launcher.ui.theme.Contrast
import app.simplifymy.launcher.ui.theme.InterfaceFamily
import app.simplifymy.launcher.ui.theme.Radius
import app.simplifymy.launcher.ui.theme.RoleText
import app.simplifymy.launcher.ui.theme.Simplify
import app.simplifymy.launcher.ui.theme.Space
import app.simplifymy.launcher.ui.theme.TextScale
import app.simplifymy.launcher.ui.theme.ThemeMode
import app.simplifymy.launcher.util.IconTarget
import app.simplifymy.launcher.util.Language
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Page { Root, Desktop, Dock, Drawer, Folders, Widgets, Calendar, Weather, Look, Gestures, Badges, Hidden, Groups, Backup }

@Composable
fun SettingsScreen(initial: Page = Page.Root, onExit: () -> Unit) {
    var page by rememberSaveable { mutableStateOf(initial) }
    val parent = if (page == Page.Calendar) Page.Widgets else Page.Root
    BackHandler(page != Page.Root) { page = parent }
    val back = { if (page == Page.Root) onExit() else page = parent }
    Box(Modifier.fillMaxSize().background(Simplify.colors.background)) {
        when (page) {
            Page.Root -> Root { page = it }
            Page.Desktop -> DesktopPage(back)
            Page.Dock -> DockPage(back)
            Page.Drawer -> DrawerPage(back) { page = it }
            Page.Folders -> FoldersPage(back)
            Page.Widgets -> WidgetsPage(back) { page = it }
            Page.Calendar -> CalendarPage(back) { page = it }
            Page.Weather -> WeatherPage(back)
            Page.Look -> LookPage(back)
            Page.Gestures -> GesturesPage(back)
            Page.Badges -> BadgesPage(back)
            Page.Hidden -> HiddenAppsPage(back)
            Page.Groups -> GroupsPage(back)
            Page.Backup -> BackupPage(back)
        }
    }
}

@Composable
internal fun settings(): Pair<Settings, ((Settings) -> Settings) -> Unit> {
    val container = LocalContext.current.container
    val current by container.settings.settings.collectAsState()
    val scope = rememberCoroutineScope()
    return current to { edit -> scope.launch { container.settings.update(edit) } }
}

@Composable
internal fun <T> onResume(read: () -> T): T {
    val owner = LocalLifecycleOwner.current
    var value by remember { mutableStateOf(read()) }
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { value = read() }
    }
    return value
}

@Composable
private fun Root(open: (Page) -> Unit) {
    val context = LocalContext.current
    val roles = context.getSystemService(RoleManager::class.java)
    val isDefault = onResume { roles.isRoleHeld(RoleManager.ROLE_HOME) }
    val askRole = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        TopLevelBar(stringResource(R.string.settings_title))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (!isDefault) {
                Column(
                    Modifier
                        .padding(horizontal = Space.screen, vertical = Space.base)
                        .fillMaxWidth()
                        .clip(Radius.card)
                        .background(Simplify.colors.surface)
                        .padding(Space.rowGap),
                ) {
                    RoleText(stringResource(R.string.default_title), Simplify.type.title)
                    RoleText(stringResource(R.string.default_body), Simplify.type.body, color = Simplify.colors.textMuted,
                        modifier = Modifier.padding(vertical = Space.base))
                    SimplifyButton(stringResource(R.string.default_action), {
                        askRole.launch(roles.createRequestRoleIntent(RoleManager.ROLE_HOME))
                    }, kind = ButtonKind.Primary)
                }
            }
            Section(R.string.section_layout)
            NavRow(R.string.page_desktop, stringResource(R.string.page_desktop_hint)) { open(Page.Desktop) }
            NavRow(R.string.page_dock, stringResource(R.string.page_dock_hint)) { open(Page.Dock) }
            NavRow(R.string.page_drawer, stringResource(R.string.page_drawer_hint)) { open(Page.Drawer) }
            NavRow(R.string.page_folders, stringResource(R.string.page_folders_hint)) { open(Page.Folders) }
            NavRow(R.string.page_widgets, stringResource(R.string.page_widgets_hint)) { open(Page.Widgets) }
            NavRow(R.string.page_weather, stringResource(R.string.page_weather_hint)) { open(Page.Weather) }
            Section(R.string.section_personalise)
            NavRow(R.string.page_look, stringResource(R.string.page_look_hint)) { open(Page.Look) }
            NavRow(R.string.page_gestures, stringResource(R.string.page_gestures_hint)) { open(Page.Gestures) }
            NavRow(R.string.page_badges, stringResource(R.string.page_badges_hint)) { open(Page.Badges) }
            Section(R.string.section_system)
            NavRow(R.string.page_backup, stringResource(R.string.page_backup_hint)) { open(Page.Backup) }
            val (s, update) = settings()
            ChoiceRow(R.string.language_label, Language.entries, s.language, optionLabel = { stringResource(it.label) }) { l ->
                update { it.copy(language = l) }
            }
            ValueRow(stringResource(R.string.version_label), BuildConfig.VERSION_NAME)
        }
    }
}

@Composable
private fun DesktopPage(back: () -> Unit) {
    val (s, update) = settings()
    SettingsPage(stringResource(R.string.page_desktop), back, meta = "${s.desktopColumns} × ${s.desktopRows}") {
        Section(R.string.section_grid)
        StepperRow(R.string.grid_columns, s.desktopColumns, 3..12) { v -> update { it.copy(desktopColumns = v) } }
        StepperRow(R.string.grid_rows, s.desktopRows, 3..12) { v -> update { it.copy(desktopRows = v) } }
        StepperRow(R.string.icon_size, s.desktopIconSize, 50..150, step = 5, format = { "$it%" }) { v -> update { it.copy(desktopIconSize = v) } }
        ToggleRow(R.string.show_labels, s.desktopLabels) { v -> update { it.copy(desktopLabels = v) } }
        ToggleRow(R.string.two_line_labels, s.twoLineLabels) { v -> update { it.copy(twoLineLabels = v) } }
        Section(R.string.section_scrolling)
        ChoiceRow(R.string.scroll_effect, ScrollEffect.entries, s.scrollEffect, optionLabel = { stringResource(it.label) }) { v ->
            update { it.copy(scrollEffect = v) }
        }
        ChoiceRow(R.string.page_indicator, PageIndicator.entries, s.pageIndicator, optionLabel = { stringResource(it.label) }) { v ->
            update { it.copy(pageIndicator = v) }
        }
        ToggleRow(R.string.infinite_scroll, s.infiniteScroll) { v -> update { it.copy(infiniteScroll = v) } }
        ToggleRow(R.string.wallpaper_scroll, s.wallpaperScroll) { v -> update { it.copy(wallpaperScroll = v) } }
        Section(R.string.section_search)
        ChoiceRow(R.string.search_bar, SearchBarPlacement.entries, s.searchBar, optionLabel = { stringResource(it.label) }) { v ->
            update { it.copy(searchBar = v) }
        }
        Section(R.string.section_advanced)
        ToggleRow(R.string.lock_desktop, s.lockDesktop, stringResource(R.string.lock_desktop_hint)) { v -> update { it.copy(lockDesktop = v) } }
        StepperRow(R.string.wallpaper_dim, s.wallpaperDim, 0..64, step = 8, format = { "$it%" }) { v -> update { it.copy(wallpaperDim = v) } }
    }
}

@Composable
private fun DockPage(back: () -> Unit) {
    val (s, update) = settings()
    SettingsPage(stringResource(R.string.page_dock), back) {
        ToggleRow(R.string.dock_enabled, s.dockEnabled) { v -> update { it.copy(dockEnabled = v) } }
        Section(R.string.section_grid)
        StepperRow(R.string.dock_icons, s.dockColumns, 3..9) { v -> update { it.copy(dockColumns = v) } }
        StepperRow(R.string.dock_pages, s.dockPages, 1..3) { v -> update { it.copy(dockPages = v) } }
        StepperRow(R.string.icon_size, s.dockIconSize, 50..150, step = 5, format = { "$it%" }) { v -> update { it.copy(dockIconSize = v) } }
        ToggleRow(R.string.show_labels, s.dockLabels) { v -> update { it.copy(dockLabels = v) } }
        Section(R.string.section_background)
        ChoiceRow(R.string.dock_background, DockBackground.entries, s.dockBackground, optionLabel = { stringResource(it.label) }) { v ->
            update { it.copy(dockBackground = v) }
        }
        if (s.dockBackground != DockBackground.None) {
            StepperRow(R.string.opacity, s.dockOpacity, 10..100, step = 10, format = { "$it%" }) { v -> update { it.copy(dockOpacity = v) } }
        }
    }
}

@Composable
private fun DrawerPage(back: () -> Unit, open: (Page) -> Unit) {
    val (s, update) = settings()
    val contacts = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        update { it.copy(searchContacts = granted) }
    }
    SettingsPage(stringResource(R.string.page_drawer), back) {
        Section(R.string.section_style)
        ChoiceRow(R.string.drawer_style, DrawerStyle.entries, s.drawerStyle, optionLabel = { stringResource(it.label) }) { v ->
            update { it.copy(drawerStyle = v) }
        }
        ChoiceRow(
            R.string.drawer_position, DrawerPosition.entries, s.drawerPosition,
            subline = stringResource(R.string.drawer_position_hint),
            segmented = false,
            optionLabel = { stringResource(it.label) },
        ) { v -> update { it.copy(drawerPosition = v) } }
        ChoiceRow(R.string.drawer_sort, DrawerSort.entries, s.drawerSort, optionLabel = { stringResource(it.label) }) { v ->
            update { it.copy(drawerSort = v) }
        }
        StepperRow(R.string.grid_columns, s.drawerColumns, 3..8) { v -> update { it.copy(drawerColumns = v) } }
        if (s.drawerStyle == DrawerStyle.Paged) {
            StepperRow(R.string.grid_rows, s.drawerRows, 3..10) { v -> update { it.copy(drawerRows = v) } }
        }
        StepperRow(R.string.icon_size, s.drawerIconSize, 50..150, step = 5, format = { "$it%" }) { v -> update { it.copy(drawerIconSize = v) } }
        ToggleRow(R.string.show_labels, s.drawerLabels) { v -> update { it.copy(drawerLabels = v) } }
        StepperRow(R.string.opacity, s.drawerOpacity, 40..100, step = 10, format = { "$it%" }) { v -> update { it.copy(drawerOpacity = v) } }
        Section(R.string.section_contents)
        ToggleRow(R.string.frequent_row, s.frequentRow) { v -> update { it.copy(frequentRow = v) } }
        ToggleRow(R.string.fast_scroll, s.fastScroll, stringResource(R.string.fast_scroll_hint)) { v -> update { it.copy(fastScroll = v) } }
        ToggleRow(R.string.work_tab, s.workTab) { v -> update { it.copy(workTab = v) } }
        NavRow(R.string.page_groups, stringResource(R.string.page_groups_hint)) { open(Page.Groups) }
        NavRow(R.string.page_hidden, stringResource(R.string.page_hidden_hint)) { open(Page.Hidden) }
        Section(R.string.section_search)
        ToggleRow(R.string.hidden_in_search, s.hiddenInSearch) { v -> update { it.copy(hiddenInSearch = v) } }
        ToggleRow(R.string.search_contacts, s.searchContacts, stringResource(R.string.search_contacts_hint)) { v ->
            if (v) contacts.launch(Manifest.permission.READ_CONTACTS) else update { it.copy(searchContacts = false) }
        }
    }
}

@Composable
private fun FoldersPage(back: () -> Unit) {
    val (s, update) = settings()
    SettingsPage(stringResource(R.string.page_folders), back) {
        ChoiceRow(R.string.folder_style, FolderStyle.entries, s.folderStyle, optionLabel = { stringResource(it.label) }) { v ->
            update { it.copy(folderStyle = v) }
        }
        ChoiceRow(R.string.folder_preview, FolderPreview.entries, s.folderPreview, optionLabel = { stringResource(it.label) }) { v ->
            update { it.copy(folderPreview = v) }
        }
        StepperRow(R.string.folder_columns, s.folderColumns, 3..6) { v -> update { it.copy(folderColumns = v) } }
        SettingsRow(stringResource(R.string.folder_swipe_to_open), subline = stringResource(R.string.folder_swipe_hint_settings))
    }
}

@Composable
private fun WidgetsPage(back: () -> Unit, open: (Page) -> Unit) {
    val (s, update) = settings()
    var choosingWorldCity by remember { mutableStateOf(false) }
    SettingsPage(stringResource(R.string.page_widgets), back) {
        Section(R.string.section_style)
        ChoiceRow(R.string.widget_color, WidgetColor.entries, s.widgetColor, optionLabel = { stringResource(it.label) }) { v ->
            update { it.copy(widgetColor = v) }
        }
        ChoiceRow(R.string.widget_weight, WidgetWeight.entries, s.widgetWeight, optionLabel = { stringResource(it.label) }) { v ->
            update { it.copy(widgetWeight = v) }
        }
        ChoiceRow(R.string.weather_icons, WeatherIcons.entries, s.weatherIcons, optionLabel = { stringResource(it.label) }) { v ->
            update { it.copy(weatherIcons = v) }
        }
        Section(R.string.page_calendar)
        NavRow(R.string.page_calendar, stringResource(R.string.page_calendar_hint)) { open(Page.Calendar) }
        Section(R.string.widget_world)
        NavRow(R.string.widget_second_city, s.worldCity?.name ?: stringResource(R.string.weather_choose_city)) { choosingWorldCity = true }
    }
    CityPicker(
        choosingWorldCity, onPick = { city -> update { it.copy(worldCity = city) } }, onDismiss = { choosingWorldCity = false },
        title = stringResource(R.string.widget_second_city),
        current = s.worldCity, onRemove = { update { it.copy(worldCity = null) } },
    )
}

@Composable
private fun WeatherPage(back: () -> Unit) {
    val (s, update) = settings()
    val context = LocalContext.current
    val weather by context.container.weather.state.collectAsState()
    var choosingCity by remember { mutableStateOf(false) }
    SettingsPage(stringResource(R.string.page_weather), back) {
        ToggleRow(R.string.weather_label, s.weather, stringResource(R.string.weather_hint)) { on ->
            update { it.copy(weather = on) }
            if (on && s.weatherCity == null) choosingCity = true
        }
        if (s.weather) {
            NavRow(
                R.string.weather_cities,
                s.weatherCity?.let { stringResource(R.string.weather_cities_hint_row, it.name, s.weatherCities.size, MAX_CITIES) }
                    ?: stringResource(R.string.weather_choose_city),
            ) { context.startActivity(WeatherActivity.open(context, WeatherView.Cities)) }
            Section(R.string.section_units)
            ChoiceRow(R.string.weather_temperature, TemperatureUnit.entries, s.temperatureUnit, optionLabel = { it.label }) { v ->
                update { it.copy(temperatureUnit = v) }
            }
            ChoiceRow(R.string.weather_wind, WindUnit.entries, s.windUnit, optionLabel = { it.label }) { v ->
                update { it.copy(windUnit = v) }
            }
            ChoiceRow(R.string.weather_pressure, PressureUnit.entries, s.pressureUnit, optionLabel = { it.label }) { v ->
                update { it.copy(pressureUnit = v) }
            }
            Section(R.string.section_data)
            ChoiceRow(
                R.string.weather_refresh, REFRESH_HOURS, s.weatherRefresh,
                subline = (weather as? WeatherState.Ready)?.now?.updated?.let { last ->
                    val clock = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
                    stringResource(R.string.weather_refresh_status, clock.format(last), clock.format(last.plus(Duration.ofHours(s.weatherRefresh.toLong()))))
                },
                segmented = false,
                optionLabel = { stringResource(refreshLabel(it)) },
            ) { v -> update { it.copy(weatherRefresh = v) } }
            SettingsRow(
                stringResource(R.string.weather_data), subline = stringResource(R.string.weather_data_hint),
                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, OPEN_METEO.toUri())) },
            ) { Icon(Icons.External, tint = Simplify.colors.textMuted, decorative = true) }
            ValueRow(
                stringResource(R.string.weather_status),
                when (val w = weather) {
                    is WeatherState.Ready -> "${w.now.temperature}° · " +
                        stringResource(R.string.weather_updated, DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(w.now.updated))
                    WeatherState.NoCity -> stringResource(R.string.weather_choose_city)
                    WeatherState.Offline -> stringResource(R.string.weather_offline)
                    WeatherState.Off, WeatherState.Loading -> ""
                },
            )
        }
        Section(R.string.section_weather_icon)
        AppIconPicker(s.weatherIcon, { icon -> update { it.copy(weatherIcon = icon) } }, target = IconTarget.Weather)
    }
    CityPicker(choosingCity, onPick = { city -> update { it.addCity(city) } }, onDismiss = { choosingCity = false })
}

private const val OPEN_METEO = "https://open-meteo.com"

private fun refreshLabel(hours: Int): Int = when (hours) {
    1 -> R.string.refresh_1
    3 -> R.string.refresh_3
    6 -> R.string.refresh_6
    else -> R.string.refresh_2
}

@Composable
private fun LookPage(back: () -> Unit) {
    val (s, update) = settings()
    val context = LocalContext.current
    val packs = remember { IconPack.installed(context) }
    SettingsPage(stringResource(R.string.page_look), back) {
        Section(R.string.section_theme)
        ChoiceRow(R.string.theme_label, ThemeMode.entries, s.themeMode, optionLabel = {
            stringResource(
                when (it) {
                    ThemeMode.System -> R.string.theme_system
                    ThemeMode.Light -> R.string.theme_light
                    ThemeMode.Dark -> R.string.theme_dark
                    ThemeMode.Black -> R.string.theme_black
                }
            )
        }) { v -> update { it.copy(themeMode = v) } }
        SettingsRow(
            stringResource(R.string.accent_label),
            subline = "#%06X".format(Locale.ROOT, Simplify.colors.accent.value.toLong().ushr(32).toInt() and 0xFFFFFF),
        )
        SwatchRow(
            presets = AccentPreset.entries.map { if (Simplify.colors.isLight) it.lightFill else it.darkFill },
            selectedIndex = if (s.customAccent == null) AccentPreset.entries.indexOf(s.accent) else -1,
            onSelect = { i -> update { it.copy(accent = AccentPreset.entries[i], customAccent = null) } },
            modifier = Modifier.padding(horizontal = Space.screen),
        )
        ChoiceRow(R.string.contrast_label, Contrast.entries, s.contrast, optionLabel = {
            stringResource(when (it) { Contrast.Soft -> R.string.contrast_soft; Contrast.Normal -> R.string.contrast_normal; Contrast.High -> R.string.contrast_high })
        }) { v -> update { it.copy(contrast = v) } }
        ChoiceRow(R.string.font_label, InterfaceFamily.entries, s.family, segmented = false, optionLabel = {
            when (it) {
                InterfaceFamily.SpaceGrotesk -> "Space Grotesk"
                InterfaceFamily.Outfit -> "Outfit"
                InterfaceFamily.IBMPlexSans -> "IBM Plex Sans"
                InterfaceFamily.System -> stringResource(R.string.theme_system)
            }
        }) { v -> update { it.copy(family = v) } }
        ChoiceRow(R.string.text_size_label, TextScale.entries, s.textScale, optionLabel = { it.name }) { v -> update { it.copy(textScale = v) } }

        Section(R.string.section_icons)
        ChoiceRow(R.string.icon_shape, IconShape.entries, s.iconShape, optionLabel = { stringResource(it.label) }) { v ->
            update { it.copy(iconShape = v) }
        }
        val packNames = listOf<String?>(null) + packs.map { it.packageName }
        ChoiceRow(
            R.string.icon_pack,
            packNames,
            s.iconPack?.takeIf { p -> packs.any { it.packageName == p } },
            subline = if (packs.isEmpty()) stringResource(R.string.no_icon_packs) else null,
            segmented = false,
            optionLabel = { name -> packs.firstOrNull { it.packageName == name }?.label ?: stringResource(R.string.icon_pack_none) },
        ) { v -> update { it.copy(iconPack = v) } }

        Section(R.string.section_system)
        ToggleRow(R.string.hide_status_bar, s.hideStatusBar) { v -> update { it.copy(hideStatusBar = v) } }
        ToggleRow(R.string.reveal_animation, s.revealAnimation, stringResource(R.string.reveal_animation_hint)) { v -> update { it.copy(revealAnimation = v) } }
        ChoiceRow(R.string.orientation_label, Orientation.entries, s.orientation, optionLabel = { stringResource(it.label) }) { v ->
            update { it.copy(orientation = v) }
        }
        Section(R.string.section_app_icon)
        AppIconPicker(s.appIcon, { icon -> update { it.copy(appIcon = icon) } })
    }
}

private enum class Gesture(val label: Int, val get: (Settings) -> Action, val set: (Settings, Action) -> Settings) {
    SwipeUp(R.string.gesture_swipe_up, { it.swipeUp }, { s, a -> s.copy(swipeUp = a) }),
    SwipeDown(R.string.gesture_swipe_down, { it.swipeDown }, { s, a -> s.copy(swipeDown = a) }),
    DoubleTap(R.string.gesture_double_tap, { it.doubleTap }, { s, a -> s.copy(doubleTap = a) }),
    PinchIn(R.string.gesture_pinch_in, { it.pinchIn }, { s, a -> s.copy(pinchIn = a) }),
    PinchOut(R.string.gesture_pinch_out, { it.pinchOut }, { s, a -> s.copy(pinchOut = a) }),
    TwoUp(R.string.gesture_two_up, { it.twoFingerUp }, { s, a -> s.copy(twoFingerUp = a) }),
    TwoDown(R.string.gesture_two_down, { it.twoFingerDown }, { s, a -> s.copy(twoFingerDown = a) }),
}

@Composable
private fun GesturesPage(back: () -> Unit) {
    val (s, update) = settings()
    val context = LocalContext.current
    val container = context.container
    var editing by remember { mutableStateOf<Gesture?>(null) }
    val accessibility = onResume { container.actions.accessibilityEnabled }
    SettingsPage(stringResource(R.string.page_gestures), back) {
        Section(R.string.section_gestures)
        Gesture.entries.forEach { gesture ->
            SettingsRow(stringResource(gesture.label), subline = actionLabel(gesture.get(s)), onClick = { editing = gesture })
        }
        Section(R.string.section_home_button)
        ChoiceRow(R.string.home_button, HomeAction.entries, s.homeAction, optionLabel = { stringResource(it.label) }) { v ->
            update { it.copy(homeAction = v) }
        }
        Section(R.string.section_permissions)
        ValueRow(
            stringResource(R.string.accessibility_label),
            stringResource(if (accessibility) R.string.state_on else R.string.state_off).uppercase(),
        ) { container.actions.start(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS)) }
        RoleText(
            stringResource(R.string.accessibility_hint), Simplify.type.meta, color = Simplify.colors.textMuted,
            modifier = Modifier.padding(horizontal = Space.screen),
        )
    }
    val current = editing
    ActionPicker(
        visible = current != null,
        title = current?.let { stringResource(it.label) }.orEmpty(),
        selected = current?.get?.invoke(s),
        onPick = { action -> current?.let { g -> update { g.set(it, action) } } },
        onDismiss = { editing = null },
    )
}

@Composable
private fun BadgesPage(back: () -> Unit) {
    val (s, update) = settings()
    val context = LocalContext.current
    val granted = onResume { Badges.granted(context) }
    SettingsPage(stringResource(R.string.page_badges), back) {
        ChoiceRow(R.string.badge_style, BadgeStyle.entries, s.badgeStyle, optionLabel = { stringResource(it.label) }) { v ->
            update { it.copy(badgeStyle = v) }
        }
        Section(R.string.section_permissions)
        ValueRow(
            stringResource(R.string.notification_access),
            stringResource(if (granted) R.string.state_on else R.string.state_off).uppercase(),
        ) { context.container.actions.start(Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        RoleText(
            stringResource(R.string.notification_access_hint), Simplify.type.meta, color = Simplify.colors.textMuted,
            modifier = Modifier.padding(horizontal = Space.screen),
        )
    }
}

@Composable
private fun BackupPage(back: () -> Unit) {
    val context = LocalContext.current
    val container = context.container
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var stamp by remember { mutableIntStateOf(0) }
    val exported = stringResource(R.string.backup_done)
    val failed = stringResource(R.string.backup_failed)
    val restoredText = stringResource(R.string.restore_done)
    val restoredWidgets = stringResource(R.string.restore_done_widgets)
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            message = runCatching { container.backup.export(uri) }.fold({ exported }, { failed })
            stamp++
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            message = runCatching { container.backup.import(uri) }.fold(
                { dropped -> if (dropped > 0) restoredWidgets.format(dropped) else restoredText },
                { failed },
            )
            stamp++
        }
    }
    LaunchedEffect(stamp) { if (message != null) { delay(3000); message = null } }
    Box(Modifier.fillMaxSize()) {
        SettingsPage(stringResource(R.string.page_backup), back) {
            SettingsRow(stringResource(R.string.backup_export), subline = stringResource(R.string.backup_export_hint),
                onClick = { export.launch("simplify-launcher-${LocalDate.now()}.json") })
            SettingsRow(stringResource(R.string.backup_import), subline = stringResource(R.string.backup_import_hint),
                onClick = { import.launch(arrayOf("application/json", "*/*")) })
        }
        Snackbar(message, Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing))
    }
}
