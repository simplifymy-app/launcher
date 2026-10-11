package app.simplifymy.launcher.core.search

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.net.Uri
import android.provider.ContactsContract
import app.simplifymy.launcher.core.apps.AppInfo
import app.simplifymy.launcher.core.apps.AppRepository
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.Normalizer

data class Contact(val name: String, val uri: Uri)

data class SearchResults(
    val apps: List<AppInfo> = emptyList(),
    val shortcuts: List<ShortcutInfo> = emptyList(),
    val contacts: List<Contact> = emptyList(),
    val calculation: String? = null,
) {
    val isEmpty: Boolean get() = apps.isEmpty() && shortcuts.isEmpty() && contacts.isEmpty() && calculation == null
}

class SearchEngine(private val context: Context, private val apps: AppRepository) {
    private var shortcutCache: Pair<Int, List<ShortcutInfo>>? = null

    fun search(query: String, candidates: List<AppInfo>, contacts: Boolean): SearchResults {
        val q = query.trim()
        if (q.isEmpty()) return SearchResults()
        val ranked = candidates
            .map { it to score(it.label, q) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
        return SearchResults(
            apps = ranked,
            shortcuts = shortcuts().filter { score(it.shortLabel?.toString().orEmpty(), q) >= 60 }.take(6),
            contacts = if (contacts) contacts(q) else emptyList(),
            calculation = Calculator.evaluate(q),
        )
    }

    private fun shortcuts(): List<ShortcutInfo> {
        val version = apps.version.value
        shortcutCache?.takeIf { it.first == version }?.let { return it.second }
        return apps.allShortcuts().also { shortcutCache = version to it }
    }

    fun hasContactsPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    private fun contacts(q: String): List<Contact> {
        if (!hasContactsPermission()) return emptyList()
        val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(q))
        val projection = arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.LOOKUP_KEY, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
        return runCatching {
            context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
                buildList {
                    while (c.moveToNext() && size < 5) {
                        val lookup = ContactsContract.Contacts.getLookupUri(c.getLong(0), c.getString(1))
                        add(Contact(c.getString(2).orEmpty(), lookup))
                    }
                }
            }
        }.getOrNull().orEmpty()
    }

    companion object {
        fun score(label: String, query: String): Int {
            val l = fold(label)
            val q = fold(query)
            if (q.isEmpty()) return 0
            return when {
                l.startsWith(q) -> 100
                l.split(' ', '-', '_', '.').any { it.startsWith(q) } -> 80
                l.contains(q) -> 60
                initials(l).startsWith(q) -> 50
                subsequence(l, q) -> 30
                else -> 0
            }
        }

        private fun fold(s: String) =
            Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

        private fun initials(s: String) = s.split(' ', '-', '_').filter { it.isNotEmpty() }.joinToString("") { it.take(1) }

        private fun subsequence(text: String, q: String): Boolean {
            var i = 0
            for (ch in text) if (i < q.length && ch == q[i]) i++
            return i == q.length && q.length >= 2
        }
    }
}

object Calculator {
    fun evaluate(input: String): String? {
        val expr = input.replace('×', '*').replace('÷', '/').replace('−', '-').replace(',', '.').replace(" ", "")
        if (!expr.any { it in "+-*/^%" } || !expr.any(Char::isDigit)) return null
        if (!expr.all { it.isDigit() || it in "+-*/^%.()" }) return null
        return runCatching {
            val parser = Parser(expr)
            val value = parser.expression()
            if (parser.pos != expr.length) return null
            value.round(MathContext(12, RoundingMode.HALF_UP)).stripTrailingZeros().toPlainString()
        }.getOrNull()
    }

    private class Parser(val s: String) {
        var pos = 0
        private val mc = MathContext(20, RoundingMode.HALF_UP)

        fun expression(): BigDecimal {
            var v = term()
            while (pos < s.length && s[pos] in "+-") {
                v = if (s[pos++] == '+') v.add(term(), mc) else v.subtract(term(), mc)
            }
            return v
        }

        private fun term(): BigDecimal {
            var v = power()
            while (pos < s.length && s[pos] in "*/%") {
                v = when (s[pos++]) {
                    '*' -> v.multiply(power(), mc)
                    '/' -> v.divide(power(), mc)
                    else -> v.remainder(power(), mc)
                }
            }
            return v
        }

        private fun power(): BigDecimal {
            val base = unary()
            if (pos < s.length && s[pos] == '^') {
                pos++
                val exponent = power()
                return BigDecimal(Math.pow(base.toDouble(), exponent.toDouble()), mc)
            }
            return base
        }

        private fun unary(): BigDecimal {
            if (pos < s.length && s[pos] == '-') { pos++; return unary().negate() }
            if (pos < s.length && s[pos] == '+') { pos++; return unary() }
            return atom()
        }

        private fun atom(): BigDecimal {
            if (pos < s.length && s[pos] == '(') {
                pos++
                val v = expression()
                require(pos < s.length && s[pos] == ')')
                pos++
                return v
            }
            val start = pos
            while (pos < s.length && (s[pos].isDigit() || s[pos] == '.')) pos++
            return BigDecimal(s.substring(start, pos))
        }
    }
}
