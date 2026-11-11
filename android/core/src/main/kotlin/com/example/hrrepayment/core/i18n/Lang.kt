package com.example.hrrepayment.core.i18n

import java.util.Locale

/**
 * UI / content language.
 *
 * The choice is stored once per device (not per save slot) so switching the
 * language never touches a run. `core` stays Android-free, therefore the whole
 * catalogue lives in Kotlin rather than in `res/values-*`.
 */
enum class Lang(val code: String) {
    ZH("zh"),
    EN("en"),
    ;

    /** The language the “switch language” button flips to. */
    val other: Lang get() = if (this == ZH) EN else ZH

    companion object {
        val DEFAULT: Lang = ZH

        /** Tolerant parse: anything that is not English falls back to Chinese. */
        fun from(code: String?): Lang =
            if (code != null && code.trim().lowercase(Locale.ROOT).startsWith("en")) EN else ZH
    }
}

/**
 * One key resolved in one language.
 *
 * Every [Key] carries both translations inline, so a missing translation is a
 * compile error instead of a blank label at runtime.
 */
class Strings(val lang: Lang) {

    operator fun get(key: Key): String = if (lang == Lang.ZH) key.zh else key.en

    /** `String.format` with a fixed locale; UI text must not depend on device locale. */
    fun format(key: Key, vararg args: Any?): String =
        String.format(Locale.ROOT, get(key), *args)

    /** `1234.56` + currency word, e.g. `1234.56 元` / `1234.56 CNY`. */
    fun money(key: Key, amount: String): String = format(key, amount)

    companion object {
        fun of(lang: Lang): Strings = Strings(lang)
    }
}
