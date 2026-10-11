package app.simplifymy.launcher.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import app.simplifymy.launcher.ui.Localized
import app.simplifymy.launcher.ui.theme.Motion
import app.simplifymy.launcher.ui.theme.Radius
import app.simplifymy.launcher.ui.theme.RoleText
import app.simplifymy.launcher.ui.theme.Simplify
import app.simplifymy.launcher.ui.theme.Space
import app.simplifymy.launcher.ui.theme.enterTween
import app.simplifymy.launcher.ui.theme.exitTween

@Composable
fun Menu(expanded: Boolean, items: List<MenuItem>, onDismiss: () -> Unit) {
    if (!expanded) return
    Popup(
        popupPositionProvider = UnderAnchor,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
      Localized {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(enterTween(Motion.CHROME)),
            exit = fadeOut(exitTween(Motion.CHROME)),
        ) {
            Column(
                Modifier
                    .widthIn(min = 180.dp, max = 280.dp)
                    .width(IntrinsicSize.Max)
                    .clip(Radius.card)
                    .background(Simplify.colors.surfaceAlt)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = Space.base),
            ) {
                items.forEach { item ->
                    MenuLine(item) { item.onClick(); onDismiss() }
                }
            }
        }
      }
    }
}

private object UnderAnchor : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val room = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        val x = when (layoutDirection) {
            LayoutDirection.Ltr -> anchorBounds.right - popupContentSize.width
            LayoutDirection.Rtl -> anchorBounds.left
        }.coerceIn(0, room)
        val fitsBelow = anchorBounds.bottom + popupContentSize.height <= windowSize.height
        val y = if (fitsBelow) {
            anchorBounds.bottom
        } else {
            (anchorBounds.top - popupContentSize.height).coerceAtLeast(0)
        }
        return IntOffset(x, y)
    }
}

data class MenuItem(
    val label: String,
    val danger: Boolean = false,
    val value: String? = null,
    val valueOn: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
internal fun MenuLine(item: MenuItem, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(Space.hitTarget)
            .pressable(onClick, shape = RoundedCornerShape(0.dp))
            .padding(horizontal = Space.rowGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoleText(
            item.label,
            Simplify.type.label,
            color = if (item.danger) Simplify.colors.danger else Simplify.colors.text,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        item.value?.let {
            RoleText(
                it,
                Simplify.type.meta,
                color = if (item.valueOn) Simplify.colors.accent else Simplify.colors.textMuted,
                maxLines = 1,
                modifier = Modifier.padding(start = Space.rowGap),
            )
        }
    }
}
