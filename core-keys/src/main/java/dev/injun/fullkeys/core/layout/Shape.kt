package dev.injun.fullkeys.core.layout

import dev.injun.fullkeys.core.KeyId

/**
 * One row's worth of a key: [width] units from [x], in [row].
 *
 * A key that spans rows, such as the Enter key of an ISO keyboard, is one area per row,
 * so every shape is described the same way whether its keys are rectangles or not.
 */
data class Area(val x: Float, val row: Int, val width: Float) {
    val right: Float get() = x + width
}

/**
 * A key that is sent.
 *
 * What is printed on it normally comes from the chosen [KeyboardLayout]. [name] is printed
 * instead when the layout has nothing for the key, which is always the case for keys that
 * do not type a character: Tab, the modifiers, the arrows.
 */
data class KeyCap(
    val id: KeyId,
    val areas: List<Area>,
    val kind: KeyKind,
    val name: String? = null,
    /** Printed instead of [name] on a screen too narrow for it at a readable size. May break over two lines. */
    val shortName: String? = null,
)

/** Decides how a cap is drawn and which labels share a size; every kind is sent the same way. */
enum class KeyKind { CHARACTER, MODIFIER, FUNCTION, NAVIGATION, SPACE }

/**
 * Row heights come from settings. The function row is kept slimmer than the rest
 * because it is reached for, not typed on, and the height it saves is height of the
 * screen that stays visible.
 */
enum class RowSize { SLIM, REGULAR }

/** Keys and where they are. */
data class Shape(val rows: List<RowSize>, val keys: List<KeyCap>)

/** The physical keyboard families, which differ in keys and in the shape of Enter. */
enum class ShapeId { ANSI, ISO, ABNT2, JIS }
