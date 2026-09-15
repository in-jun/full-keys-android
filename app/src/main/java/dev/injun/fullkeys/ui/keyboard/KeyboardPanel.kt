package dev.injun.fullkeys.ui.keyboard

import android.content.res.Configuration
import android.view.HapticFeedbackConstants
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.tappableElement
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntRect
import androidx.compose.ui.unit.sp
import dev.injun.fullkeys.R
import dev.injun.fullkeys.core.KeyId
import dev.injun.fullkeys.core.input.Latch
import dev.injun.fullkeys.core.layout.Frame
import dev.injun.fullkeys.core.layout.Geometry
import dev.injun.fullkeys.core.layout.KeyCap
import dev.injun.fullkeys.core.layout.KeyKind
import dev.injun.fullkeys.core.layout.KeyboardLayout
import dev.injun.fullkeys.core.layout.Point
import dev.injun.fullkeys.core.layout.RowSize
import dev.injun.fullkeys.core.layout.Shapes
import dev.injun.fullkeys.core.layout.outline
import dev.injun.fullkeys.ime.FnState
import dev.injun.fullkeys.settings.KeyboardSettings
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * The keyboard itself.
 *
 * One pointer handler covers the whole panel and works out which key each finger is on,
 * rather than every cap handling its own touches. A key has to go down on the first
 * contact and up on that finger's release even when other fingers come and go, and a
 * shortcut is two fingers on two caps at once; per-cap gesture handling is built for
 * taps and would cancel one when the other starts.
 *
 * [clearSystemBars] pads the keyboard clear of the navigation bar and cutouts, which only
 * the input method's own window needs; a preview inside a screen leaves it off.
 *
 * With [resize] set the keyboard is being resized rather than typed on; see [Resize].
 *
 * A floating keyboard is drawn as a board of its own somewhere on the screen rather than
 * across the bottom of it. [onBounds] reports where that board ended up, which the input
 * method needs: everything outside it belongs to the app underneath.
 */
