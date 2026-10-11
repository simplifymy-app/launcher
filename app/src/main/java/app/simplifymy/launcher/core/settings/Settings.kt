package app.simplifymy.launcher.core.settings

import androidx.annotation.StringRes
import app.simplifymy.launcher.R
import app.simplifymy.launcher.core.actions.Action
import app.simplifymy.launcher.ui.theme.AccentPreset
import app.simplifymy.launcher.ui.theme.Contrast
import app.simplifymy.launcher.ui.theme.CornerRadius
import app.simplifymy.launcher.ui.theme.InterfaceFamily
import app.simplifymy.launcher.ui.theme.TextScale
import app.simplifymy.launcher.ui.theme.ThemeMode
import app.simplifymy.launcher.util.AppIcon
import app.simplifymy.launcher.util.Language
import kotlinx.serialization.Serializable

enum class ScrollEffect(@StringRes val label: Int) {
    Simple(R.string.effect_simple),
    Cube(R.string.effect_cube),
    Stack(R.string.effect_stack),
    Zoom(R.string.effect_zoom),
    Fade(R.string.effect_fade),
    Tablet(R.string.effect_tablet),
}

enum class PageIndicator(@StringRes val label: Int) {
    None(R.string.indicator_none),
    Dots(R.string.indicator_dots),
    Line(R.string.indicator_line),
    Numeric(R.string.indicator_numeric),
}

enum class SearchBarPlacement(@StringRes val label: Int) {
    None(R.string.placement_none),
    Top(R.string.placement_top),
    AboveDock(R.string.placement_above_dock),
    BelowDock(R.string.placement_below_dock),
}

enum class DockBackground(@StringRes val label: Int) {
    None(R.string.dock_bg_none),
    Surface(R.string.dock_bg_surface),
}

enum class DrawerStyle(@StringRes val label: Int) {
    Vertical(R.string.drawer_vertical),
    Paged(R.string.drawer_paged),
    List(R.string.drawer_list),
}

enum class DrawerPosition(@StringRes val label: Int, val openY: Float) {
    Bottom(R.string.position_bottom, -1f),
    Top(R.string.position_top, 1f),
    FirstPage(R.string.position_first_page, 0f),
    LastPage(R.string.position_last_page, 0f),
    ;

    val isPage: Boolean get() = openY == 0f
}

enum class DrawerSort(@StringRes val label: Int) {
    Name(R.string.sort_name),
    Installed(R.string.sort_installed),
    Usage(R.string.sort_usage),
}

enum class FolderPreview(@StringRes val label: Int) {
    Grid(R.string.folder_grid),
    Stack(R.string.folder_stack),
    Line(R.string.folder_line),
}

enum class FolderStyle(@StringRes val label: Int) {
    Popup(R.string.folder_style_popup),
    FullScreen(R.string.folder_style_full),
}

enum class WidgetColor(@StringRes val label: Int) {
    Auto(R.string.color_auto),
    White(R.string.color_white),
    Black(R.string.color_black),
    Accent(R.string.color_accent),
}

enum class WidgetWeight(@StringRes val label: Int) {
    Regular(R.string.weight_regular),
    Medium(R.string.weight_medium),
}

enum class WeatherIcons(@StringRes val label: Int) {
    Both(R.string.weather_icons_both),
    Icon(R.string.weather_icons_icon),
    Word(R.string.weather_icons_word),
    ;

    val showIcon: Boolean get() = this != Word
    val showWord: Boolean get() = this != Icon
}

@Serializable
data class City(val name: String, val region: String, val latitude: Double, val longitude: Double, val timezone: String = "")

enum class TemperatureUnit(val label: String, val query: String) {
    Celsius("°C", "celsius"),
    Fahrenheit("°F", "fahrenheit"),
}

enum class IconShape(@StringRes val label: Int) {
    System(R.string.shape_system),
    Circle(R.string.shape_circle),
    Squircle(R.string.shape_squircle),
    Rounded(R.string.shape_rounded),
    Teardrop(R.string.shape_teardrop),
    Square(R.string.shape_square),
}

