package app.simplifymy.launcher.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

object Space {
    val gutter = 4.dp
    val base = 8.dp
    val toolbarInset = 12.dp
    val rowGap = 16.dp
    val screen = 20.dp
    val section = 22.dp
    val hitTarget = 44.dp
    val settingsRow = 56.dp
}

enum class CornerRadius(val step: Int) { XS(2), S(4), M(6), L(14) }

object Radius {
    val control = RoundedCornerShape(4.dp)
    val card = RoundedCornerShape(6.dp)
    val sheet = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = 0.dp, bottomEnd = 0.dp)
    val pill = RoundedCornerShape(percent = 50)
    fun content(radius: CornerRadius) = RoundedCornerShape(radius.step.dp)
}

object Sizes {
    val hairline = 1.dp
    val playBlock = 34.dp
    val button = 34.dp
    val dropdown = 38.dp
    val toggleTrackWidth = 44.dp
    val toggleTrackHeight = 26.dp
    val toggleKnob = 20.dp
    val recordFab = 56.dp
    val recordFabSmall = 48.dp
    val transportPrimary = 76.dp
    val transportSide = 64.dp
    val playerButton = 48.dp
    val fullscreenAction = 23.dp
    val icon = 20.dp
}

object WaveHeight {
    val strip = 20.dp
    val card = 26.dp
    val player = 48.dp
    val stage = 200.dp
}
