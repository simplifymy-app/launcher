package app.simplifymy.launcher.ui.launcher

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.simplifymy.launcher.AppContainer
import app.simplifymy.launcher.container
import app.simplifymy.launcher.core.apps.AppInfo
import app.simplifymy.launcher.core.apps.AppKey
import app.simplifymy.launcher.core.badges.Badges
import app.simplifymy.launcher.core.icons.IconShapeOutline
import app.simplifymy.launcher.core.settings.BadgeStyle
import app.simplifymy.launcher.core.settings.FolderPreview
import app.simplifymy.launcher.core.workspace.ItemEntity
import app.simplifymy.launcher.core.workspace.ItemType
import app.simplifymy.launcher.feature.home.HomeState
import app.simplifymy.launcher.ui.component.Icon
import app.simplifymy.launcher.ui.component.Icons
import app.simplifymy.launcher.ui.component.pressable
import app.simplifymy.launcher.ui.theme.Radius
import app.simplifymy.launcher.ui.theme.RoleText
import app.simplifymy.launcher.ui.theme.Simplify
import app.simplifymy.launcher.ui.theme.Space
import app.simplifymy.launcher.ui.theme.States
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val BaseIcon = 48.dp

val LocalLabelColor = staticCompositionLocalOf { Color.White }

val InkOnDark = Color(0xFFEDEDEF)
val InkOnLight = Color(0xFF131315)

fun iconSize(percent: Int): Dp = BaseIcon * (percent / 100f)

@Composable
fun rememberIcon(vararg keys: Any?, cached: (AppContainer) -> ImageBitmap? = { null }, load: (AppContainer) -> ImageBitmap?): State<ImageBitmap?> {
    val container = LocalContext.current.container
    val version by container.apps.version.collectAsState()
    val settings by container.settings.settings.collectAsState()
    return produceState(cached(container), *keys, version, settings.iconShape, settings.iconPack) {
        value = withContext(Dispatchers.IO) { load(container) }
    }
}

@Composable
fun rememberAppIcon(key: AppKey?, ref: String? = null): State<ImageBitmap?> =
    rememberIcon(key, ref, cached = { c -> key?.let { c.icons.cachedApp(it, ref) } }) { c -> key?.let { c.icons.app(it, ref) } }

@Composable
fun rememberItemIcon(item: ItemEntity, ref: String?): State<ImageBitmap?> =
    rememberIcon(
        item.app, item.shortcutId, ref,
        cached = { c -> item.app?.let(AppKey::parse)?.let { c.icons.cachedApp(it, ref) } },
    ) { c ->
        when (item.type) {
            ItemType.APP -> item.app?.let(AppKey::parse)?.let { c.icons.app(it, ref) }
            ItemType.SHORTCUT -> c.apps.pinnedShortcut(item.packageName.orEmpty(), item.shortcutId.orEmpty(), item.user)
                ?.let { c.icons.shortcut(it, ref) }
                ?: ref?.let(c.icons::ref)
                ?: c.apps.apps.value.firstOrNull { it.packageName == item.packageName }?.let { c.icons.app(it.key) }
            else -> null
        }
    }

