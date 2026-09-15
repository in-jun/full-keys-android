package dev.injun.fullkeys.core.input

import dev.injun.fullkeys.core.KeyId

/** A press or a release, as whatever receives the keys should see it. */
data class Stroke(
    val key: KeyId,
    val action: Action,
    /** Modifiers held down when this stroke is sent, including [key] itself on its own press. */
    val modifiers: Set<KeyId>,
)

enum class Action { DOWN, UP }

/** How Fn stands between touches. */
enum class Latch {
    OFF,

    /** Tapped once: applies to the next key only. */
    ONCE,

    /** Tapped twice: applies until tapped again. */
    LOCKED,
}

/**
 * Turns touches into the presses and releases a physical keyboard would produce.
 *
 * A key goes down when a finger lands and up when it lifts, with nothing repeated in
 * between. Repeating a held key is left to the caller, which repeats it the way a
 * hardware keyboard's events do.
 *
 * Modifiers are real keys: down with the finger, up when it lifts, so a modifier
 * tapped on its own arrives as a tap. Systems act on those taps, switching an
 * input language on right Alt or opening an overview on Super, and a modifier that
 * waited for the next key would reach them as the start of a shortcut instead. A
 * shortcut is two fingers, as it is on a hardware keyboard.
 *
 * Fn is never sent; it only changes what other keys send. Held, it applies while held;
 * tapped, to the next key; tapped twice, until it is tapped again.
 */
class KeyboardEngine {

    private class Press(val key: KeyId, val sent: KeyId?) {
        /** Another key went down while this one was held, so its release is not a tap. */
        var usedAsHold = false
    }

    private val presses = LinkedHashMap<Long, Press>()
    private val down = LinkedHashSet<KeyId>()

    /** How Fn stands between touches. */
    var fnLatch: Latch = Latch.OFF
        private set

    /** True when the next key goes through the Fn layer. */
    val fnActive: Boolean
        get() = presses.values.any { it.key == KeyId.FN } || fnLatch != Latch.OFF

    /** True while [key] is held down. */
    fun isDown(key: KeyId): Boolean = key in down

    fun press(pointer: Long, key: KeyId): List<Stroke> {
        if (pointer in presses) return emptyList()
        presses.values.forEach { it.usedAsHold = true }

        // A second finger on a key that is already held adds nothing a keyboard could send.
        if (presses.values.any { it.key == key }) {
            presses[pointer] = Press(key, sent = null)
            return emptyList()
        }

        if (key == KeyId.FN) {
            presses[pointer] = Press(key, sent = null)
            return emptyList()
        }
        val sent = if (fnActive) FnLayer.map(key) else key
        presses[pointer] = Press(key, sent)
        down += sent
        return listOf(Stroke(sent, Action.DOWN, modifiers()))
    }

    fun release(pointer: Long): List<Stroke> {
        val press = presses.remove(pointer) ?: return emptyList()
        val key = press.key
        if (key == KeyId.FN) {
            if (!press.usedAsHold) fnLatch = fnLatch.next()
            return emptyList()
        }
        val sent = press.sent ?: return emptyList()
        val strokes = lift(sent)
        if (!key.isModifier && fnLatch == Latch.ONCE && presses.values.none { it.key == KeyId.FN }) {
            fnLatch = Latch.OFF
        }
        return strokes
    }

    /**
     * Takes back a press of Fn held on its own, for a hold that means something to the
     * keyboard itself rather than to the keys: its release then sends nothing and Fn is
     * left as it stood before the press. Returns false and changes nothing for any other
     * press, or when another key has been touched since.
     */
    fun withdrawFnHold(pointer: Long): Boolean {
        val press = presses[pointer] ?: return false
        if (press.key != KeyId.FN || press.usedAsHold || presses.size > 1) return false
        presses.remove(pointer)
        return true
    }

    /**
     * Lifts every key still held down. Called when the keyboard goes away with a
     * finger still on it, because a key left down keeps repeating wherever it was sent,
     * with nothing left on screen to lift it.
     */
    fun releaseAll(): List<Stroke> {
        presses.clear()
        fnLatch = Latch.OFF
        val order = down.filterNot { it.isModifier } + down.filter { it.isModifier }
        return order.flatMap { lift(it) }
    }

    private fun lift(key: KeyId): List<Stroke> {
        if (!down.remove(key)) return emptyList()
        return listOf(Stroke(key, Action.UP, modifiers()))
    }

    private fun modifiers(): Set<KeyId> = down.filterTo(LinkedHashSet()) { it.isModifier }

    private fun Latch.next(): Latch = when (this) {
        Latch.OFF -> Latch.ONCE
        Latch.ONCE -> Latch.LOCKED
        Latch.LOCKED -> Latch.OFF
    }
}

/** What a key sends while Fn is active: the keys compact keyboards keep on their arrows and Delete. */
object FnLayer {
    fun map(key: KeyId): KeyId = when (key) {
        KeyId.LEFT -> KeyId.HOME
        KeyId.RIGHT -> KeyId.END
        KeyId.UP -> KeyId.PAGE_UP
        KeyId.DOWN -> KeyId.PAGE_DOWN
        KeyId.FORWARD_DELETE -> KeyId.INSERT
        else -> key
    }
}
