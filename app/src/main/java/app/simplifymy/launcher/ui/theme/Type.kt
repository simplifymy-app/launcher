package app.simplifymy.launcher.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.simplifymy.launcher.R

enum class InterfaceFamily { SpaceGrotesk, Outfit, IBMPlexSans, System }

enum class TextScale(val factor: Float) { S(0.90f), M(1.00f), L(1.12f), XL(1.26f) }

@OptIn(ExperimentalTextApi::class)
private fun variable(resId: Int, vararg weights: Int) = FontFamily(
    weights.map { w ->
        Font(resId, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    }
)

private val WEIGHTS = intArrayOf(400, 500, 600, 700)

private val SpaceGrotesk by lazy { variable(R.font.space_grotesk, *WEIGHTS) }
private val Outfit by lazy { variable(R.font.outfit, *WEIGHTS) }
private val IBMPlexSans by lazy { variable(R.font.ibm_plex_sans, *WEIGHTS) }

val PlexMono by lazy {
    FontFamily(
        Font(R.font.ibm_plex_mono_regular, FontWeight.W400),
        Font(R.font.ibm_plex_mono_medium, FontWeight.W500),
    )
}

fun InterfaceFamily.forScript(cyrillic: Boolean): InterfaceFamily =
    if (cyrillic && (this == InterfaceFamily.SpaceGrotesk || this == InterfaceFamily.Outfit)) {
        InterfaceFamily.IBMPlexSans
    } else {
        this
    }

fun InterfaceFamily.resolve(): FontFamily = when (this) {
    InterfaceFamily.SpaceGrotesk -> SpaceGrotesk
    InterfaceFamily.Outfit -> Outfit
    InterfaceFamily.IBMPlexSans -> IBMPlexSans
    InterfaceFamily.System -> FontFamily.SansSerif
}

@Immutable
data class SimplifyTypography(
    val display: TextStyle,
    val headline: TextStyle,
    val title: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
    val value: TextStyle,
    val meta: TextStyle,
    val section: TextStyle,
)

private const val TABULAR = "tnum"

private fun sans(family: FontFamily, size: TextUnit, weight: Int, tracking: Float = 0f) = TextStyle(
    fontFamily = family,
    fontSize = size,
    fontWeight = FontWeight(weight),
    letterSpacing = tracking.em,
)

private fun mono(size: TextUnit, weight: Int, tracking: Float = 0f) = TextStyle(
    fontFamily = PlexMono,
    fontSize = size,
    fontWeight = FontWeight(weight),
    letterSpacing = tracking.em,
    fontFeatureSettings = TABULAR,
)

fun simplifyTypography(family: InterfaceFamily, scale: TextScale): SimplifyTypography {
    val f = family.resolve()
    val s = scale.factor
    return SimplifyTypography(
        display = sans(f, (27 * s).sp, 600, -0.03f),
        headline = sans(f, (22 * s).sp, 600, -0.02f),
        title = sans(f, (19 * s).sp, 600, -0.02f),
        body = sans(f, (14.5f * s).sp, 400),
        label = sans(f, (14 * s).sp, 500),
        value = mono((13 * s).sp, 400),
        meta = mono((11.5f * s).sp, 400),
        section = mono((10.5f * s).sp, 500, 0.09f),
    )
}

fun String.sectionCase(): String = uppercase()
