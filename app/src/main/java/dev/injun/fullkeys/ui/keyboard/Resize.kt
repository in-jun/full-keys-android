package dev.injun.fullkeys.ui.keyboard

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
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    Row(
        modifier
            .fillMaxWidth()
            .height(height)
            .padding(horizontal = sidePadding),
        horizontalArrangement = Arrangement.spacedBy(KEY_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The one handle that changes the height of a keyboard fixed to the bottom of the
        // screen, in the middle of the room the buttons leave it. A floating one is dragged
        // about by anywhere on it and resized by its corner, so a handle would stand for
        // nothing.
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (!floating) Grip(palette)
        }
        BarButton(stringResource(R.string.resize_float), palette, height, on = floating) { resize.onFloating(!floating) }
        BarButton(stringResource(R.string.resize_translucent), palette, height, on = translucent) { resize.onTranslucent(!translucent) }
        BarButton(stringResource(R.string.resize_done), palette, height, onClick = resize.onDone)
    }
}

/**
 * A button on the bar is a cap like any other: the same corners and colours as the keys
 * below it, with a word on it rather than a mark. A word says what will happen; a mark
 * drawn for the purpose has to be learnt first. What is on is filled in the accent, the
 * way a latched Fn key is.
 */
@Composable
private fun BarButton(label: String, palette: KeyPalette, height: Dp, on: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .height(height - KEY_GAP)
            .clip(RoundedCornerShape(KEY_RADIUS))
            .background(if (on) palette.accent else palette.modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = BAR_LABEL_PADDING),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            label,
            style = TextStyle(fontSize = BAR_LABEL_SIZE, fontWeight = FontWeight.Medium, color = palette.text, shadow = palette.textShadow),
        )
    }
}

/** The mark drawn on the bar to say the keyboard is dragged by it. */
private val HANDLE_WIDTH = 36.dp
private val HANDLE_HEIGHT = 4.dp

/** The room a word on the bar has either side, and its size. */
private val BAR_LABEL_PADDING = 10.dp
private val BAR_LABEL_SIZE = 11.sp

/** The corner a floating keyboard is resized by, the way a window is. */
internal val CORNER_HANDLE = 40.dp