@Composable
fun KeyboardPanel(
    layout: KeyboardLayout,
    settings: KeyboardSettings,
    fn: FnState,
    onPress: (pointer: Long, key: KeyCap) -> Unit,
    onRelease: (pointer: Long) -> Unit,
    modifier: Modifier = Modifier,
    clearSystemBars: Boolean = true,
    resize: Resize? = null,
    /** Where a floating keyboard has been dragged, as a share of the room it has to move in. */
    onMove: (x: Float, y: Float) -> Unit = { _, _ -> },
    onBounds: (IntRect) -> Unit = {},
) {
    // The device's orientation, not the window's shape: an input method's window can be
    // only as tall as the keyboard, which is wider than tall in portrait too.
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val rowHeightDp = if (landscape) settings.landscapeRowHeightDp else settings.portraitRowHeightDp
    val sidePadding = if (landscape) 12.dp else 6.dp
    val shape = layout.shape
    val palette = if (settings.translucent) KeyPalette.Translucent else KeyPalette.Opaque

    val view = LocalView.current
    val density = LocalDensity.current
    val press by rememberUpdatedState(onPress)
    val release by rememberUpdatedState(onRelease)
    val touched = remember { HashMap<Long, KeyCap>() }

    // Each key holds whether it is down in a state of its own, so a finger landing on one
    // key redraws that cap alone rather than every cap on the keyboard.
    val held = remember(layout.shape) {
        layout.shape.keys.associateWith { mutableStateOf(false) }
    }

    // Keys stop pressing while the keyboard is resized, and a finger that was on one when
    // that began is no longer on anything.
    val resizing = resize != null
    LaunchedEffect(resizing) {
        touched.clear()
        held.values.forEach { it.value = false }
        if (resizing) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    // A preview inside a screen is a picture of the keyboard, so it stays where it is put.
    val floating = settings.floating && clearSystemBars
    val insets = if (clearSystemBars) keyboardInsets() else PaddingValues()

    // The floating board is placed inside the whole screen; a docked one fills the width it
    // is given, and the caller's modifier belongs to the board either way.
    BoxWithConstraints(
        if (floating) {
            // A floating board moves inside what the screen actually leaves free, so it can
            // never be put under the system's own buttons where its keys would not press.
            Modifier
                .fillMaxSize()
                .padding(insets)
                .padding(FLOAT_MARGIN)
        } else {
            Modifier.fillMaxWidth()
        },
    ) {
        val screen = IntSize(constraints.maxWidth, constraints.maxHeight)
        var boardSize by remember { mutableStateOf(IntSize.Zero) }
        val board = if (!floating) {
            modifier
                .fillMaxWidth()
                // The keyboard's own surface stops where its keys do. The strip the system
                // keeps for itself below is left to whatever is behind, so the keyboard
                // does not put a band of its own colour under the last row.
                .padding(bottom = insets.calculateBottomPadding())
                .background(palette.ground)
                .padding(
                    start = insets.calculateStartPadding(LayoutDirection.Ltr),
                    end = insets.calculateEndPadding(LayoutDirection.Ltr),
                )
        } else {
            // Where the board sits is kept as a share of the room it has to move in, so it
            // stays where it was put when the screen it floats on changes shape.
            val room = IntSize(
                (screen.width - boardSize.width).coerceAtLeast(0),
                (screen.height - boardSize.height).coerceAtLeast(0),
            )
            Modifier
                .offset { IntOffset((room.width * settings.floatX).roundToInt(), (room.height * settings.floatY).roundToInt()) }
                .width(floatingWidth(rowHeightDp.dp, sidePadding, with(density) { screen.width.toDp() }))
                .onSizeChanged { boardSize = it }
                .shadow(FLOAT_ELEVATION, RoundedCornerShape(FLOAT_RADIUS))
                .clip(RoundedCornerShape(FLOAT_RADIUS))
                .background(palette.ground)
                .border(OUTLINE_WIDTH, palette.outline ?: palette.modifier, RoundedCornerShape(FLOAT_RADIUS))
        }
        BoxWithConstraints(board.onGloballyPositioned { onBounds(it.boundsInWindow().roundToIntRect()) }) {
        val widthPx = constraints.maxWidth.toFloat()
        val screenHeightPx = LocalResources.current.displayMetrics.heightPixels.toFloat()
        val verticalPadding = 4.dp
        // How many regular rows the keyboard is tall, the function row counting for its share.
        val rows = shape.rows.sumOf { (if (it == RowSize.SLIM) SLIM_ROW_RATIO else 1f).toDouble() }.toFloat()
        // Rows as tall as settings ask, unless that would bury more of the screen than
        // MAX_HEIGHT_FRACTION; then every row gives up the same share.
        val tallestRowPx = with(density) { (screenHeightPx * MAX_HEIGHT_FRACTION - 2 * verticalPadding.toPx()) / rows }
        // The row heights that look different on this screen: past the tallest, settings
        // would change nothing visible, so resizing stops there.
        val range = KeyboardSettings.ROW_HEIGHT_RANGE_DP
        val shownRange = range.start..(tallestRowPx / density.density).coerceIn(range)
        val shownRowHeightDp = rowHeightDp.coerceIn(shownRange)
        val geometry = remember(shape, widthPx, tallestRowPx, rowHeightDp, sidePadding, density) {
            with(density) {
                val regular = minOf(rowHeightDp.dp.toPx(), tallestRowPx)
                Geometry(
                    shape,
                    width = widthPx,
                    regularRowHeight = regular,
                    slimRowHeight = regular * SLIM_ROW_RATIO,
                    horizontalPadding = sidePadding.toPx(),
                    verticalPadding = verticalPadding.toPx(),
                    maxUnitWidth = regular * MAX_KEY_ASPECT,
                )
            }
        }
        val gapPx = with(density) { KEY_GAP.toPx() }
        val radiusPx = with(density) { KEY_RADIUS.toPx() }
        val measurer = rememberTextMeasurer()
        val sizes = remember(geometry, layout, density, measurer) {
            labelSizesFor(geometry, layout, gapPx, measurer, density)
        }

        val currentRowHeightDp by rememberUpdatedState(shownRowHeightDp)
        // Dragging an edge drags that edge: the top one grows the keyboard as it goes up,
        // the bottom corner as it goes down.
        fun resizeFrom(edge: Float): Modifier = if (resize == null) {
            Modifier
        } else {
            Modifier.pointerInput(resize, shownRange, rows, density, edge) {
                awaitEachGesture {
                    awaitFirstDown()
                    val startY = currentEvent.motionEvent?.rawY ?: return@awaitEachGesture
                    val start = currentRowHeightDp
                    do {
                        val event = awaitPointerEvent()
                        event.motionEvent?.let { motion ->
                            val moved = (startY - motion.rawY) * edge / density.density / rows
                            val rowHeight = (start + moved).coerceIn(shownRange)
                            if (rowHeight != currentRowHeightDp) resize.onRowHeight(rowHeight)
                        }
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                }
            }
        }
        val resizeDrag = resizeFrom(1f)
        val resizeFromCorner = resizeFrom(-1f)

        val moveDrag = if (resize == null || !floating) {
            Modifier
        } else {
            Modifier.pointerInput(onMove, screen, boardSize) {
                // Dragging a floating keyboard carries the board with it, measured on the
                // screen since the board moves under the finger as it goes.
                awaitEachGesture {
                    awaitFirstDown()
                    var last = currentEvent.motionEvent?.let { Offset(it.rawX, it.rawY) } ?: return@awaitEachGesture
                    var at = Offset(settings.floatX, settings.floatY)
                    do {
                        val event = awaitPointerEvent()
                        event.motionEvent?.let { motion ->
                            val free = IntSize(
                                (screen.width - boardSize.width).coerceAtLeast(1),
                                (screen.height - boardSize.height).coerceAtLeast(1),
                            )
                            at = Offset(
                                (at.x + (motion.rawX - last.x) / free.width).coerceIn(0f, 1f),
                                (at.y + (motion.rawY - last.y) / free.height).coerceIn(0f, 1f),
                            )
                            last = Offset(motion.rawX, motion.rawY)
                            onMove(at.x, at.y)
                        }
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                }
            }
        }
        val keysArea = Modifier
            .fillMaxWidth()
            .height(with(density) { geometry.height.toDp() })
            // A floating keyboard being resized is dragged about by anywhere on it, its
            // keys included: they press nothing while it is being resized anyway.
            .then(if (floating) moveDrag else Modifier)

        Column {
            // The bar grows out of the top of the keyboard rather than appearing on it,
            // so that what is underneath is pushed down rather than jumped.
            AnimatedVisibility(resize != null, enter = expandVertically(), exit = shrinkVertically()) {
                if (resize != null) {
                    Column {
                        ResizeBar(
                            resize = resize,
                            translucent = settings.translucent,
                            floating = settings.floating,
                            palette = palette,
                            sidePadding = sidePadding,
                            // As tall as the function row, so the bar is a row of the keyboard.
                            height = shownRowHeightDp.dp * SLIM_ROW_RATIO,
                            modifier = if (floating) moveDrag else resizeDrag,
                        )
                    }
                }
            }
            Box(
                if (resize != null) {
                    keysArea.alpha(RESTING_KEYS_ALPHA)
                } else {
                    keysArea.pointerInput(geometry, held) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                for (change in event.changes) {
                                    val id = change.id.value
                                    if (change.changedToDownIgnoreConsumed()) {
                                        geometry.keyAt(change.position.x, change.position.y)?.let { key ->
                                            touched[id] = key
                                            held.getValue(key).value = true
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            press(id, key)
                                        }
                                        change.consume()
                                    } else if (change.changedToUpIgnoreConsumed()) {
                                        touched.remove(id)?.let { key ->
                                            held.getValue(key).value = touched.containsValue(key)
                                            release(id)
                                        }
                                        change.consume()
                                    }
                                }
                            }
                        }
                    }
                },
            ) {
                for (frame in geometry.frames) {
                    Cap(
                        frame = frame,
                        layout = layout,
                        gapPx = gapPx,
                        radiusPx = radiusPx,
                        sizes = sizes,
                        palette = palette,
                        pressed = held.getValue(frame.key),
                        fn = { fn },
                    )
                }
                // Drawn last so it lies over the caps: the corner a floating board is
                // resized by, the way a window is.
                if (floating && resize != null) {
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .size(CORNER_HANDLE)
                            .then(resizeFromCorner)
                            .drawBehind { drawCornerGrip(palette.text) },
                    )
                }
            }
        }
        }
    }
}

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
private fun DrawScope.drawCornerGrip(colour: Color) {
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
private fun ResizeBar(
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
    MOVE(R.string.resize_move),
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
 * through, a tick, and the cross that says a thing can be dragged about.
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
        Mark.MOVE -> {
            drawLine(colour, Offset(cx - r, cy), Offset(cx + r, cy), stroke.width, StrokeCap.Round)
            drawLine(colour, Offset(cx, cy - r), Offset(cx, cy + r), stroke.width, StrokeCap.Round)
            listOf(
                Triple(Offset(cx - r, cy), Offset(cx - r * 0.5f, cy - r * 0.4f), Offset(cx - r * 0.5f, cy + r * 0.4f)),
                Triple(Offset(cx + r, cy), Offset(cx + r * 0.5f, cy - r * 0.4f), Offset(cx + r * 0.5f, cy + r * 0.4f)),
                Triple(Offset(cx, cy - r), Offset(cx - r * 0.4f, cy - r * 0.5f), Offset(cx + r * 0.4f, cy - r * 0.5f)),
                Triple(Offset(cx, cy + r), Offset(cx - r * 0.4f, cy + r * 0.5f), Offset(cx + r * 0.4f, cy + r * 0.5f)),
            ).forEach { (tip, a, b) ->
                drawPath(
                    Path().apply {
                        moveTo(tip.x, tip.y)
                        lineTo(a.x, a.y)
                        lineTo(b.x, b.y)
                        close()
                    },
                    colour,
                )
            }
        }
    }
}

