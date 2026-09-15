package dev.injun.fullkeys.ui.keyboard

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.injun.fullkeys.R

/**
 * What the keyboard does while it is resized instead of typed on: the keys show as they
 * will look but press nothing, a drag anywhere moves the top edge, and a bar above the
 * keys holds the rest of what there is to change.
 */
class Resize(
    /** The row height for the current orientation, in dp. */
    val onRowHeight: (Float) -> Unit,
    val onTranslucent: (Boolean) -> Unit,
    val onFloating: (Boolean) -> Unit,
    val onDone: () -> Unit,
)

/** The lines drawn in the corner a floating keyboard is resized by. */
internal fun DrawScope.drawCornerGrip(colour: Color) {
    val inset = size.minDimension * 0.28f
    val step = size.minDimension * 0.16f
    val weight = size.minDimension * 0.05f
    for (i in 1..2) {
        val d = inset + step * i
        drawLine(
            colour,
            Offset(size.width - d, size.height - inset),
            Offset(size.width - inset, size.height - d),
            strokeWidth = weight,
            cap = StrokeCap.Round,
        )
    }
}

/** The mark that says a thing can be dragged. */
@Composable
private fun Grip(palette: KeyPalette) {
    Box(Modifier.size(HANDLE_WIDTH, HANDLE_HEIGHT).background(palette.secondaryText, RoundedCornerShape(50)))
}

/**
 * What can be changed about the keyboard, on the keyboard: its height by dragging it, and
 * the rest as one row of buttons of one size.
 */
@Composable
internal fun ResizeBar(
    resize: Resize,
    translucent: Boolean,
    floating: Boolean,
    palette: KeyPalette,
    sidePadding: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .padding(horizontal = sidePadding),
        contentAlignment = Alignment.Center,
    ) {
        // The one handle that changes the height of a keyboard fixed to the bottom of the
        // screen. A floating one is dragged about by anywhere on it and resized by its
        // corner, so a handle would stand for nothing.
        if (!floating) Grip(palette)
        Row(
            Modifier.align(Alignment.CenterEnd),
            horizontalArrangement = Arrangement.spacedBy(KEY_GAP),
        ) {
            BarButton(Mark.FLOAT, palette, height, on = floating) { resize.onFloating(!floating) }
            BarButton(Mark.SEE_THROUGH, palette, height, on = translucent) { resize.onTranslucent(!translucent) }
            BarButton(Mark.DONE, palette, height, onClick = resize.onDone)
        }
    }
}

/** What a button on the bar is for, drawn rather than written. */
private enum class Mark(@StringRes val said: Int) {
    FLOAT(R.string.resize_float),
    SEE_THROUGH(R.string.resize_translucent),
    DONE(R.string.resize_done),
}

/**
 * A button on the bar is a cap like any other: the same corners and colours as the keys
 * below it, marked rather than labelled. What is on is filled in the accent, the way a
 * latched Fn key is.
 */
@Composable
private fun BarButton(mark: Mark, palette: KeyPalette, height: Dp, on: Boolean = false, onClick: () -> Unit) {
    // Marked rather than labelled, so what it does is said out loud instead of printed.
    val says = stringResource(mark.said)
    Box(
        Modifier
            .size(height - KEY_GAP)
            .semantics { contentDescription = says }
            .clip(RoundedCornerShape(KEY_RADIUS))
            .background(if (on) palette.accent else palette.modifier)
            .clickable(onClick = onClick)
            .drawBehind { drawMark(mark, palette.text, size.minDimension) },
    )
}

/**
 * The marks the bar is drawn with: a board lifted off the keyboard, a circle half seen
 * through, and a tick.
 */
private fun DrawScope.drawMark(mark: Mark, colour: Color, box: Float) {
    val cx = size.width / 2
    val cy = size.height / 2
    val r = box * MARK_SCALE / 2
    val stroke = Stroke(width = box * MARK_WEIGHT, cap = StrokeCap.Round, join = StrokeJoin.Round)
    when (mark) {
        Mark.FLOAT -> {
            val corner = CornerRadius(r * 0.35f, r * 0.35f)
            drawRoundRect(colour, Offset(cx - r, cy - r), Size(r * 1.5f, r * 1.5f), corner, stroke)
            drawRoundRect(colour, Offset(cx - r * 0.5f, cy - r * 0.5f), Size(r * 1.5f, r * 1.5f), corner, stroke)
        }
        Mark.SEE_THROUGH -> {
            drawCircle(colour, radius = r, center = Offset(cx, cy), style = stroke)
            drawArc(colour, 90f, 180f, true, Offset(cx - r, cy - r), Size(r * 2, r * 2))
        }
        Mark.DONE -> drawPath(
            Path().apply {
                moveTo(cx - r, cy)
                lineTo(cx - r * 0.25f, cy + r * 0.7f)
                lineTo(cx + r, cy - r * 0.7f)
            },
            colour,
            style = stroke,
        )
    }
}

/** The mark drawn on the bar to say the keyboard is dragged by it. */
private val HANDLE_WIDTH = 36.dp
private val HANDLE_HEIGHT = 4.dp

/** How big a mark on the bar is inside its cap, and how thick its lines are. */
private const val MARK_SCALE = 0.44f
private const val MARK_WEIGHT = 0.055f

/** The corner a floating keyboard is resized by, the way a window is. */
internal val CORNER_HANDLE = 40.dp
