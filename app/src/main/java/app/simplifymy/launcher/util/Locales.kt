package app.simplifymy.launcher.util

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.icu.util.ULocale
import android.view.ContextThemeWrapper
import androidx.annotation.StringRes
import app.simplifymy.launcher.R
import java.util.Locale

enum class Language(val tag: String, @StringRes val label: Int) {
    System("", R.string.language_system),
    English("en", R.string.language_en),
    Ukrainian("uk", R.string.language_uk);

    val locale: Locale? get() = tag.takeIf { it.isNotEmpty() }?.let(Locale::forLanguageTag)
}

fun Locale.usesCyrillic(): Boolean =
    ULocale.addLikelySubtags(ULocale.forLocale(this)).script == "Cyrl"

object Locales {
    private val systemLocale: Locale
        get() = Resources.getSystem().configuration.locales[0]

    fun wrap(context: Context, language: Language): Context {
        val locale = language.locale ?: systemLocale
        Locale.setDefault(locale)
        val configuration = Configuration(context.resources.configuration).apply { setLocale(locale) }
        return ContextThemeWrapper(context, 0).apply { applyOverrideConfiguration(configuration) }
    }
}