/**
 * One cap: its outline, filled, and its labels in its first row.
 *
 * Every cap is drawn from [outline], so a two-row Enter is the same kind of shape as any
 * other key rather than a special case, and its labels sit in its upper part as they do on
 * a printed keycap.
 */
@Composable
private fun Cap(
    frame: Frame,
    layout: KeyboardLayout,
    gapPx: Float,
    radiusPx: Float,
    sizes: LabelSizes,
    palette: KeyPalette,
    // Read while drawing rather than passed in: a key that goes down then only redraws the
    // cap whose colour it changes, instead of every cap on the keyboard being built again.
    pressed: State<Boolean>,
    fn: () -> FnState,
) {
    val key = frame.key
    val density = LocalDensity.current
    val left = frame.boxes.minOf { it.left }
    val top = frame.boxes.minOf { it.top }
    val width = frame.boxes.maxOf { it.right } - left
    val height = frame.boxes.maxOf { it.bottom } - top
    val isFnKey = key.id == KeyId.FN
    val resting = when (key.kind) {
        KeyKind.CHARACTER, KeyKind.SPACE -> palette.character
        KeyKind.FUNCTION -> palette.function
        KeyKind.MODIFIER, KeyKind.NAVIGATION -> palette.modifier
    }
    val path = remember(frame, gapPx, radiusPx) { roundedPath(outline(frame.boxes, gapPx / 2), radiusPx, left, top) }
    val labelBox = frame.boxes.first()

    Box(
        Modifier
            .absoluteOffset { IntOffset(left.roundToInt(), top.roundToInt()) }
            .size(with(density) { width.toDp() }, with(density) { height.toDp() })
            .drawBehind {
                val state = fn()
                val fill = when {
                    pressed.value -> palette.pressed
                    isFnKey && state.latch == Latch.LOCKED -> palette.accent
                    isFnKey && state.active -> palette.accentDim
                    else -> resting
                }
                drawPath(path, fill)
                palette.outline?.let { drawPath(path, it, style = Stroke(width = OUTLINE_WIDTH.toPx())) }
            },
    ) {
        val inset = with(density) { LABEL_INSET.toPx() }
        val areaWidth = labelBox.width - gapPx - 2 * inset
        val areaHeight = labelBox.height - gapPx - 2 * inset
        Box(
            Modifier
                .absoluteOffset { IntOffset((labelBox.left - left).roundToInt(), (labelBox.top - top).roundToInt()) }
                .size(with(density) { labelBox.width.toDp() }, with(density) { labelBox.height.toDp() })
                .padding(with(density) { (gapPx / 2 + inset).toDp() }),
        ) {
            val text = capText(key, layout, sizes.short)
            for (placement in placements(text, areaWidth, areaHeight)) {
                PlacedLabel(placement, text.kind, sizes, palette)
            }
        }
    }
}

