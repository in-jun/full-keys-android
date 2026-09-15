package dev.injun.fullkeys.settings

import dev.injun.fullkeys.core.layout.KeyboardLayout
import dev.injun.fullkeys.core.layout.KeyboardLayouts
import java.util.Locale

/** The layout to draw: the one chosen, or the one the phone's locale points to. */
fun KeyboardSettings.layout(locale: Locale = Locale.getDefault()): KeyboardLayout =
    layoutId?.let(KeyboardLayouts::byId) ?: phoneLayout(locale)

/** The layout the phone's language and country point to. */
fun phoneLayout(locale: Locale = Locale.getDefault()): KeyboardLayout {
    // A locale with no three-letter language code has none to match on.
    val language = runCatching { locale.isO3Language }.getOrDefault("")
    return KeyboardLayouts.forLocale(language, locale.country)
}
