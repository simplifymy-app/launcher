package app.simplifymy.launcher.feature.home

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import kotlin.math.abs

private enum class PressResult { Up, Cancel }

fun Modifier.itemGestures(
    key: Any?,
    enabled: Boolean = true,
    onTap: () -> Unit,
    onLongPress: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: (cancelled: Boolean) -> Unit,
): Modifier = composed {
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val tap by rememberUpdatedState(onTap)
    val longPress by rememberUpdatedState(onLongPress)
    val drag by rememberUpdatedState(onDrag)
    val dragEnd by rememberUpdatedState(onDragEnd)
    this
        .onGloballyPositioned { coords = it }
        .pointerInput(key, enabled) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val result = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull PressResult.Cancel
                        if (!change.pressed) return@withTimeoutOrNull if (change.isConsumed) PressResult.Cancel else PressResult.Up
                        if (change.isConsumed || event.changes.count { it.pressed } > 1) return@withTimeoutOrNull PressResult.Cancel
                        if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) return@withTimeoutOrNull PressResult.Cancel
                    }
                    @Suppress("UNREACHABLE_CODE") PressResult.Cancel
                }
                when (result) {
                    PressResult.Up -> {
                        currentEvent.changes.forEach { it.consume() }
                        tap()
                    }
                    PressResult.Cancel -> Unit
                    null -> {
                        val origin = coords ?: return@awaitEachGesture
                        longPress(origin.localToRoot(down.position))
                        var ended = false
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                val released = !change.pressed
                                change.consume()
                                if (released) { ended = true; dragEnd(false); break }
                                coords?.let { drag(it.localToRoot(change.position)) }
                            }
                        } finally {
                            if (!ended) dragEnd(true)
                        }
                    }
                }
            }
        }
}

enum class Swipe { Up, Down }

class DesktopGestureCallbacks(
    val active: () -> Boolean,
    val onSwipeStart: (Swipe, Offset) -> Boolean,
    val onSwipeDrag: (Offset) -> Unit,
    val onSwipeEnd: (velocity: Offset) -> Unit,
    val onPinchIn: () -> Unit,
    val onPinchOut: () -> Unit,
    val onTwoFingerUp: () -> Unit,
    val onTwoFingerDown: () -> Unit,
)

fun Modifier.desktopGestures(callbacks: DesktopGestureCallbacks): Modifier = pointerInput(callbacks) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (!callbacks.active()) return@awaitEachGesture
        val slop = viewConfiguration.touchSlop
        var startSpread = 0f
        var startCentroid = Offset.Zero
        var multi = false
        val tracker = VelocityTracker()
        tracker.addPosition(down.uptimeMillis, down.position)
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (!callbacks.active()) return@awaitEachGesture
            val pressed = event.changes.filter { it.pressed }
            if (pressed.isEmpty()) return@awaitEachGesture
            if (pressed.size >= 2) {
                val a = pressed[0].position
                val b = pressed[1].position
                val spread = (a - b).getDistance()
                val centroid = (a + b) / 2f
                if (!multi) { multi = true; startSpread = spread; startCentroid = centroid; continue }
                val spreadChange = spread - startSpread
                val move = centroid - startCentroid
                when {
                    abs(spreadChange) > slop * 4 && abs(spreadChange) > abs(move.y) -> {
                        event.changes.forEach { it.consume() }
                        if (spreadChange < 0) callbacks.onPinchIn() else callbacks.onPinchOut()
                        consumeRest(); return@awaitEachGesture
                    }
                    abs(move.y) > slop * 3 && abs(move.y) > abs(move.x) -> {
                        event.changes.forEach { it.consume() }
                        if (move.y < 0) callbacks.onTwoFingerUp() else callbacks.onTwoFingerDown()
                        consumeRest(); return@awaitEachGesture
                    }
                }
                continue
            }
            if (multi) continue
            val change = pressed.first()
            tracker.addPosition(change.uptimeMillis, change.position)
            val delta = change.position - down.position
            if (abs(delta.x) > slop && abs(delta.x) > abs(delta.y)) return@awaitEachGesture
            if (abs(delta.y) <= slop || abs(delta.y) <= abs(delta.x) * 1.5f) continue
            val swipe = if (delta.y < 0) Swipe.Up else Swipe.Down
            if (!callbacks.onSwipeStart(swipe, down.position)) {
                change.consume()
                consumeRest()
                return@awaitEachGesture
            }
            change.consume()
            callbacks.onSwipeDrag(delta)
            var last = change.position
            while (true) {
                val next = awaitPointerEvent(PointerEventPass.Initial)
                val c = next.changes.firstOrNull { it.id == change.id }
                if (c == null || !c.pressed) {
                    if (c != null) {
                        tracker.addPosition(c.uptimeMillis, c.position)
                        callbacks.onSwipeDrag(c.position - last)
                    }
                    callbacks.onSwipeEnd(tracker.calculateVelocity().let { Offset(it.x, it.y) })
                    return@awaitEachGesture
                }
                tracker.addPosition(c.uptimeMillis, c.position)
                callbacks.onSwipeDrag(c.position - last)
                last = c.position
                c.consume()
            }
        }
    }
}

private suspend fun AwaitPointerEventScope.consumeRest() {
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        event.changes.forEach { it.consume() }
        if (event.changes.none { it.pressed }) return
    }
}
