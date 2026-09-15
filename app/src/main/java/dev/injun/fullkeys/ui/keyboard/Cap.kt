package dev.injun.fullkeys.ui.keyboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.tappableElement
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.injun.fullkeys.core.KeyId
import dev.injun.fullkeys.core.input.Latch
import dev.injun.fullkeys.core.layout.Frame
import dev.injun.fullkeys.core.layout.Geometry
import dev.injun.fullkeys.core.layout.KeyCap
import dev.injun.fullkeys.core.layout.KeyKind
import dev.injun.fullkeys.core.layout.KeyboardLayout
import dev.injun.fullkeys.core.layout.Point
import dev.injun.fullkeys.core.layout.outline
import dev.injun.fullkeys.ime.FnState
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * One cap: its outline, filled, and its labels in its first row.
 *
 * Every cap is drawn from [outline], so a two-row Enter is the same kind of shape as any
 * other key rather than a special case, and its labels sit in its upper part as they do on
 * a printed keycap.
 */
@Composable
internal fun Cap(
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
internal data class ShortNames(val modifiers: Boolean, val functions: Boolean)

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
internal enum class Role {
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
internal data class LabelSizes(
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
internal fun labelSizesFor(
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

private val MAX_LABEL_SIZE = 15.dp
private val MAX_SMALL_LABEL_SIZE = 10.dp
private val MIN_LABEL_SIZE = 6.sp
private val SMALLEST_LABEL_SIZE = 4.sp
private val READABLE_WORD_SIZE = 8.sp
private const val LABEL_STEP = 0.5f
private const val ROUNDING_PX = 1f
private val LABEL_INSET = 1.dp