/** Whether modifiers and function keys print their short names. */
private data class ShortNames(val modifiers: Boolean, val functions: Boolean)

/** What a cap prints, decided in one place so that drawing and sizing agree on it. */
private data class CapText(
    val main: String,
    /** Which size the main label shares: the key's kind, or a letter's for one-character names. */
    val kind: KeyKind,
    /** Printed small above [main]: what the key types with Shift, when that is not just its capital. */
    val upper: String? = null,
    /** The character of a second script. */
    val secondary: String? = null,
)

/**
 * The legend the layout gives the key, or the key's own name when the layout gives none.
 *
 * A letter whose Shift is its capital prints the capital alone, the way keycaps do; any
 * other key prints its Shift character small above its base one.
 */
private fun capText(key: KeyCap, layout: KeyboardLayout, short: ShortNames): CapText {
    val legend = layout.legends[key.id]
    if (legend != null) {
        val shifted = legend.shifted
        val capitalised = shifted != null && legend.base.uppercase(Locale.ROOT) == shifted
        return CapText(
            main = if (capitalised) shifted else legend.base,
            kind = KeyKind.CHARACTER,
            upper = shifted.takeUnless { capitalised },
            secondary = legend.secondary,
        )
    }
    val name = key.name.orEmpty()
    // A one-character name, such as Backspace's symbol or an arrow, is sized with the letters.
    val kind = if (name.length == 1) KeyKind.CHARACTER else key.kind
    val useShort = when (kind) {
        KeyKind.MODIFIER -> short.modifiers
        KeyKind.FUNCTION -> short.functions
        else -> false
    }
    return CapText(main = if (useShort) key.shortName ?: name else name, kind = kind)
}

