package app.simplifymy.launcher.ui.launcher

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.toSize

fun pageLabel(current: Int, count: Int): String = "%02d / %02d".format(current + 1, count)

fun LayoutCoordinates.rootRect(): Rect = Rect(positionInRoot(), size.toSize())

class BoundsRef(var rect: Rect = Rect.Zero)

fun Modifier.trackBounds(ref: BoundsRef): Modifier = onGloballyPositioned { ref.rect = it.rootRect() }
