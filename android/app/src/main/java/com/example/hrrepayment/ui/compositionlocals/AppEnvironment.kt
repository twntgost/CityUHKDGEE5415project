package com.example.hrrepayment.ui.compositionlocals

import androidx.compose.runtime.staticCompositionLocalOf
import com.example.hrrepayment.core.i18n.Lang
import com.example.hrrepayment.core.i18n.Strings
import com.example.hrrepayment.ui.components.AvatarCache

/**
 * The bilingual catalogue for the current language.
 *
 * `staticCompositionLocalOf` is deliberate: switching language must recompose the
 * whole tree (every label changes at once), and static locals skip the read-tracking
 * bookkeeping that `compositionLocalOf` pays for on every one of the many reads.
 */
val LocalStrings = staticCompositionLocalOf { Strings(Lang.DEFAULT) }

/** The language itself, for the few places that branch on it (e.g. numerals, dates). */
val LocalLang = staticCompositionLocalOf { Lang.DEFAULT }

/** Shared portrait cache; provided once so every screen reuses the same bitmaps. */
val LocalAvatarCache = staticCompositionLocalOf { AvatarCache() }