/** Which labels share a size: those that play the same part on their caps. */
private enum class Role {
    /** The one legend of a key that prints one: a capital, a symbol, an arrow. */
    LETTER,

    /** The base legend under a Shift character. */
    PAIR,

    /** Shift characters and second-script characters. */
    SMALL,
    MODIFIER,
    FUNCTION,
}

/** How a label is coloured: the key's own legend or name, a Shift character, or a second script. */
private enum class Ink { MAIN, SHIFT, SCRIPT }

/** A label and the part of the cap's label area it has to fit in, in pixels. */
private data class Placement(
    val text: String,
    val role: Role,
    val ink: Ink,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val alignment: Alignment,
)

/**
 * Where each label of a cap goes, as keycaps printed for one or two scripts place them.
 *
 * - One legend fills the cap.
 * - A Shift character sits over the base one.
 * - A second script goes in the bottom-right and the Latin legend in the top-left, so the
 *   two sit diagonally and each keeps the cap's full width.
 * - With a Shift character as well, the Latin pair takes the left half and the second
 *   script stays bottom-right.
 *
 * Drawing places labels in these boxes and sizing fits labels to them, so a label is never
 * drawn anywhere it was not measured for.
 */
private fun placements(text: CapText, width: Float, height: Float): List<Placement> {
    val role = when (text.kind) {
        KeyKind.MODIFIER -> Role.MODIFIER
        KeyKind.FUNCTION -> Role.FUNCTION
        else -> if (text.upper != null) Role.PAIR else Role.LETTER
    }
    val half = height / 2
    val upper = text.upper
    val secondary = text.secondary
    return when {
        upper == null && secondary == null ->
            listOf(Placement(text.main, role, Ink.MAIN, 0f, 0f, width, height, Alignment.Center))

        secondary == null -> listOf(
            Placement(requireNotNull(upper), Role.SMALL, Ink.SHIFT, 0f, 0f, width, half, Alignment.BottomCenter),
            Placement(text.main, role, Ink.MAIN, 0f, half, width, half, Alignment.TopCenter),
        )

        upper == null -> listOf(
            Placement(text.main, role, Ink.MAIN, 0f, 0f, width, half, Alignment.CenterStart),
            Placement(secondary, Role.SMALL, Ink.SCRIPT, 0f, half, width, half, Alignment.CenterEnd),
        )

        else -> listOf(
            Placement(upper, Role.SMALL, Ink.SHIFT, 0f, 0f, width / 2, half, Alignment.BottomCenter),
            Placement(text.main, role, Ink.MAIN, 0f, half, width / 2, half, Alignment.TopCenter),
            Placement(secondary, Role.SMALL, Ink.SCRIPT, width / 2, half, width / 2, half, Alignment.CenterEnd),
        )
    }
}

