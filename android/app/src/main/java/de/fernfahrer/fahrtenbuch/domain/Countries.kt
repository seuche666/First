package de.fernfahrer.fahrtenbuch.domain

import java.text.Collator
import java.util.Locale

data class Country(val code: String, val name: String) {
    val flag: String get() = Countries.flag(code)
}

/** Alle Länder mit deutschen Namen, aus den Systemdaten von Java/Android. */
object Countries {
    private val german = Locale.GERMANY

    val all: List<Country> by lazy {
        val collator = Collator.getInstance(german)
        Locale.getISOCountries()
            .map { Country(it, name(it)) }
            .sortedWith { a, b -> collator.compare(a.name, b.name) }
    }

    fun name(code: String): String =
        Locale("", code).getDisplayCountry(german).ifBlank { code }

    /** Flaggen-Emoji aus dem Ländercode, z. B. "DE" → 🇩🇪. */
    fun flag(code: String): String {
        if (code.length != 2 || !code.all { it in 'A'..'Z' }) return "🏳"
        val first = Character.codePointAt(code, 0) - 'A'.code + 0x1F1E6
        val second = Character.codePointAt(code, 1) - 'A'.code + 0x1F1E6
        return String(Character.toChars(first)) + String(Character.toChars(second))
    }

    fun label(code: String): String = "${flag(code)} ${name(code)}"

    fun search(query: String): List<Country> {
        val q = query.trim()
        if (q.isEmpty()) return all
        return all.filter { it.name.contains(q, ignoreCase = true) || it.code.equals(q, ignoreCase = true) }
    }
}
