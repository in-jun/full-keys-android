package dev.injun.fullkeys.core.layout

/** A rectangle in whatever unit the caller measures in (pixels on the phone). */
data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun contains(x: Float, y: Float): Boolean = x >= left && x < right && y >= top && y < bottom
}

/** Where a key sits: one box per area, top row first. */
data class Frame(val key: KeyCap, val boxes: List<Box>)

/**
 * Lays a [Shape] out to a width and answers which key a touch landed on.
 *
 * Boxes tile the board edge to edge. The gap drawn between caps belongs to the keys on
 * either side of it, so a touch that lands between two caps still presses one of them;
 * on a key this narrow, a dead strip between caps would swallow a real share of touches.
 */
class Geometry(
    shape: Shape,
    width: Float,
    regularRowHeight: Float,
    slimRowHeight: Float,
    horizontalPadding: Float,
    verticalPadding: Float,
    /**
     * The widest a unit may get. Past it the keys stop growing and the keyboard is centred,
     * so a tablet or an unfolded phone gets keys of a sensible size rather than keys
     * stretched across the whole screen.
     */
    maxUnitWidth: Float = Float.POSITIVE_INFINITY,
) {
    private val left: Float = maxOf(horizontalPadding, (width - Shapes.COLUMNS * maxUnitWidth) / 2)
    private val right: Float = width - left
    private val rowTops = FloatArray(shape.rows.size)
    private val rowBottoms = FloatArray(shape.rows.size)

    val unit: Float = (right - left) / Shapes.COLUMNS
    val frames: List<Frame>
    val height: Float

    init {
        var top = verticalPadding
        shape.rows.forEachIndexed { i, size ->
            rowTops[i] = top
            top += if (size == RowSize.SLIM) slimRowHeight else regularRowHeight
            rowBottoms[i] = top
        }
        height = top + verticalPadding
        frames = shape.keys.map { key ->
            Frame(
                key,
                key.areas.sortedBy { it.row }.map { area ->
                    Box(left + area.x * unit, rowTops[area.row], left + area.right * unit, rowBottoms[area.row])
                },
            )
        }
    }

    /**
     * The key under ([x], [y]). A touch in the padding counts for the nearest edge of the
     * board: the edges of a phone are where thumbs land short.
     */
    fun keyAt(x: Float, y: Float): KeyCap? {
        if (rowTops.isEmpty()) return null
        val cx = x.coerceIn(left, right - EDGE)
        val cy = y.coerceIn(rowTops.first(), rowBottoms.last() - EDGE)
        return frames.firstOrNull { frame -> frame.boxes.any { it.contains(cx, cy) } }?.key
    }

    private companion object {
        /** Keeps a clamped touch inside the last row and column rather than on their far edge. */
        const val EDGE = 0.001f
    }
}
