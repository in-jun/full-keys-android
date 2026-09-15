package dev.injun.fullkeys.core.layout

import dev.injun.fullkeys.core.KeyId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GeometryTest {

    // 832 wide with 16 of padding either side leaves 800, so a unit is 800 / 16 = 50.
    // Rows: function row 6..31, then 36 each from 31.
    private fun geometry(shape: Shape) = Geometry(
        shape,
        width = 832f,
        regularRowHeight = 36f,
        slimRowHeight = 25f,
        horizontalPadding = 16f,
        verticalPadding = 6f,
    )

    private val ansi = geometry(Shapes.of(ShapeId.ANSI))
    private val iso = geometry(Shapes.of(ShapeId.ISO))
    private val unit = 50f

    private fun Geometry.idAt(x: Float, y: Float) = keyAt(x, y)?.id

    private fun middleOf(row: Int) = 31f + (row - 1) * 36f + 18f

    @Test
    fun `height is the rows plus the padding above and below`() {
        assertEquals(6f + 25f + 5 * 36f + 6f, ansi.height, 0.001f)
    }

    @Test
    fun `a touch in the middle of a cap presses that key`() {
        assertEquals(KeyId.Q, ansi.idAt(16f + 1.5f * unit + unit / 2, middleOf(2)))
    }

    @Test
    fun `a touch in the side padding presses the nearest key in the row`() {
        assertEquals(KeyId.GRAVE, ansi.idAt(2f, middleOf(1)))
        assertEquals(KeyId.HOME, ansi.idAt(830f, middleOf(1)))
    }

    @Test
    fun `a touch above the first row presses the function row`() {
        assertEquals(KeyId.ESCAPE, ansi.idAt(20f, 1f))
    }

    // Both parts of a two-row Enter are Enter, including the corner of the upper part
    // that overhangs the key below it.
    @Test
    fun `either part of a two-row Enter presses Enter`() {
        assertEquals(KeyId.ENTER, iso.idAt(16f + 13.6f * unit, middleOf(2)))
        assertEquals(KeyId.ENTER, iso.idAt(16f + 14.5f * unit, middleOf(3)))
        assertEquals(KeyId.BACKSLASH, iso.idAt(16f + 13.5f * unit, middleOf(3)))
    }

    // On a 1280-wide tablet an uncapped unit would be 80 across. Capped at 60, the sixteen
    // units take 960 and the 320 left over is split evenly either side.
    @Test
    fun `past the widest unit the keys stop growing and the keyboard is centred`() {
        val wide = Geometry(Shapes.of(ShapeId.ANSI), 1280f, 36f, 25f, horizontalPadding = 16f, verticalPadding = 6f, maxUnitWidth = 60f)
        val boxes = wide.frames.flatMap { it.boxes }

        assertEquals(160f, boxes.minOf { it.left }, 0.001f)
        assertEquals(1120f, boxes.maxOf { it.right }, 0.001f)
        assertEquals(KeyId.ESCAPE, wide.idAt(161f, 10f))
    }
}
