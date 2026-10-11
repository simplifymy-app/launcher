package app.simplifymy.launcher.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.simplifymy.launcher.R
import app.simplifymy.launcher.ui.theme.Radius
import app.simplifymy.launcher.ui.theme.RoleText
import app.simplifymy.launcher.ui.theme.Simplify
import app.simplifymy.launcher.ui.theme.Sizes
import app.simplifymy.launcher.ui.theme.Space
import app.simplifymy.launcher.util.AppIcon

private const val MASK_SCALE = 108f / 72f

@Composable
fun AppIconTile(icon: AppIcon, size: Dp, modifier: Modifier = Modifier, corner: Dp = 15.dp) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(colorResource(icon.background))
            .border(Sizes.hairline, Simplify.colors.hairline, RoundedCornerShape(corner)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(size * MASK_SCALE),
            colorFilter = ColorFilter.tint(Color.White),
        )
    }
}

@Composable
fun AppIconPicker(selected: AppIcon, onSelect: (AppIcon) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = Space.screen, vertical = Space.base),
        verticalArrangement = Arrangement.spacedBy(Space.rowGap),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.rowGap),
        ) {
            AppIconTile(selected, size = 52.dp)
            Column(Modifier.weight(1f)) {
                RoleText(stringResource(selected.label), Simplify.type.body)
                RoleText(
                    stringResource(R.string.app_icon_hint),
                    Simplify.type.meta,
                    color = Simplify.colors.textMuted,
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            AppIcon.entries.forEach { icon ->
                val active = icon == selected
                Box(
                    Modifier
                        .size(Space.hitTarget)
                        .pressable({ onSelect(icon) }, shape = Radius.pill),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(if (active) 26.dp else 30.dp)
                            .clip(Radius.pill)
                            .background(colorResource(icon.background))
                            .border(Sizes.hairline, Simplify.colors.hairline, Radius.pill)
                    )
                    if (active) {
                        Box(
                            Modifier
                                .size(Space.hitTarget - 6.dp)
                                .clip(Radius.pill)
                                .border(2.dp, Simplify.colors.text, Radius.pill)
                        )
                    }
                }
            }
        }
    }
}
