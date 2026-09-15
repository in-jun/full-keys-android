package dev.injun.fullkeys.core.layout

import dev.injun.fullkeys.core.KeyId

/**
 * The physical keyboards: each family's 60% main block, turned into a gapless 75% board by
 * one set of rules.
 *
 * The main blocks are QMK's community 60% layouts (`60_ansi`, `60_iso`, `60_abnt2`,
 * `60_jis`): which keys each row holds and the standard keycap widths of the keys at its
 * ends. Extending them to 75% is the same for every family:
 *
 * - The standard function row of sixteen 1u keys goes on top: Escape, F1 to F12, Print
 *   Screen, Pause and Delete.
 * - A navigation column of 1u keys (Home, Page Up, Page Down, End) runs down the right
 *   edge, so the main block is fifteen units wide.
 * - In the Shift row, Up sits left of End; right Shift takes what the row leaves, and if
 *   that would be under 1u, left Shift gives up the difference.
 * - In the bottom row, Ctrl, Super and Alt keep their 1.25u on the left; every key right
 *   of the space bar is 1u, followed by the arrows; the space bar takes what is left.
 * - An Enter that spans two rows fills from the end of each row's keys to the navigation
 *   column.
 *
 * Applied to the ANSI and ISO main blocks these rules give exactly QMK's `75_ansi` and
 * `75_iso` layouts, which is what makes the JIS and ABNT2 boards they give trustworthy.
 */
object Shapes {

    const val COLUMNS = 16f

    /** Standard keycap widths, in units. */
    object Cap {
        const val U1 = 1f
        const val U1_25 = 1.25f
        const val U1_5 = 1.5f
        const val U1_75 = 1.75f
        const val U2 = 2f
        const val U2_25 = 2.25f
    }

    fun of(id: ShapeId): Shape = when (id) {
        ShapeId.ANSI -> ansi
        ShapeId.ISO -> iso
        ShapeId.ABNT2 -> abnt2
        ShapeId.JIS -> jis
    }

    private val ansi: Shape by lazy {
        build(
            MainBlock(
                numberRow = numberKeys + named(KeyId.BACKSPACE, "⌫", Cap.U2),
                topRow = listOf(named(KeyId.TAB, "Tab", Cap.U1_5)) + topLetters + character(KeyId.BACKSLASH, Cap.U1_5),
                homeRow = listOf(named(KeyId.CAPS_LOCK, "Caps", Cap.U1_75)) + homeLetters,
                tallEnter = false,
                leftShift = Cap.U2_25,
                shiftRow = bottomLetters,
            ),
        )
    }

    private val iso: Shape by lazy {
        build(
            MainBlock(
                numberRow = numberKeys + named(KeyId.BACKSPACE, "⌫", Cap.U2),
                topRow = listOf(named(KeyId.TAB, "Tab", Cap.U1_5)) + topLetters,
                homeRow = listOf(named(KeyId.CAPS_LOCK, "Caps", Cap.U1_75)) + homeLetters + character(KeyId.BACKSLASH),
                tallEnter = true,
                leftShift = Cap.U1_25,
                shiftRow = listOf(character(KeyId.INTL_BACKSLASH)) + bottomLetters,
            ),
        )
    }

    private val abnt2: Shape by lazy {
        build(
            MainBlock(
                numberRow = numberKeys + named(KeyId.BACKSPACE, "⌫", Cap.U2),
                topRow = listOf(named(KeyId.TAB, "Tab", Cap.U1_5)) + topLetters,
                homeRow = listOf(named(KeyId.CAPS_LOCK, "Caps", Cap.U1_75)) + homeLetters + character(KeyId.BACKSLASH),
                tallEnter = true,
                leftShift = Cap.U1_25,
                shiftRow = listOf(character(KeyId.INTL_BACKSLASH)) + bottomLetters + character(KeyId.RO),
            ),
        )
    }