@Composable
fun IconImage(bitmap: ImageBitmap?, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size)) {
        if (bitmap != null) {
            Image(BitmapPainter(bitmap), contentDescription = null, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
fun IconCell(
    label: String,
    showLabel: Boolean,
    twoLines: Boolean,
    modifier: Modifier = Modifier,
    labelColor: Color = LocalLabelColor.current,
    icon: @Composable BoxScope.() -> Unit,
) {
    Column(
        modifier.semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center, content = icon)
        if (showLabel) {
            RoleText(
                label,
                Simplify.type.label,
                color = labelColor,
                maxLines = if (twoLines) 2 else 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp),
            )
        }
    }
}

@Composable
fun Badge(packageName: String?, user: Long, style: BadgeStyle, iconSize: Dp) {
    if (style == BadgeStyle.None || packageName == null) return
    val counts by Badges.all.collectAsState()
    counts[Badges.key(packageName, user)]?.let { BadgeMark(it, style, iconSize) }
}

@Composable
fun BadgeMark(count: Int, style: BadgeStyle, iconSize: Dp) {
    if (count <= 0) return
    Box(Modifier.size(iconSize), contentAlignment = Alignment.TopEnd) {
        when (style) {
            BadgeStyle.Dot -> Box(
                Modifier.offset(x = 2.dp, y = (-2).dp).size(10.dp).clip(Radius.pill).background(Simplify.colors.accent)
            )
            BadgeStyle.Count -> Box(
                Modifier
                    .offset(x = 6.dp, y = (-4).dp)
                    .defaultMinSize(minWidth = 18.dp)
                    .height(18.dp)
                    .clip(Radius.pill)
                    .background(Simplify.colors.accent)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                RoleText(if (count > 99) "99+" else "$count", Simplify.type.meta, color = Simplify.colors.onAccent, maxLines = 1)
            }
            BadgeStyle.None -> Unit
        }
    }
}

private fun ItemEntity.badgeKey(): String? {
    val key = app?.let(AppKey::parse)
    val pkg = key?.component?.packageName ?: packageName ?: return null
    return Badges.key(pkg, key?.user ?: user)
}

@Composable
fun ItemIcon(item: ItemEntity, state: HomeState, size: Dp, badges: Boolean = true) {
    val bitmap by rememberItemIcon(item, state.iconRef(item))
    IconImage(bitmap, size)
    if (badges) {
        val key = item.app?.let(AppKey::parse)
        Badge(key?.component?.packageName ?: item.packageName, key?.user ?: item.user, state.settings.badgeStyle, size)
    }
}

@Composable
fun AppIcon(app: AppInfo, state: HomeState, size: Dp) {
    val bitmap by rememberAppIcon(app.key, state.overrides[app.key.flat]?.icon)
    IconImage(bitmap, size)
    Badge(app.packageName, app.key.user, state.settings.badgeStyle, size)
}

@Composable
fun FolderIcon(children: List<ItemEntity>, state: HomeState, size: Dp, cover: Boolean, highlighted: Boolean = false) {
    if (cover && children.isNotEmpty()) {
        Box(Modifier.size(size)) {
            ItemIcon(children.first(), state, size, badges = false)
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(size * 0.38f)
                    .clip(Radius.pill)
                    .background(Simplify.colors.surfaceAlt),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Folder, size = size * 0.24f, tint = Simplify.colors.text, decorative = true)
            }
        }
        return
    }
    val shape = IconShapeOutline(state.settings.iconShape)
    val tile = if (highlighted) androidx.compose.ui.graphics.lerp(Simplify.colors.surfaceAlt, Simplify.colors.accent, States.SELECTED) else Simplify.colors.surfaceAlt.copy(alpha = 0.92f)
    Box(Modifier.size(size).clip(shape).background(tile), contentAlignment = Alignment.Center) {
        val preview = children.take(4)
        when (state.settings.folderPreview) {
            FolderPreview.Grid -> Column(verticalArrangement = Arrangement.spacedBy(size * 0.06f)) {
                preview.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(size * 0.06f)) {
                        row.forEach { ItemIcon(it, state, size * 0.34f, badges = false) }
                    }
                }
            }
            FolderPreview.Stack -> Box(Modifier.size(size * 0.7f)) {
                preview.take(3).reversed().forEachIndexed { i, child ->
                    val step = (2 - i - (3 - preview.take(3).size)).coerceAtLeast(0)
                    Box(Modifier.offset(x = size * 0.08f * step, y = size * 0.08f * step)) {
                        ItemIcon(child, state, size * 0.52f, badges = false)
                    }
                }
            }
            FolderPreview.Line -> Row(horizontalArrangement = Arrangement.spacedBy(size * 0.03f)) {
                preview.take(3).forEach { ItemIcon(it, state, size * 0.28f, badges = false) }
            }
        }
    }
    val counts by Badges.all.collectAsState()
    BadgeMark(children.sumOf { c -> c.badgeKey()?.let { counts[it] } ?: 0 }, state.settings.badgeStyle, size)
}

@Composable
fun IconRow(icon: ImageBitmap?, label: String, onClick: () -> Unit, trailing: @Composable (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(Space.settingsRow)
            .pressable(onClick, shape = RoundedCornerShape(0.dp))
            .padding(horizontal = Space.screen),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.rowGap),
    ) {
        if (icon != null) IconImage(icon, 32.dp) else Box(Modifier.size(32.dp).clip(Radius.pill).background(Simplify.colors.surfaceAlt))
        RoleText(label, Simplify.type.body, modifier = Modifier.weight(1f), maxLines = 1)
        trailing?.invoke()
    }
}
