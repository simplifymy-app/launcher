package app.simplifymy.launcher.ui.component

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import app.simplifymy.launcher.R
import app.simplifymy.launcher.core.weather.Condition
import app.simplifymy.launcher.ui.theme.Simplify
import app.simplifymy.launcher.ui.theme.Sizes

data class SimplifyIcon(val path: String, @StringRes val label: Int, val filled: Boolean = false)

private const val VIEWPORT = 20f
private const val STROKE = 1.5f

object Icons {
    val Back = SimplifyIcon("M12.5 4L6.5 10L12.5 16", R.string.icon_back)
    val ChevronDown = SimplifyIcon("M5.5 8L10 12.5L14.5 8", R.string.icon_expand)
    val ChevronRight = SimplifyIcon("M8 5.5L12.5 10L8 14.5", R.string.icon_open)
    val Search = SimplifyIcon(
        "M8.5 3A5.5 5.5 0 1 0 8.5 14A5.5 5.5 0 1 0 8.5 3M12.6 12.6L16.5 16.5",
        R.string.icon_search,
    )
    val More = SimplifyIcon(
        "M11.5 4.5A1.5 1.5 0 1 1 8.5 4.5A1.5 1.5 0 1 1 11.5 4.5" +
            "M11.5 10A1.5 1.5 0 1 1 8.5 10A1.5 1.5 0 1 1 11.5 10" +
            "M11.5 15.5A1.5 1.5 0 1 1 8.5 15.5A1.5 1.5 0 1 1 11.5 15.5",
        R.string.icon_more, filled = true,
    )
    val Plus = SimplifyIcon("M10 4.5V15.5M4.5 10H15.5", R.string.icon_add)
    val Edit = SimplifyIcon(
        "M3.5 16.5L4.3 12.9L13 4.2L15.8 7L7.1 15.7ZM11.2 6L14 8.8",
        R.string.icon_edit,
    )
    val Trash = SimplifyIcon(
        "M4.5 6H15.5M8 6V4.5H12V6M6 6L6.8 16.5H13.2L14 6M9 9V13.5M11 9V13.5",
        R.string.icon_uninstall,
    )
    val Remove = SimplifyIcon("M5 5L15 15M15 5L5 15", R.string.icon_remove)
    val Info = SimplifyIcon(
        "M10 3A7 7 0 1 0 10 17A7 7 0 1 0 10 3M10 9V13.5M10 6.5V6.6",
        R.string.icon_info,
    )
    val Folder = SimplifyIcon("M3 15.5V5.5H8.5L10 7.5H17V15.5Z", R.string.icon_folder)
    val Widgets = SimplifyIcon(
        "M3.5 3.5H8.5V8.5H3.5ZM11.5 3.5H16.5V8.5H11.5ZM3.5 11.5H8.5V16.5H3.5ZM11.5 11.5H16.5V16.5H11.5Z",
        R.string.icon_widgets,
    )
    val Settings = SimplifyIcon(
        "M10 7.5A2.5 2.5 0 1 0 10 12.5A2.5 2.5 0 1 0 10 7.5" +
            "M10 2.5V4.5M10 15.5V17.5M2.5 10H4.5M15.5 10H17.5" +
            "M4.7 4.7L6.1 6.1M13.9 13.9L15.3 15.3M4.7 15.3L6.1 13.9M13.9 6.1L15.3 4.7",
        R.string.icon_settings,
    )
    val Home = SimplifyIcon("M3.5 9L10 3.5L16.5 9V16.5H12V12H8V16.5H3.5Z", R.string.icon_home)
    val Lock = SimplifyIcon(
        "M5 9H15V16.5H5ZM7 9V6.5A3 3 0 0 1 13 6.5V9",
        R.string.icon_lock,
    )
    val Apps = SimplifyIcon(
        "M5 4A1 1 0 1 0 5 6A1 1 0 1 0 5 4M10 4A1 1 0 1 0 10 6A1 1 0 1 0 10 4M15 4A1 1 0 1 0 15 6A1 1 0 1 0 15 4" +
            "M5 9A1 1 0 1 0 5 11A1 1 0 1 0 5 9M10 9A1 1 0 1 0 10 11A1 1 0 1 0 10 9M15 9A1 1 0 1 0 15 11A1 1 0 1 0 15 9" +
            "M5 14A1 1 0 1 0 5 16A1 1 0 1 0 5 14M10 14A1 1 0 1 0 10 16A1 1 0 1 0 10 14M15 14A1 1 0 1 0 15 16A1 1 0 1 0 15 14",
        R.string.icon_apps,
    )
    val Hide = SimplifyIcon(
        "M2.5 10C4.5 6.5 7 5 10 5C13 5 15.5 6.5 17.5 10C15.5 13.5 13 15 10 15C7 15 4.5 13.5 2.5 10" +
            "M10 7.8A2.2 2.2 0 1 0 10 12.2A2.2 2.2 0 1 0 10 7.8M4 16L16 4",
        R.string.icon_hide,
    )
    val Cities = SimplifyIcon("M4 5H16M4 10H16M4 15H12", R.string.weather_cities)
    val Check = SimplifyIcon("M4.5 10.5L8 14L15.5 6", R.string.icon_added)
    val External = SimplifyIcon("M11.5 3.5H16.5V8.5M16.5 3.5L9.5 10.5M14 11.5V16.5H3.5V6H8.5", R.string.icon_open_external)
    val Resize = SimplifyIcon("M11.5 3.5H16.5V8.5M16.5 3.5L11 9M8.5 16.5H3.5V11.5M3.5 16.5L9 11", R.string.icon_resize)
}

