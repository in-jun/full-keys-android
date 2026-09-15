package dev.injun.fullkeys.core.input

import dev.injun.fullkeys.core.KeyId
import dev.injun.fullkeys.core.KeyId.A
import dev.injun.fullkeys.core.KeyId.ALT_RIGHT
import dev.injun.fullkeys.core.KeyId.C
import dev.injun.fullkeys.core.KeyId.CTRL_LEFT
import dev.injun.fullkeys.core.KeyId.FN
import dev.injun.fullkeys.core.KeyId.LEFT
import dev.injun.fullkeys.core.KeyId.SHIFT_LEFT
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KeyboardEngineTest {

    private fun down(key: KeyId, vararg modifiers: KeyId) = Stroke(key, Action.DOWN, modifiers.toSet())
    private fun up(key: KeyId, vararg modifiers: KeyId) = Stroke(key, Action.UP, modifiers.toSet())

    @Test
    fun `a key goes down with the finger and up when it lifts`() {
        val engine = KeyboardEngine()

        assertEquals(listOf(down(C)), engine.press(1, C))
        assertEquals(listOf(up(C)), engine.release(1))
    }

    // A tap on right Alt has to arrive as a tap. A system that switches input
    // languages on it acts on the press and release with nothing in between, and a
    // modifier held back until the next key would reach it as the start of a shortcut.
    @Test
    fun `right Alt tapped on its own arrives as a tap`() {
        val engine = KeyboardEngine()

        assertEquals(listOf(down(ALT_RIGHT, ALT_RIGHT)), engine.press(1, ALT_RIGHT))
        assertEquals(listOf(up(ALT_RIGHT)), engine.release(1))
    }

    @Test
    fun `a shortcut held with two fingers is sent as a hardware keyboard sends it`() {
        val engine = KeyboardEngine()

        val strokes = engine.press(1, CTRL_LEFT) + engine.press(2, C) + engine.release(2) + engine.release(1)

        assertEquals(
            listOf(down(CTRL_LEFT, CTRL_LEFT), down(C, CTRL_LEFT), up(C, CTRL_LEFT), up(CTRL_LEFT)),
            strokes,
        )
    }

    @Test
    fun `Fn tapped once moves the next arrow to its layer and then lets go`() {
        val engine = KeyboardEngine()

        assertEquals(emptyList<Stroke>(), engine.press(1, FN) + engine.release(1), "Fn sends nothing")
        assertEquals(listOf(down(KeyId.HOME), up(KeyId.HOME)), engine.press(2, LEFT) + engine.release(2))
        assertEquals(listOf(down(LEFT), up(LEFT)), engine.press(3, LEFT) + engine.release(3))
    }

    @Test
    fun `Fn held on its own can be taken back without touching the latch`() {
        val engine = KeyboardEngine()
        engine.press(1, FN)
        engine.release(1)

        engine.press(2, FN)
        assertTrue(engine.withdrawFnHold(2))
        assertEquals(emptyList<Stroke>(), engine.release(2))
        assertEquals(Latch.ONCE, engine.fnLatch, "a hold taken back is not a second tap")
    }

    @Test
    fun `Fn held for another key cannot be taken back`() {
        val engine = KeyboardEngine()

        engine.press(1, FN)
        engine.press(2, LEFT)
        engine.release(2)

        assertFalse(engine.withdrawFnHold(1))
    }

    // The release has to lift what the press sent. Lifting Fn first and then the
    // arrow would otherwise send Left up after Home down, and Home would stay held.
    @Test
    fun `an arrow pressed under Fn lifts the key it sent even after Fn lets go`() {
        val engine = KeyboardEngine()

        engine.press(1, FN)
        assertEquals(listOf(down(KeyId.END)), engine.press(2, KeyId.RIGHT))
        engine.release(1)

        assertEquals(listOf(up(KeyId.END)), engine.release(2))
        assertEquals(Latch.OFF, engine.fnLatch, "Fn held through a key must not latch")
    }

    @Test
    fun `Fn tapped twice stays on until tapped again`() {
        val engine = KeyboardEngine()

        repeat(2) { engine.press(1, FN); engine.release(1) }
        assertEquals(Latch.LOCKED, engine.fnLatch)
        assertEquals(listOf(down(KeyId.HOME), up(KeyId.HOME)), engine.press(2, LEFT) + engine.release(2))
        assertEquals(listOf(down(KeyId.END), up(KeyId.END)), engine.press(3, KeyId.RIGHT) + engine.release(3))

        engine.press(4, FN)
        engine.release(4)
        assertEquals(listOf(down(LEFT), up(LEFT)), engine.press(5, LEFT) + engine.release(5))
    }

    // Holding a modifier while tapping Fn is one hand on Ctrl and the other reaching
    // for Fn. The Ctrl is a real hold and must not be released by a key it never saw.
    @Test
    fun `Fn under a held modifier still latches and the modifier stays down`() {
        val engine = KeyboardEngine()

        engine.press(1, CTRL_LEFT)
        engine.press(2, FN)
        engine.release(2)

        assertEquals(listOf(down(KeyId.HOME, CTRL_LEFT)), engine.press(3, LEFT))
        assertEquals(listOf(up(KeyId.HOME, CTRL_LEFT)), engine.release(3))
        assertTrue(engine.isDown(CTRL_LEFT))
    }

    @Test
    fun `a second finger on a key already held sends nothing`() {
        val engine = KeyboardEngine()

        engine.press(1, C)
        assertEquals(emptyList<Stroke>(), engine.press(2, C))
        assertEquals(emptyList<Stroke>(), engine.release(2))
        assertEquals(listOf(up(C)), engine.release(1))
    }

    // The keyboard can close under a finger. Whatever is down then stays down,
    // repeating, with nothing left on screen that could lift it.
    @Test
    fun `closing the keyboard lifts every key still down`() {
        val engine = KeyboardEngine()

        engine.press(1, FN)
        engine.release(1)
        engine.press(2, SHIFT_LEFT)
        engine.press(3, CTRL_LEFT)
        engine.press(4, C)

        val strokes = engine.releaseAll()

        assertEquals(setOf(C, SHIFT_LEFT, CTRL_LEFT), strokes.map { it.key }.toSet())
        assertTrue(strokes.all { it.action == Action.UP })
        assertEquals(C, strokes.first().key, "a key lifted after its modifiers arrives as a plain key")
        assertEquals(Latch.OFF, engine.fnLatch, "Fn stayed on for a keyboard that is no longer showing")
        assertEquals(emptyList<Stroke>(), engine.release(4), "a finger lifted after closing sent a second release")
    }

    @Test
    fun `the modifiers held now are what a repeat should carry`() {
        val engine = KeyboardEngine()
        engine.press(1, SHIFT_LEFT)
        engine.press(2, A)
        assertEquals(setOf(SHIFT_LEFT), engine.heldModifiers(), "Shift is held while the letter is")
        engine.release(1)
        assertEquals(emptySet<KeyId>(), engine.heldModifiers(), "Shift lifted while the letter is still held")
    }
}