@Composable
private fun BoxScope.PlacedLabel(placement: Placement, kind: KeyKind, sizes: LabelSizes, palette: KeyPalette) {
    val density = LocalDensity.current
    val color = when (placement.ink) {
        Ink.SCRIPT -> palette.secondaryScript
        Ink.SHIFT -> palette.secondaryText
        Ink.MAIN -> if (kind == KeyKind.MODIFIER || kind == KeyKind.FUNCTION) palette.secondaryText else palette.text
    }
    Box(
        Modifier
            .absoluteOffset { IntOffset(placement.x.roundToInt(), placement.y.roundToInt()) }
            .size(with(density) { placement.width.toDp() }, with(density) { placement.height.toDp() }),
        contentAlignment = placement.alignment,
    ) {
        Label(placement.text, sizes.of(placement.role), color, palette)
    }
}

/**
 * [points] as a closed path with each corner rounded by up to [radius], relative to
 * ([originX], [originY]). A quadratic curve through each corner rounds outward and inward
 * corners alike, so the inside corner of a two-row Enter needs nothing of its own.
 */
private fun roundedPath(points: List<Point>, radius: Float, originX: Float, originY: Float): Path {
    val path = Path()
    points.forEachIndexed { i, corner ->
        val previous = points[(i - 1 + points.size) % points.size]
        val next = points[(i + 1) % points.size]
        val r = minOf(radius, distance(previous, corner) / 2, distance(corner, next) / 2)
        val from = towards(corner, previous, r)
        val to = towards(corner, next, r)
        if (i == 0) path.moveTo(from.x - originX, from.y - originY) else path.lineTo(from.x - originX, from.y - originY)
        path.quadraticTo(corner.x - originX, corner.y - originY, to.x - originX, to.y - originY)
    }
    path.close()
    return path
}

/** Outline edges are horizontal or vertical, so their length is the sum of the two differences. */
private fun distance(a: Point, b: Point) = abs(a.x - b.x) + abs(a.y - b.y)

private fun towards(from: Point, to: Point, by: Float): Offset {
    val length = distance(from, to)
    return Offset(from.x + (to.x - from.x) / length * by, from.y + (to.y - from.y) / length * by)
}

/**
 * Clear of cutouts and the navigation bar, and above the strip at the bottom where a swipe
 * up means home: a bottom-row key pressed there would start the gesture instead.
 *
 * Read as values rather than applied with windowInsetsPadding. In an input method's
 * window that modifier left the bottom unpadded in landscape even while these same insets
 * reported the gesture strip, and the space bar sat under the home handle.
 */
@Composable
private fun keyboardInsets(): PaddingValues {
    val density = LocalDensity.current
    // Where the system takes the taps: the gesture handle, and the strip beside it that
    // holds the buttons for switching or hiding the keyboard. Keys under it would be
    // pressed by the system instead of by the keyboard.
    val bottom = maxOf(
        WindowInsets.navigationBars.getBottom(density),
        WindowInsets.tappableElement.getBottom(density),
    )
    // The same on both sides, so a cutout on one edge of a landscape screen keeps the keys
    // clear of it without pushing the whole keyboard off centre.
    val sides = maxOf(
        WindowInsets.displayCutout.getLeft(density, LayoutDirection.Ltr),
        WindowInsets.displayCutout.getRight(density, LayoutDirection.Ltr),
    )
    return with(density) { PaddingValues(start = sides.toDp(), end = sides.toDp(), bottom = bottom.toDp()) }
}

@Composable
private fun Label(text: String, size: TextUnit, color: Color, palette: KeyPalette, modifier: Modifier = Modifier) {
    BasicText(
        text = text,
        modifier = modifier,
        maxLines = 2,
        softWrap = false,
        style = labelStyle(size).copy(color = color, shadow = palette.textShadow),
    )
}

private fun labelStyle(size: TextUnit) = TextStyle(
    fontSize = size,
    lineHeight = size,
    fontWeight = FontWeight.Medium,
    textAlign = TextAlign.Center,
)

