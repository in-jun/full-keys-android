package dev.injun.fullkeys.ime

import android.view.KeyCharacterMap
import android.view.KeyEvent
import dev.injun.fullkeys.core.KeyId
import dev.injun.fullkeys.core.input.Action
import dev.injun.fullkeys.core.input.Stroke
import dev.injun.fullkeys.core.scanCode

/**
 * Builds the event a hardware keyboard would deliver for [stroke], scan code included.
 *
 * Apps that read raw key events act on the key code and the meta state, so the code has
 * to be the physical key and the meta state has to say which side each modifier is on.
 * Android gives the ISO key beside left Shift the same key code as Backslash, as its own
 * hardware keyboard mapping does; the scan code is what tells the two apart. [downTime] is the time of the matching press, and
 * [repeatCount] counts the repeats of a held key the way a hardware keyboard's do.
 */
fun keyEventOf(stroke: Stroke, downTime: Long, eventTime: Long, repeatCount: Int = 0): KeyEvent = KeyEvent(
    downTime,
    eventTime,
    if (stroke.action == Action.DOWN) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP,
    stroke.key.keyCode,
    repeatCount,
    metaStateOf(stroke.modifiers),
    KeyCharacterMap.VIRTUAL_KEYBOARD,
    stroke.key.scanCode,
    KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE,
)

fun metaStateOf(modifiers: Set<KeyId>): Int = modifiers.fold(0) { meta, key ->
    meta or when (key) {
        KeyId.SHIFT_LEFT -> KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        KeyId.SHIFT_RIGHT -> KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_RIGHT_ON
        KeyId.CTRL_LEFT -> KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        KeyId.CTRL_RIGHT -> KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_RIGHT_ON
        KeyId.ALT_LEFT -> KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
        KeyId.ALT_RIGHT -> KeyEvent.META_ALT_ON or KeyEvent.META_ALT_RIGHT_ON
        KeyId.META_LEFT -> KeyEvent.META_META_ON or KeyEvent.META_META_LEFT_ON
        else -> 0
    }
}

