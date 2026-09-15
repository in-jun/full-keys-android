package dev.injun.fullkeys.core.layout

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OutlineTest {

    @Test
    fun `a one-row key is its four corners, pulled in by the inset`() {
        assertEquals(
            listOf(Point(2f, 2f), Point(48f, 2f), Point(48f, 48f), Point(2f, 48f)),
            outline(listOf(Box(0f, 0f, 50f, 50f)), inset = 2f),
        )
    }

    // An ISO Enter: 1.5 units wide in the upper row and 1.25 in the lower, right edges
    // aligned, units 40 across and rows 40 tall. The seam between the rows is not inset,
    // and the only step is on the left, where the lower part is narrower.
    @Test
    fun `a two-row Enter is one polygon stepping in where the lower row is narrower`() {
        val upper = Box(0f, 0f, 60f, 40f)
        val lower = Box(10f, 40f, 60f, 80f)

        assertEquals(
            listOf(
                Point(2f, 2f), Point(58f, 2f),
                Point(58f, 78f), Point(12f, 78f),
                Point(12f, 40f), Point(2f, 40f),
            ),
            outline(listOf(lower, upper), inset = 2f),
        )
    }

    @Test
    fun `a step on the right is traced on the way down`() {
        val upper = Box(0f, 0f, 60f, 40f)
        val lower = Box(0f, 40f, 50f, 80f)

        assertEquals(
            listOf(
                Point(2f, 2f), Point(58f, 2f),
                Point(58f, 40f), Point(48f, 40f),
                Point(48f, 78f), Point(2f, 78f),
            ),
            outline(listOf(upper, lower), inset = 2f),
        )
    }
}
