package dev.injun.fullkeys.ime

import android.view.KeyEvent
import dev.injun.fullkeys.core.KeyId
import dev.injun.fullkeys.core.scanCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class KeyEventsTest {

    // Two keys that send the same code and scan code would type the same thing from two
    // places, and the one that was meant would be unreachable with no error anywhere.
    @Test
    fun `every key sends a code and scan code of its own`() {
        val shared = KeyId.entries.groupBy { it.keyCode to it.scanCode }.filterValues { it.size > 1 }
        assertEquals(emptyMap<Pair<Int, Int>, List<KeyId>>(), shared)
    }

    @Test
    fun `every key has a scan code of its own`() {
        val shared = KeyId.entries.groupBy { it.scanCode }.filterValues { it.size > 1 }
        assertEquals(emptyMap<Int, List<KeyId>>(), shared)
    }

    @Test
    fun `no key sends the unknown code`() {
        assertEquals(emptyList<KeyId>(), KeyId.entries.filter { it.keyCode == KeyEvent.KEYCODE_UNKNOWN })
    }

    // An app that reads the meta state tells left from right by these bits, and a
    // system that switches languages on right Alt only sees right Alt if the bit says so.
    @Test
    fun `the meta state names the side of each modifier`() {
        assertEquals(
            KeyEvent.META_ALT_ON or KeyEvent.META_ALT_RIGHT_ON,
            metaStateOf(setOf(KeyId.ALT_RIGHT)),
        )
        assertEquals(
            KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_RIGHT_ON,
            metaStateOf(setOf(KeyId.CTRL_LEFT, KeyId.SHIFT_RIGHT)),
        )
    }

    @Test
    fun `keys that are not modifiers add nothing to the meta state`() {
        assertEquals(0, metaStateOf(setOf(KeyId.C, KeyId.FN)))
    }
}