val KeyId.keyCode: Int
    get() = when (this) {
        KeyId.ESCAPE -> KeyEvent.KEYCODE_ESCAPE
        KeyId.F1 -> KeyEvent.KEYCODE_F1
        KeyId.F2 -> KeyEvent.KEYCODE_F2
        KeyId.F3 -> KeyEvent.KEYCODE_F3
        KeyId.F4 -> KeyEvent.KEYCODE_F4
        KeyId.F5 -> KeyEvent.KEYCODE_F5
        KeyId.F6 -> KeyEvent.KEYCODE_F6
        KeyId.F7 -> KeyEvent.KEYCODE_F7
        KeyId.F8 -> KeyEvent.KEYCODE_F8
        KeyId.F9 -> KeyEvent.KEYCODE_F9
        KeyId.F10 -> KeyEvent.KEYCODE_F10
        KeyId.F11 -> KeyEvent.KEYCODE_F11
        KeyId.F12 -> KeyEvent.KEYCODE_F12
        KeyId.PRINT_SCREEN -> KeyEvent.KEYCODE_SYSRQ
        KeyId.PAUSE -> KeyEvent.KEYCODE_BREAK
        KeyId.FORWARD_DELETE -> KeyEvent.KEYCODE_FORWARD_DEL
        KeyId.INSERT -> KeyEvent.KEYCODE_INSERT

        KeyId.GRAVE -> KeyEvent.KEYCODE_GRAVE
        KeyId.DIGIT_1 -> KeyEvent.KEYCODE_1
        KeyId.DIGIT_2 -> KeyEvent.KEYCODE_2
        KeyId.DIGIT_3 -> KeyEvent.KEYCODE_3
        KeyId.DIGIT_4 -> KeyEvent.KEYCODE_4
        KeyId.DIGIT_5 -> KeyEvent.KEYCODE_5
        KeyId.DIGIT_6 -> KeyEvent.KEYCODE_6
        KeyId.DIGIT_7 -> KeyEvent.KEYCODE_7
        KeyId.DIGIT_8 -> KeyEvent.KEYCODE_8
        KeyId.DIGIT_9 -> KeyEvent.KEYCODE_9
        KeyId.DIGIT_0 -> KeyEvent.KEYCODE_0
        KeyId.MINUS -> KeyEvent.KEYCODE_MINUS
        KeyId.EQUALS -> KeyEvent.KEYCODE_EQUALS
        KeyId.YEN -> KeyEvent.KEYCODE_YEN
        KeyId.BACKSPACE -> KeyEvent.KEYCODE_DEL

        KeyId.TAB -> KeyEvent.KEYCODE_TAB
        KeyId.Q -> KeyEvent.KEYCODE_Q
        KeyId.W -> KeyEvent.KEYCODE_W
        KeyId.E -> KeyEvent.KEYCODE_E
        KeyId.R -> KeyEvent.KEYCODE_R
        KeyId.T -> KeyEvent.KEYCODE_T
        KeyId.Y -> KeyEvent.KEYCODE_Y
        KeyId.U -> KeyEvent.KEYCODE_U
        KeyId.I -> KeyEvent.KEYCODE_I
        KeyId.O -> KeyEvent.KEYCODE_O
        KeyId.P -> KeyEvent.KEYCODE_P
        KeyId.LEFT_BRACKET -> KeyEvent.KEYCODE_LEFT_BRACKET
        KeyId.RIGHT_BRACKET -> KeyEvent.KEYCODE_RIGHT_BRACKET
        KeyId.BACKSLASH -> KeyEvent.KEYCODE_BACKSLASH

        KeyId.CAPS_LOCK -> KeyEvent.KEYCODE_CAPS_LOCK
        KeyId.A -> KeyEvent.KEYCODE_A
        KeyId.S -> KeyEvent.KEYCODE_S
        KeyId.D -> KeyEvent.KEYCODE_D
        KeyId.F -> KeyEvent.KEYCODE_F
        KeyId.G -> KeyEvent.KEYCODE_G
        KeyId.H -> KeyEvent.KEYCODE_H
        KeyId.J -> KeyEvent.KEYCODE_J
        KeyId.K -> KeyEvent.KEYCODE_K
        KeyId.L -> KeyEvent.KEYCODE_L
        KeyId.SEMICOLON -> KeyEvent.KEYCODE_SEMICOLON
        KeyId.APOSTROPHE -> KeyEvent.KEYCODE_APOSTROPHE
        KeyId.ENTER -> KeyEvent.KEYCODE_ENTER

        KeyId.SHIFT_LEFT -> KeyEvent.KEYCODE_SHIFT_LEFT
        KeyId.INTL_BACKSLASH -> KeyEvent.KEYCODE_BACKSLASH
        KeyId.Z -> KeyEvent.KEYCODE_Z
        KeyId.X -> KeyEvent.KEYCODE_X
        KeyId.C -> KeyEvent.KEYCODE_C
        KeyId.V -> KeyEvent.KEYCODE_V
        KeyId.B -> KeyEvent.KEYCODE_B
        KeyId.N -> KeyEvent.KEYCODE_N
        KeyId.M -> KeyEvent.KEYCODE_M
        KeyId.COMMA -> KeyEvent.KEYCODE_COMMA
        KeyId.PERIOD -> KeyEvent.KEYCODE_PERIOD
        KeyId.SLASH -> KeyEvent.KEYCODE_SLASH
        KeyId.RO -> KeyEvent.KEYCODE_RO
        KeyId.SHIFT_RIGHT -> KeyEvent.KEYCODE_SHIFT_RIGHT

        KeyId.CTRL_LEFT -> KeyEvent.KEYCODE_CTRL_LEFT
        KeyId.CTRL_RIGHT -> KeyEvent.KEYCODE_CTRL_RIGHT
        KeyId.ALT_LEFT -> KeyEvent.KEYCODE_ALT_LEFT
        KeyId.ALT_RIGHT -> KeyEvent.KEYCODE_ALT_RIGHT
        KeyId.META_LEFT -> KeyEvent.KEYCODE_META_LEFT
        KeyId.SPACE -> KeyEvent.KEYCODE_SPACE

        KeyId.MUHENKAN -> KeyEvent.KEYCODE_MUHENKAN
        KeyId.HENKAN -> KeyEvent.KEYCODE_HENKAN
        KeyId.KATAKANA_HIRAGANA -> KeyEvent.KEYCODE_KATAKANA_HIRAGANA

        KeyId.LEFT -> KeyEvent.KEYCODE_DPAD_LEFT
        KeyId.DOWN -> KeyEvent.KEYCODE_DPAD_DOWN
        KeyId.UP -> KeyEvent.KEYCODE_DPAD_UP
        KeyId.RIGHT -> KeyEvent.KEYCODE_DPAD_RIGHT
        KeyId.HOME -> KeyEvent.KEYCODE_MOVE_HOME
        KeyId.END -> KeyEvent.KEYCODE_MOVE_END
        KeyId.PAGE_UP -> KeyEvent.KEYCODE_PAGE_UP
        KeyId.PAGE_DOWN -> KeyEvent.KEYCODE_PAGE_DOWN

        KeyId.FN -> KeyEvent.KEYCODE_FUNCTION
    }
