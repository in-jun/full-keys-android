package dev.injun.fullkeys.ui.keyboard

import android.content.res.Configuration
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.tappableElement
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntRect
import dev.injun.fullkeys.core.layout.Geometry
import dev.injun.fullkeys.core.layout.KeyCap
import dev.injun.fullkeys.core.layout.KeyboardLayout
import dev.injun.fullkeys.core.layout.RowSize
import dev.injun.fullkeys.core.layout.Shapes
import dev.injun.fullkeys.core.layout.outline
import dev.injun.fullkeys.ime.FnState
import dev.injun.fullkeys.settings.KeyboardSettings
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
 * A [preview] is a picture of the keyboard inside a screen rather than the keyboard itself:
 * it is not padded clear of the navigation bar, it does not float, and it takes no touches,
 * so a finger on it scrolls the screen it is in rather than pressing keys that go nowhere.
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
    modifier: Modifier = Modifier,
    onPress: (pointer: Long, key: KeyCap) -> Unit = { _, _ -> },
    onRelease: (pointer: Long) -> Unit = {},
    preview: Boolean = false,
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

    val floating = settings.floating && !preview
    val insets = if (preview) PaddingValues() else keyboardInsets()

    // The floating board is placed inside the whole window; a docked one fills the width it
    // is given, and the caller's modifier belongs to the board either way.
    val windowHeight = LocalWindowInfo.current.containerSize.height
    BoxWithConstraints(
        if (floating) {
            // As tall as the window by measure, not by filling: an input method's view is
            // given no height to fill, so a board asked to fill it was given exactly its own
            // height and had nowhere to go. Inside that, the board moves in what the screen
            // actually leaves free, never under the system's own buttons.
            Modifier
                .fillMaxWidth()
                .height(with(density) { windowHeight.toDp() })
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
        val resizeNow by rememberUpdatedState(resize)
        val floatingNow by rememberUpdatedState(floating)
        val geometryNow by rememberUpdatedState(geometry)
        val boardSizeNow by rememberUpdatedState(boardSize)
        val placeNow by rememberUpdatedState(Offset(settings.floatX, settings.floatY))
        val moveNow by rememberUpdatedState(onMove)

        // Both drags are measured on the screen, since what is dragged moves under the finger.
        // The height a drag of the top edge arrives at: up grows the keyboard, down shrinks it.
        fun heightAfter(startRow: Float, startRawY: Float, rawY: Float): Float =
            (startRow + (startRawY - rawY) / density.density / rows).coerceIn(shownRange)

        // Where a floating board stands after a drag, as a share of the room it has to move in.
        fun placeAfter(startAt: Offset, startRaw: Offset, raw: Offset): Offset {
            val free = IntSize(
                (screen.width - boardSizeNow.width).coerceAtLeast(1),
                (screen.height - boardSizeNow.height).coerceAtLeast(1),
            )
            return Offset(
                (startAt.x + (raw.x - startRaw.x) / free.width).coerceIn(0f, 1f),
                (startAt.y + (raw.y - startRaw.y) / free.height).coerceIn(0f, 1f),
            )
        }

        // Dragging the handle on the bar drags the top edge of the keyboard, floating or not.
        val resizeDrag = if (resize == null) {
            Modifier
        } else {
            Modifier.pointerInput(resize, shownRange, rows, density) {
                awaitEachGesture {
                    awaitFirstDown()
                    val startY = currentEvent.motionEvent?.rawY ?: return@awaitEachGesture
                    val start = currentRowHeightDp
                    do {
                        val event = awaitPointerEvent()
                        event.motionEvent?.let { motion ->
                            val rowHeight = heightAfter(start, startY, motion.rawY)
                            if (rowHeight != currentRowHeightDp) resize.onRowHeight(rowHeight)
                        }
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                }
            }
        }

        // Dragging the bar of a floating keyboard carries the board with it.
        val moveDrag = if (resize == null || !floating) {
            Modifier
        } else {
            Modifier.pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    val startRaw = currentEvent.motionEvent?.let { Offset(it.rawX, it.rawY) } ?: return@awaitEachGesture
                    val startAt = placeNow
                    do {
                        val event = awaitPointerEvent()
                        event.motionEvent?.let { motion ->
                            val at = placeAfter(startAt, startRaw, Offset(motion.rawX, motion.rawY))
                            moveNow(at.x, at.y)
                        }
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                }
            }
        }

        // One handler for the keys, kept through resizing so that the finger that held Fn
        // to begin it goes on as the drag without lifting: it moves a floating board and
        // drags the top edge of a docked one. Keyed on the shape alone; a geometry that
        // changes under a drag, as it does when the drag is what resizes it, must not
        // restart the handler mid-gesture.
        val keysInput = Modifier.pointerInput(shape, held) {
            awaitPointerEventScope {
                var dragFrom: Offset? = null
                var rowAtStart = 0f
                var placeAtStart = Offset.Zero
                while (true) {
                    val event = awaitPointerEvent()
                    val resizing = resizeNow
                    if (resizing != null) {
                        val motion = event.motionEvent
                        if (motion != null && event.changes.any { it.pressed }) {
                            val raw = Offset(motion.rawX, motion.rawY)
                            val from = dragFrom ?: raw.also {
                                dragFrom = it
                                rowAtStart = currentRowHeightDp
                                placeAtStart = placeNow
                            }
                            if (floatingNow) {
                                val at = placeAfter(placeAtStart, from, raw)
                                moveNow(at.x, at.y)
                            } else {
                                val rowHeight = heightAfter(rowAtStart, from.y, raw.y)
                                if (rowHeight != currentRowHeightDp) resizing.onRowHeight(rowHeight)
                            }
                        } else {
                            dragFrom = null
                        }
                        event.changes.forEach { it.consume() }
                        continue
                    }
                    dragFrom = null
                    for (change in event.changes) {
                        val id = change.id.value
                        if (change.changedToDownIgnoreConsumed()) {
                            geometryNow.keyAt(change.position.x, change.position.y)?.let { key ->
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
        val keysArea = Modifier
            .fillMaxWidth()
            .height(with(density) { geometry.height.toDp() })

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
                            handleModifier = resizeDrag,
                        )
                    }
                }
            }
            Box(
                when {
                    preview -> keysArea
                    resize != null -> keysArea.then(keysInput).alpha(RESTING_KEYS_ALPHA)
                    else -> keysArea.then(keysInput)
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
            }
        }
        }
    }
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

/** How the keys look while the keyboard is being resized, when they press nothing. */
private const val RESTING_KEYS_ALPHA = 0.55f