/** One size for each role, so a row of keys reads as one row. */
private data class LabelSizes(
    val letter: TextUnit,
    val pair: TextUnit,
    val small: TextUnit,
    val modifier: TextUnit,
    val function: TextUnit,
    val short: ShortNames,
) {
    fun of(role: Role): TextUnit = when (role) {
        Role.LETTER -> letter
        Role.PAIR -> pair
        Role.SMALL -> small
        Role.MODIFIER -> modifier
        Role.FUNCTION -> function
    }
}

/**
 * The largest size at which every label of a role fits the box [placements] gives it.
 *
 * Fitting each label on its own made "F9" large and "F10" small beside it, because one
 * more character forced one key down; a row whose labels change size from key to key
 * looks broken. So the tightest label of each role sets the size for all of that role.
 *
 * Rather than shrink past readable, modifiers and function keys switch to their short
 * names once the full names would drop below [READABLE_WORD_SIZE].
 */
private fun labelSizesFor(
    geometry: Geometry,
    layout: KeyboardLayout,
    gapPx: Float,
    measurer: TextMeasurer,
    density: Density,
): LabelSizes = with(density) {
    val inset = LABEL_INSET.toPx()
    val caps = geometry.frames.map { frame ->
        val box = frame.boxes.first()
        Triple(frame.key, box.width - gapPx - 2 * inset, box.height - gapPx - 2 * inset)
    }

    fun labels(role: Role, short: ShortNames): List<Placement> = caps.flatMap { (key, width, height) ->
        placements(capText(key, layout, short), width, height).filter { it.role == role }
    }

    // A box drawn at label.width pixels can come out a pixel narrower once it has been
    // through dp and back, so a label only fits with that pixel to spare.
    fun fits(labels: List<Placement>, size: Float) = labels.all { label ->
        val measured = measurer.measure(label.text, labelStyle(size.sp), maxLines = 2, softWrap = false).size
        measured.width <= label.width - ROUNDING_PX && measured.height <= label.height - ROUNDING_PX
    }

    /**
     * The size that fits every label, or null below [floor]. Scaling from one measurement
     * is only an estimate: small text is hinted wider than its size predicts, so the
     * estimate is measured again and stepped down until nothing overflows.
     */
    fun fit(labels: List<Placement>, max: TextUnit, floor: TextUnit = MIN_LABEL_SIZE): TextUnit? {
        var size = max.value
        for (label in labels) {
            val measured = measurer.measure(label.text, labelStyle(max), maxLines = 2, softWrap = false).size
            if (measured.width > label.width) size = minOf(size, max.value * label.width / measured.width)
            if (measured.height > label.height) size = minOf(size, max.value * label.height / measured.height)
        }
        size = floor(size * 2) / 2
        while (size >= floor.value && !fits(labels, size)) size -= LABEL_STEP
        return if (size < floor.value) null else size.sp
    }

    val main = MAX_LABEL_SIZE.toSp()
    val small = MAX_SMALL_LABEL_SIZE.toSp()
    val long = ShortNames(modifiers = false, functions = false)
    val short = ShortNames(modifiers = true, functions = true)

    // Readable if at all possible; otherwise smaller still, because a label cut off is
    // worse than a small one.
    fun whole(labels: List<Placement>, max: TextUnit): TextUnit =
        fit(labels, max) ?: fit(labels, max, floor = SMALLEST_LABEL_SIZE) ?: SMALLEST_LABEL_SIZE

    val fullModifiers = fit(labels(Role.MODIFIER, long), main, floor = READABLE_WORD_SIZE)
    val fullFunctions = fit(labels(Role.FUNCTION, long), main, floor = READABLE_WORD_SIZE)
    // A name is never printed larger than the letters. On a keycap the character is the
    // biggest thing printed; a wide cap fits "Shift" at a size that would otherwise dwarf
    // the letter beside it, which is what makes a keyboard look wrong at a glance.
    val letter = whole(labels(Role.LETTER, long), main)
    fun atMostLetter(size: TextUnit) = if (size.value <= letter.value) size else letter
    LabelSizes(
        letter = letter,
        pair = whole(labels(Role.PAIR, long), main),
        small = whole(labels(Role.SMALL, long), small),
        modifier = atMostLetter(fullModifiers ?: whole(labels(Role.MODIFIER, short), main)),
        function = atMostLetter(fullFunctions ?: whole(labels(Role.FUNCTION, short), main)),
        short = ShortNames(modifiers = fullModifiers == null, functions = fullFunctions == null),
    )
}

