package dev.injun.fullkeys.core.layout

import dev.injun.fullkeys.core.KeyId
import dev.injun.fullkeys.core.input.FnLayer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ShapesTest {

    private val all = ShapeId.entries.map { it to Shapes.of(it) }

    /** (x, width) of every area in [row], left to right. */
    private fun Shape.row(row: Int): List<Pair<Float, Float>> =
        keys.flatMap { it.areas }.filter { it.row == row }.sortedBy { it.x }.map { it.x to it.width }

    private fun run(from: Float, count: Int) = List(count) { (from + it) to 1f }

    private fun Shape.key(id: KeyId) = keys.single { it.id == id }

    // QMK's community 75_ansi layout, row by row. The extension rules are only trusted
    // for JIS and ABNT2 because they give this, and 75_iso, exactly.
    @Test
    fun `the rules reproduce the standard 75 percent ANSI layout`() {
        val ansi = Shapes.of(ShapeId.ANSI)
        val reference = listOf(
            run(0f, 16),
            run(0f, 13) + listOf(13f to 2f, 15f to 1f),
            listOf(0f to 1.5f) + run(1.5f, 12) + listOf(13.5f to 1.5f, 15f to 1f),
            listOf(0f to 1.75f) + run(1.75f, 11) + listOf(12.75f to 2.25f, 15f to 1f),
            listOf(0f to 2.25f) + run(2.25f, 10) + listOf(12.25f to 1.75f, 14f to 1f, 15f to 1f),
            listOf(0f to 1.25f, 1.25f to 1.25f, 2.5f to 1.25f, 3.75f to 6.25f) + run(10f, 6),
        )
        reference.forEachIndexed { row, expected -> assertEquals(expected, ansi.row(row), "row $row") }
    }

    // QMK's community 75_iso layout. QMK records the two-row Enter by its lower part,
    // 1.25u at 13.75; the upper part runs from the end of the row's keys to the
    // navigation column.
    @Test
    fun `the rules reproduce the standard 75 percent ISO layout`() {
        val iso = Shapes.of(ShapeId.ISO)
        val reference = listOf(
            run(0f, 16),
            run(0f, 13) + listOf(13f to 2f, 15f to 1f),
            listOf(0f to 1.5f) + run(1.5f, 12) + listOf(13.5f to 1.5f, 15f to 1f),
            listOf(0f to 1.75f) + run(1.75f, 12) + listOf(13.75f to 1.25f, 15f to 1f),
            listOf(0f to 1.25f) + run(1.25f, 11) + listOf(12.25f to 1.75f, 14f to 1f, 15f to 1f),
            listOf(0f to 1.25f, 1.25f to 1.25f, 2.5f to 1.25f, 3.75f to 6.25f) + run(10f, 6),
        )
        reference.forEachIndexed { row, expected -> assertEquals(expected, iso.row(row), "row $row") }
        assertEquals(listOf(Area(13.5f, 2, 1.5f), Area(13.75f, 3, 1.25f)), iso.key(KeyId.ENTER).areas)
    }

    // A gap or an overlap anywhere shows as a hole in the board or two keys answering one
    // touch.
    @Test
    fun `every shape covers each row edge to edge exactly once`() {
        for ((id, shape) in all) {
            shape.rows.indices.forEach { row ->
                var edge = 0f
                for ((x, width) in shape.row(row)) {
                    assertEquals(edge, x, 0.0001f, "$id row $row has a gap or overlap at $edge")
                    edge = x + width
                }
                assertEquals(Shapes.COLUMNS, edge, 0.0001f, "$id row $row ends at $edge")
            }
        }
    }

    @Test
    fun `no key anywhere is narrower than one unit`() {
        for ((id, shape) in all) {
            val narrow = shape.keys.flatMap { key -> key.areas.map { key.id to it } }.filter { (_, area) -> area.width < 1f }
            assertTrue(narrow.isEmpty(), "$id: $narrow")
        }
    }

    @Test
    fun `each family has the keys that set it apart`() {
        fun ids(id: ShapeId) = Shapes.of(id).keys.map { it.id }.toSet()
        val extras = setOf(KeyId.INTL_BACKSLASH, KeyId.YEN, KeyId.RO, KeyId.MUHENKAN, KeyId.HENKAN, KeyId.KATAKANA_HIRAGANA)
        assertEquals(emptySet<KeyId>(), ids(ShapeId.ANSI) intersect extras)
        assertEquals(setOf(KeyId.INTL_BACKSLASH), ids(ShapeId.ISO) intersect extras)
        assertEquals(setOf(KeyId.INTL_BACKSLASH, KeyId.RO), ids(ShapeId.ABNT2) intersect extras)
        assertEquals(setOf(KeyId.YEN, KeyId.RO, KeyId.MUHENKAN, KeyId.HENKAN, KeyId.KATAKANA_HIRAGANA), ids(ShapeId.JIS) intersect extras)
    }

    // The two-row Enter is one key: both of its parts end at the navigation column, and
    // the lower part begins where the row's keys end.
    @Test
    fun `a two-row Enter ends at the navigation column in both rows`() {
        for (id in listOf(ShapeId.ISO, ShapeId.ABNT2, ShapeId.JIS)) {
            val enter = Shapes.of(id).key(KeyId.ENTER).areas
            assertEquals(2, enter.size, "$id")
            assertTrue(enter.all { it.right == Shapes.COLUMNS - 1 }, "$id: $enter")
        }
    }

    // Up has to sit over Down, or the inverted T that a thumb finds without looking is
    // not there.
    @Test
    fun `the up arrow sits directly over the down arrow`() {
        for ((id, shape) in all) {
            assertEquals(shape.key(KeyId.DOWN).areas.single().x, shape.key(KeyId.UP).areas.single().x, "$id")
        }
    }

    @Test
    fun `every key can be reached on some keyboard`() {
        val onCaps = all.flatMap { (_, shape) -> shape.keys.map { it.id } }.toSet()
        val missing = KeyId.entries.toSet() - onCaps - onCaps.map(FnLayer::map).toSet()
        assertTrue(missing.isEmpty(), "no way to send $missing")
    }

    @Test
    fun `no key appears twice on a keyboard`() {
        for ((id, shape) in all) {
            val ids = shape.keys.map { it.id }
            assertEquals(ids.size, ids.toSet().size, "$id duplicates ${ids.groupBy { it }.filterValues { it.size > 1 }.keys}")
        }
    }

    // The standard 75% function row, key for key, so nothing on it is an invention of this app.
    @Test
    fun `every shape has the standard function row`() {
        val standard = listOf(KeyId.ESCAPE) + (1..12).map { KeyId.valueOf("F$it") } +
            listOf(KeyId.PRINT_SCREEN, KeyId.PAUSE, KeyId.FORWARD_DELETE)
        for ((id, shape) in all) {
            val row = shape.keys.filter { it.areas.first().row == 0 }.sortedBy { it.areas.first().x }.map { it.id }
            assertEquals(standard, row, "$id")
        }
    }

    // A repeating modifier would send a run of presses for a key that is meant to be held,
    // and a repeating mode key would flip its mode until released.
    @Test
    fun `only keys that type or move repeat when held`() {
        val repeating = KeyId.entries.filter { it.repeats }.toSet()
        assertTrue(KeyId.BACKSPACE in repeating && KeyId.LEFT in repeating && KeyId.A in repeating)
        assertTrue(repeating.none { it.isModifier })
        assertTrue(listOf(KeyId.FN, KeyId.CAPS_LOCK, KeyId.KATAKANA_HIRAGANA, KeyId.HENKAN, KeyId.MUHENKAN).none { it in repeating })
    }
}