enum class BadgeStyle(@StringRes val label: Int) {
    None(R.string.badge_none),
    Dot(R.string.badge_dot),
    Count(R.string.badge_count),
}

enum class HomeAction(@StringRes val label: Int) {
    DefaultPage(R.string.home_default_page),
    Drawer(R.string.home_drawer),
    Overview(R.string.home_overview),
    Nothing(R.string.home_nothing),
}

enum class Orientation(@StringRes val label: Int) {
    Auto(R.string.orientation_auto),
    Portrait(R.string.orientation_portrait),
    Landscape(R.string.orientation_landscape),
}

@Serializable
data class Settings(
    val themeMode: ThemeMode = ThemeMode.System,
    val accent: AccentPreset = AccentPreset.Indigo,
    val customAccent: Int? = null,
    val contrast: Contrast = Contrast.Normal,
    val family: InterfaceFamily = InterfaceFamily.SpaceGrotesk,
    val textScale: TextScale = TextScale.M,
    val cornerRadius: CornerRadius = CornerRadius.M,
    val language: Language = Language.System,
    val appIcon: AppIcon = AppIcon.Indigo,
    val desktopColumns: Int = 5,
    val desktopRows: Int = 6,
    val desktopPages: Int = 2,
    val defaultPage: Int = 0,
    val desktopIconSize: Int = 100,
    val desktopLabels: Boolean = true,
    val twoLineLabels: Boolean = false,
    val scrollEffect: ScrollEffect = ScrollEffect.Simple,
    val infiniteScroll: Boolean = false,
    val pageIndicator: PageIndicator = PageIndicator.Numeric,
    val wallpaperScroll: Boolean = true,
    val wallpaperDim: Int = 0,
    val searchBar: SearchBarPlacement = SearchBarPlacement.Top,
    val lockDesktop: Boolean = false,
    val dockEnabled: Boolean = true,
    val dockColumns: Int = 5,
    val dockPages: Int = 1,
    val dockIconSize: Int = 100,
    val dockLabels: Boolean = false,
    val dockBackground: DockBackground = DockBackground.None,
    val dockOpacity: Int = 70,
    val drawerStyle: DrawerStyle = DrawerStyle.Vertical,
    val drawerPosition: DrawerPosition = DrawerPosition.Bottom,
    val drawerColumns: Int = 5,
    val drawerRows: Int = 6,
    val drawerIconSize: Int = 100,
    val drawerLabels: Boolean = true,
    val drawerSort: DrawerSort = DrawerSort.Name,
    val frequentRow: Boolean = true,
    val drawerOpacity: Int = 100,
    val fastScroll: Boolean = true,
    val hiddenInSearch: Boolean = true,
    val workTab: Boolean = true,
    val searchContacts: Boolean = false,
    val folderPreview: FolderPreview = FolderPreview.Grid,
    val folderColumns: Int = 4,
    val folderStyle: FolderStyle = FolderStyle.Popup,
    val iconPack: String? = null,
    val iconShape: IconShape = IconShape.System,
    val swipeUp: Action = Action.Drawer,
    val swipeDown: Action = Action.Notifications,
    val doubleTap: Action = Action.None,
    val pinchIn: Action = Action.Overview,
    val pinchOut: Action = Action.None,
    val twoFingerUp: Action = Action.None,
    val twoFingerDown: Action = Action.QuickSettings,
    val homeAction: HomeAction = HomeAction.DefaultPage,
    val badgeStyle: BadgeStyle = BadgeStyle.Dot,

    val widgetColor: WidgetColor = WidgetColor.Auto,
    val widgetWeight: WidgetWeight = WidgetWeight.Regular,
    val weather: Boolean = false,
    val weatherCity: City? = null,
    val worldCity: City? = null,
    val weatherIcons: WeatherIcons = WeatherIcons.Both,
    val temperatureUnit: TemperatureUnit = TemperatureUnit.Celsius,
    val hideStatusBar: Boolean = false,
    val orientation: Orientation = Orientation.Auto,
    val revealAnimation: Boolean = true,
)