/**
 * Two looks. Opaque keeps the caps readable over anything; see-through lets the app
 * underneath show behind the keyboard, and gives the labels a shadow so they still read
 * over whatever text is underneath.
 */
private data class KeyPalette(
    val ground: Color,
    val character: Color,
    val modifier: Color,
    val function: Color,
    val pressed: Color,
    val accent: Color,
    val accentDim: Color,
    val text: Color,
    val secondaryText: Color,
    val secondaryScript: Color,
    val outline: Color?,
    val textShadow: Shadow?,
) {
    companion object {
        val Opaque = KeyPalette(
            ground = Color(0xFF15171C),
            character = Color(0xFF2D3039),
            modifier = Color(0xFF22252C),
            function = Color(0xFF1F2128),
            pressed = Color(0xFF4A5060),
            accent = Color(0xFF3D59A1),
            accentDim = Color(0xFF2C3A5E),
            text = Color(0xFFE6E8EE),
            secondaryText = Color(0xFF9AA0AD),
            secondaryScript = Color(0xFF8C9AD0),
            outline = null,
            textShadow = null,
        )

        val Translucent = KeyPalette(
            ground = Color(0x4D0C0D11),
            character = Color(0x21FFFFFF),
            modifier = Color(0x1AFFFFFF),
            function = Color(0x14FFFFFF),
            pressed = Color(0x66FFFFFF),
            accent = Color(0x997AA2F7),
            accentDim = Color(0x557AA2F7),
            text = Color.White,
            secondaryText = Color(0xDDFFFFFF),
            secondaryScript = Color(0xFFC9D4FF),
            outline = Color(0x38FFFFFF),
            textShadow = Shadow(Color(0xCC000000), Offset(0f, 1f), blurRadius = 3f),
        )
    }
}

/**
 * How wide a floating keyboard is: the width its keys need to come out square, which is the
 * shape of the keys on the keyboard it copies. On a screen too narrow for that it is as wide
 * as the screen, which is where a keyboard across the bottom is the better shape anyway.
 */
private fun floatingWidth(rowHeight: Dp, sidePadding: Dp, screenWidth: Dp): Dp =
    minOf(rowHeight * Shapes.COLUMNS + sidePadding * 2, screenWidth)

private const val SLIM_ROW_RATIO = 0.7f

/**
 * The most of the screen's height the keyboard may cover. Past it the app underneath has too
 * little left to be worth looking at, so rows shrink instead of the settings being obeyed.
 */
private const val MAX_HEIGHT_FRACTION = 0.7f

/**
 * The widest a 1u key may be for its height. A tablet in landscape would otherwise stretch
 * keys to more than twice as wide as they are tall; capped, every screen gets keys of
 * about the same shape and the width left over becomes margin either side.
 */
private const val MAX_KEY_ASPECT = 1.5f

/** A floating keyboard is a board in its own right, with corners and a shadow of its own. */
private val FLOAT_RADIUS = 16.dp
private val FLOAT_MARGIN = 6.dp
private val FLOAT_ELEVATION = 8.dp

/** The strip a floating keyboard is dragged by, and the mark drawn in the middle of it. */
private val HANDLE_ROW_HEIGHT = 18.dp
private val HANDLE_WIDTH = 36.dp
private val HANDLE_HEIGHT = 4.dp

/** How the keys look while the keyboard is being resized, when they press nothing. */
private const val RESTING_KEYS_ALPHA = 0.55f

/** How big a mark on the bar is inside its cap, and how thick its lines are. */
private const val MARK_SCALE = 0.44f
private const val MARK_WEIGHT = 0.055f

/** The corner a floating keyboard is resized by, the way a window is. */
private val CORNER_HANDLE = 40.dp
private val KEY_GAP = 4.dp
private val KEY_RADIUS = 5.dp
private val OUTLINE_WIDTH = 0.8.dp
private val MAX_LABEL_SIZE = 15.dp
private val MAX_SMALL_LABEL_SIZE = 10.dp
private val MIN_LABEL_SIZE = 6.sp
private val SMALLEST_LABEL_SIZE = 4.sp
private val READABLE_WORD_SIZE = 8.sp
private const val LABEL_STEP = 0.5f
private const val ROUNDING_PX = 1f
private val LABEL_INSET = 1.dp