    private val jis: Shape by lazy {
        build(
            MainBlock(
                numberRow = listOf(named(KeyId.GRAVE, "半/全")) + numberKeys.drop(1) +
                    character(KeyId.YEN) + named(KeyId.BACKSPACE, "⌫"),
                topRow = listOf(named(KeyId.TAB, "Tab", Cap.U1_5)) + topLetters,
                homeRow = listOf(named(KeyId.CAPS_LOCK, "英数", Cap.U1_75)) + homeLetters + character(KeyId.BACKSLASH),
                tallEnter = true,
                leftShift = Cap.U2_25,
                shiftRow = bottomLetters + character(KeyId.RO),
                besideSpaceLeft = listOf(named(KeyId.MUHENKAN, "無変換")),
                besideSpaceRight = listOf(named(KeyId.HENKAN, "変換"), named(KeyId.KATAKANA_HIRAGANA, "かな")),
            ),
        )
    }

    /** What differs between keyboard families: the keys of the 60% main block. */
    private class MainBlock(
        /** Ends at the navigation column. */
        val numberRow: List<Part>,
        /** Ends at the navigation column, or where a two-row Enter begins. */
        val topRow: List<Part>,
        /** Ends where Enter begins. */
        val homeRow: List<Part>,
        val tallEnter: Boolean,
        /** Left Shift's standard width in this family. */
        val leftShift: Float,
        /** The keys between the two Shifts. */
        val shiftRow: List<Part>,
        /** Keys between Alt and the space bar. */
        val besideSpaceLeft: List<Part> = emptyList(),
        /** Keys between the space bar and the right-hand modifiers. */
        val besideSpaceRight: List<Part> = emptyList(),
    )

    private data class Part(
        val id: KeyId,
        val width: Float,
        val kind: KeyKind,
        val name: String? = null,
        val shortName: String? = null,
    )

    private class RowCursor(val row: Int) {
        var x = 0f

        fun take(width: Float): Area = Area(x, row, width).also { x += width }

        fun key(part: Part): KeyCap = KeyCap(part.id, listOf(take(part.width)), part.kind, part.name, part.shortName)
    }

    private const val NAVIGATION_COLUMN = COLUMNS - Cap.U1

