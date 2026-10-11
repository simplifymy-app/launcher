package app.simplifymy.launcher.core.icons

import android.content.Context
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Process
import android.util.LruCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import app.simplifymy.launcher.core.apps.AppKey
import app.simplifymy.launcher.core.apps.AppRepository
import app.simplifymy.launcher.core.settings.IconShape
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

private const val SIZE = 192

object IconRef {
    fun pack(packageName: String, drawable: String) = "pack:$packageName:$drawable"
}

class IconLoader(private val context: Context, private val apps: AppRepository) {
    private val cache = LruCache<String, ImageBitmap>(400)
    private val packs = HashMap<String, IconPack?>()
    private val density = context.resources.displayMetrics.densityDpi

    @Volatile var shape: IconShape = IconShape.System
        private set
    @Volatile var packName: String? = null
        private set

    fun configure(shape: IconShape, pack: String?) {
        if (shape == this.shape && pack == packName) return
        this.shape = shape
        this.packName = pack
        cache.evictAll()
    }

    fun invalidate() = cache.evictAll()

    fun pack(name: String?): IconPack? = name?.let {
        synchronized(packs) { packs.getOrPut(it) { runCatching { IconPack(context, it) }.getOrNull() } }
    }

    fun cachedApp(key: AppKey, override: String? = null): ImageBitmap? = cache.get(appCacheKey(key, override))

    private fun appCacheKey(key: AppKey, override: String?) = "${key.flat}|$override"

    fun app(key: AppKey, override: String? = null): ImageBitmap? {
        val cacheKey = appCacheKey(key, override)
        cache.get(cacheKey)?.let { return it }
        val bitmap = override?.let(::fromRef) ?: fromPack(key) ?: run {
            val drawable = apps.activity(key)?.getIcon(density) ?: return null
            render(drawable)
        }
        val badged = badge(bitmap, key.user)
        return badged.asImageBitmap().also { cache.put(cacheKey, it) }
    }

    fun shortcut(info: ShortcutInfo, override: String? = null): ImageBitmap? {
        val cacheKey = "sc|${info.`package`}|${info.id}|${apps.serial(info.userHandle)}|$override"
        cache.get(cacheKey)?.let { return it }
        val bitmap = override?.let(::fromRef) ?: run {
            val drawable = runCatching { apps.launcherApps.getShortcutIconDrawable(info, density) }.getOrNull() ?: return null
            render(drawable)
        }
        return bitmap.asImageBitmap().also { cache.put(cacheKey, it) }
    }

    fun ref(ref: String): ImageBitmap? {
        cache.get(ref)?.let { return it }
        return fromRef(ref)?.asImageBitmap()?.also { cache.put(ref, it) }
    }

    private fun fromRef(ref: String): Bitmap? = when {
        ref.startsWith("pack:") -> {
            val (pkg, name) = ref.removePrefix("pack:").split(':', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
            pack(pkg)?.load(name)?.let(::plain)
        }
        else -> null
    }

    private fun fromPack(key: AppKey): Bitmap? {
        val pack = pack(packName) ?: return null
        pack.drawableName(key.component.flattenToString())?.let { name -> pack.load(name)?.let { return plain(it) } }
        if (!pack.styled) return null
        val original = apps.activity(key)?.getIcon(density) ?: return null
        return styled(pack, original, key.component.packageName.hashCode())
    }

    private fun styled(pack: IconPack, icon: Drawable, seed: Int): Bitmap {
        val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        pack.back(seed)?.let { it.setBounds(0, 0, SIZE, SIZE); it.draw(canvas) }
        val inner = (SIZE * pack.scale).toInt()
        val offset = (SIZE - inner) / 2
        val layer = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        Canvas(layer).also { c ->
            icon.setBounds(offset, offset, offset + inner, offset + inner)
            icon.draw(c)
            pack.mask()?.let { mask ->
                val masked = plain(mask)
                c.drawBitmap(masked, 0f, 0f, Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) })
            }
        }
        canvas.drawBitmap(layer, 0f, 0f, null)
        pack.upon()?.let { it.setBounds(0, 0, SIZE, SIZE); it.draw(canvas) }
        return out
    }

    private fun plain(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap.width == SIZE) return drawable.bitmap
        val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, SIZE, SIZE)
        drawable.draw(Canvas(out))
        return out
    }

    private fun render(drawable: Drawable): Bitmap {
        if (shape == IconShape.System || drawable !is AdaptiveIconDrawable) return plain(drawable)
        val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.clipPath(shapePath(shape, SIZE.toFloat()))

        val inset = SIZE / 4
        listOfNotNull(drawable.background, drawable.foreground).forEach {
            it.setBounds(-inset, -inset, SIZE + inset, SIZE + inset)
            it.draw(canvas)
        }
        return out
    }

    private fun badge(bitmap: Bitmap, user: Long): Bitmap {
        val handle = apps.user(user) ?: return bitmap
        if (handle == Process.myUserHandle()) return bitmap
        val badged = context.packageManager.getUserBadgedIcon(BitmapDrawable(context.resources, bitmap), handle)
        return plain(badged)
    }
}

fun shapePath(shape: IconShape, size: Float): Path = Path().apply {
    val r = RectF(0f, 0f, size, size)
    when (shape) {
        IconShape.Circle -> addOval(r, Path.Direction.CW)
        IconShape.System -> addPath(systemMask, Matrix().apply { setScale(size / 100f, size / 100f) })
        IconShape.Square -> addRect(r, Path.Direction.CW)
        IconShape.Rounded -> addRoundRect(r, size * 0.22f, size * 0.22f, Path.Direction.CW)
        IconShape.Teardrop -> {
            val big = size / 2
            val small = size * 0.12f
            addRoundRect(r, floatArrayOf(big, big, big, big, small, small, big, big), Path.Direction.CW)
        }
        IconShape.Squircle -> {
            val half = size / 2
            val steps = 96
            for (i in 0..steps) {
                val t = 2 * Math.PI * i / steps
                val c = cos(t); val s = sin(t)
                val x = half + half * sign(c) * abs(c).pow(2.0 / 4.0)
                val y = half + half * sign(s) * abs(s).pow(2.0 / 4.0)
                if (i == 0) moveTo(x.toFloat(), y.toFloat()) else lineTo(x.toFloat(), y.toFloat())
            }
            close()
        }
    }
}

private val systemMask: Path by lazy {
    AdaptiveIconDrawable(ColorDrawable(), ColorDrawable()).iconMask
}

class IconShapeOutline(private val shape: IconShape) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = shapePath(shape, size.minDimension).asComposePath()
        return Outline.Generic(path)
    }
}
