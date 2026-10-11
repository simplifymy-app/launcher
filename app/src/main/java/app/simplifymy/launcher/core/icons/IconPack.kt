package app.simplifymy.launcher.core.icons

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.Xml
import org.xmlpull.v1.XmlPullParser

data class IconPackInfo(val packageName: String, val label: String)

class IconPack(private val context: Context, val packageName: String) {
    private val resources: Resources = context.packageManager.getResourcesForApplication(packageName)
    private val mapping = HashMap<String, String>()
    private val backs = mutableListOf<String>()
    private var mask: String? = null
    private var upon: String? = null
    var scale: Float = 1f
        private set

    init {
        parse("appfilter") { parser ->
            when (parser.name) {
                "item" -> {
                    val component = parser.getAttributeValue(null, "component")
                    val drawable = parser.getAttributeValue(null, "drawable")
                    if (component != null && drawable != null) {
                        mapping.putIfAbsent(component.removePrefix("ComponentInfo{").removeSuffix("}"), drawable)
                    }
                }
                "iconback" -> for (i in 0 until parser.attributeCount) backs += parser.getAttributeValue(i)
                "iconmask" -> mask = parser.getAttributeValue(null, "img1")
                "iconupon" -> upon = parser.getAttributeValue(null, "img1")
                "scale" -> scale = parser.getAttributeValue(null, "factor")?.toFloatOrNull() ?: 1f
            }
        }
    }

    val styled: Boolean get() = backs.isNotEmpty() || mask != null || upon != null

    fun drawableName(component: String): String? = mapping[component]

    fun back(seed: Int): Drawable? = backs.takeIf { it.isNotEmpty() }?.let { load(it[Math.floorMod(seed, it.size)]) }
    fun mask(): Drawable? = mask?.let(::load)
    fun upon(): Drawable? = upon?.let(::load)

    @SuppressLint("DiscouragedApi")
    fun load(name: String): Drawable? {
        val id = resources.getIdentifier(name, "drawable", packageName)
        if (id == 0) return null
        return runCatching { resources.getDrawableForDensity(id, resources.displayMetrics.densityDpi, null) }.getOrNull()
    }

    fun allDrawables(): List<String> {
        val names = LinkedHashSet<String>()
        parse("drawable") { parser ->
            if (parser.name == "item") parser.getAttributeValue(null, "drawable")?.let(names::add)
        }
        if (names.isEmpty()) names += mapping.values
        return names.toList()
    }

    @SuppressLint("DiscouragedApi")
    private fun parse(file: String, onTag: (XmlPullParser) -> Unit) {
        val parser: XmlPullParser = runCatching {
            val id = resources.getIdentifier(file, "xml", packageName)
            if (id != 0) resources.getXml(id)
            else Xml.newPullParser().apply { setInput(resources.assets.open("$file.xml"), "UTF-8") }
        }.getOrNull() ?: return
        runCatching {
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) onTag(parser)
                event = parser.next()
            }
        }
    }

    companion object {
        private val ACTIONS = listOf(
            "org.adw.launcher.THEMES",
            "com.novalauncher.THEME",
            "com.teslacoilsw.launcher.THEME",
            "com.anddoes.launcher.THEME",
        )

        fun installed(context: Context): List<IconPackInfo> {
            val pm = context.packageManager
            return ACTIONS.flatMap { action ->
                pm.queryIntentActivities(Intent(action), PackageManager.GET_META_DATA)
            }.map { it.activityInfo.applicationInfo }
                .distinctBy { it.packageName }
                .map { IconPackInfo(it.packageName, pm.getApplicationLabel(it).toString()) }
                .sortedBy { it.label.lowercase() }
        }
    }
}