private const val CLOUD = "M6 12H14.5A3 3 0 0 0 14.5 6A4.5 4.5 0 0 0 5.8 7.1A2.5 2.5 0 0 0 6 12Z"

val Condition.icon: SimplifyIcon get() = when (this) {
    Condition.Clear -> SimplifyIcon(
        "M10 6.5A3.5 3.5 0 1 1 10 13.5A3.5 3.5 0 1 1 10 6.5M10 2V3.5M10 16.5V18M2 10H3.5M16.5 10H18" +
            "M4.3 4.3L5.4 5.4M14.6 14.6L15.7 15.7M4.3 15.7L5.4 14.6M14.6 5.4L15.7 4.3",
        label,
    )
    Condition.Cloudy -> SimplifyIcon("M6 15H14.5A3 3 0 0 0 14.5 9A4.5 4.5 0 0 0 5.8 10.1A2.5 2.5 0 0 0 6 15Z", label)
    Condition.Fog -> SimplifyIcon("M4 7H16M3 10H17M4 13H16M6 16H14", label)
    Condition.Drizzle -> SimplifyIcon(CLOUD + "M7.5 15V15.5M10.5 15V15.5M13.5 15V15.5M9 17.5V18M12 17.5V18", label)
    Condition.Rain -> SimplifyIcon(CLOUD + "M7.5 14.5L6.5 17.5M10.5 14.5L9.5 17.5M13.5 14.5L12.5 17.5", label)
    Condition.Snow -> SimplifyIcon(CLOUD + "M7 15H7.01M10 15H10.01M13 15H13.01M8.5 17.5H8.51M11.5 17.5H11.51", label)
    Condition.Storm -> SimplifyIcon(CLOUD + "M11 13L9 15.5H11.5L9.5 18.5", label)
}

@Composable
fun Icon(
    icon: SimplifyIcon,
    modifier: Modifier = Modifier,
    tint: Color = Simplify.colors.text,
    size: Dp = Sizes.icon,
    decorative: Boolean = false,
    label: String? = null,
) {
    val path: Path = remember(icon.path) { PathParser().parsePathString(icon.path).toPath() }
    val description = label ?: stringResource(icon.label)
    Canvas(
        modifier
            .size(size)
            .then(if (decorative) Modifier else Modifier.semantics { contentDescription = description })
    ) {
        val factor = this.size.minDimension / VIEWPORT
        scale(factor, factor, pivot = Offset.Zero) {
            if (icon.filled) drawPath(path, tint, style = Fill)
            else drawPath(path, tint, style = Stroke(STROKE, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
