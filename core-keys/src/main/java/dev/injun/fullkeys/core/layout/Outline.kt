package dev.injun.fullkeys.core.layout

data class Point(val x: Float, val y: Float)

/**
 * The outline of a cap whose [boxes] are stacked one row above the next, clockwise from
 * the top-left corner, with every outer edge pulled in by [inset].
 *
 * A one-row key comes out as its four corners. A two-row Enter comes out as one polygon
 * with a step where the rows differ in width, so it is drawn as a single piece rather
 * than as two rectangles whose seam would show. Edges shared between rows are not inset:
 * they are inside the cap.
 */
fun outline(boxes: List<Box>, inset: Float): List<Point> {
    require(boxes.isNotEmpty()) { "a cap has at least one box" }
    val rows = boxes.sortedBy { it.top }.let { sorted ->
        sorted.mapIndexed { i, box ->
            Box(
                left = box.left + inset,
                top = if (i == 0) box.top + inset else box.top,
                right = box.right - inset,
                bottom = if (i == sorted.lastIndex) box.bottom - inset else box.bottom,
            )
        }
    }

    val points = mutableListOf(Point(rows.first().left, rows.first().top), Point(rows.first().right, rows.first().top))
    for (i in 0 until rows.lastIndex) {
        val (upper, lower) = rows[i] to rows[i + 1]
        if (upper.right != lower.right) {
            points += Point(upper.right, upper.bottom)
            points += Point(lower.right, upper.bottom)
        }
    }
    points += Point(rows.last().right, rows.last().bottom)
    points += Point(rows.last().left, rows.last().bottom)
    for (i in rows.lastIndex downTo 1) {
        val (lower, upper) = rows[i] to rows[i - 1]
        if (lower.left != upper.left) {
            points += Point(lower.left, lower.top)
            points += Point(upper.left, lower.top)
        }
    }
    return points
}