    private fun build(block: MainBlock): Shape {
        val keys = mutableListOf<KeyCap>()

        val functionRow = RowCursor(0)
        keys += functionRow.key(named(KeyId.ESCAPE, "Esc", kind = KeyKind.FUNCTION))
        (1..12).forEach { keys += functionRow.key(named(KeyId.valueOf("F$it"), "F$it", kind = KeyKind.FUNCTION)) }
        keys += functionRow.key(named(KeyId.PRINT_SCREEN, "PrtSc", kind = KeyKind.FUNCTION, short = "Prt\nSc"))
        keys += functionRow.key(named(KeyId.PAUSE, "Pause", kind = KeyKind.FUNCTION))
        keys += functionRow.key(named(KeyId.FORWARD_DELETE, "Del", kind = KeyKind.FUNCTION))

        val numbers = RowCursor(1)
        block.numberRow.forEach { keys += numbers.key(it) }
        requireEdge(numbers, NAVIGATION_COLUMN, "number row")
        keys += numbers.key(navigation(KeyId.HOME, "Home"))

        val top = RowCursor(2)
        block.topRow.forEach { keys += top.key(it) }
        val enterTop = if (block.tallEnter) top.take(NAVIGATION_COLUMN - top.x) else null
        requireEdge(top, NAVIGATION_COLUMN, "top row")
        keys += top.key(navigation(KeyId.PAGE_UP, "PgUp", short = "Pg\nUp"))

        val home = RowCursor(3)
        block.homeRow.forEach { keys += home.key(it) }
        keys += KeyCap(KeyId.ENTER, listOfNotNull(enterTop, home.take(NAVIGATION_COLUMN - home.x)), KeyKind.MODIFIER, "Enter")
        keys += home.key(navigation(KeyId.PAGE_DOWN, "PgDn", short = "Pg\nDn"))

        val shift = RowCursor(4)
        val between = block.shiftRow.sumOf { it.width.toDouble() }.toFloat()
        val rightShiftSpace = NAVIGATION_COLUMN - Cap.U1 - block.leftShift - between
        val rightShift = maxOf(rightShiftSpace, Cap.U1)
        val leftShift = block.leftShift - (rightShift - rightShiftSpace)
        keys += shift.key(named(KeyId.SHIFT_LEFT, "Shift", leftShift))
        block.shiftRow.forEach { keys += shift.key(it) }
        keys += shift.key(named(KeyId.SHIFT_RIGHT, "Shift", rightShift))
        keys += shift.key(arrows.first { it.id == KeyId.UP })
        keys += shift.key(navigation(KeyId.END, "End"))

        val bottom = RowCursor(5)
        val left = listOf(
            named(KeyId.CTRL_LEFT, "Ctrl", Cap.U1_25, short = "Ctl"),
            named(KeyId.META_LEFT, "Super", Cap.U1_25, short = "Sup"),
            named(KeyId.ALT_LEFT, "Alt", Cap.U1_25),
        ) + block.besideSpaceLeft
        val right = block.besideSpaceRight + listOf(
            named(KeyId.ALT_RIGHT, "Alt"),
            named(KeyId.FN, "Fn"),
            named(KeyId.CTRL_RIGHT, "Ctrl", short = "Ctl"),
        )
        val bottomArrows = arrows.filter { it.id != KeyId.UP }
        val space = COLUMNS - (left + right + bottomArrows).sumOf { it.width.toDouble() }.toFloat()
        require(space >= Cap.U1) { "no room for the space bar" }
        left.forEach { keys += bottom.key(it) }
        keys += bottom.key(Part(KeyId.SPACE, space, KeyKind.SPACE))
        right.forEach { keys += bottom.key(it) }
        bottomArrows.forEach { keys += bottom.key(it) }

        val rows = listOf(RowSize.SLIM) + List(5) { RowSize.REGULAR }
        return Shape(rows, keys)
    }

    private fun requireEdge(row: RowCursor, edge: Float, what: String) =
        require(row.x == edge) { "$what ends at ${row.x}u, not at $edge" }

    private val numberKeys: List<Part> =
        listOf(character(KeyId.GRAVE)) + (1..9).map { character(KeyId.valueOf("DIGIT_$it")) } +
            listOf(character(KeyId.DIGIT_0), character(KeyId.MINUS), character(KeyId.EQUALS))

    private val topLetters: List<Part> =
        "QWERTYUIOP".map { character(KeyId.valueOf("$it")) } + character(KeyId.LEFT_BRACKET) + character(KeyId.RIGHT_BRACKET)

    private val homeLetters: List<Part> =
        "ASDFGHJKL".map { character(KeyId.valueOf("$it")) } + character(KeyId.SEMICOLON) + character(KeyId.APOSTROPHE)

    private val bottomLetters: List<Part> =
        "ZXCVBNM".map { character(KeyId.valueOf("$it")) } + character(KeyId.COMMA) + character(KeyId.PERIOD) + character(KeyId.SLASH)

    private val arrows: List<Part> = listOf(
        Part(KeyId.LEFT, Cap.U1, KeyKind.NAVIGATION, "←"),
        Part(KeyId.DOWN, Cap.U1, KeyKind.NAVIGATION, "↓"),
        Part(KeyId.UP, Cap.U1, KeyKind.NAVIGATION, "↑"),
        Part(KeyId.RIGHT, Cap.U1, KeyKind.NAVIGATION, "→"),
    )

    private fun character(id: KeyId, width: Float = Cap.U1) = Part(id, width, KeyKind.CHARACTER)

    private fun named(id: KeyId, name: String, width: Float = Cap.U1, kind: KeyKind = KeyKind.MODIFIER, short: String? = null) =
        Part(id, width, kind, name, short)

    private fun navigation(id: KeyId, name: String, short: String? = null) = Part(id, Cap.U1, KeyKind.FUNCTION, name, short)
}
