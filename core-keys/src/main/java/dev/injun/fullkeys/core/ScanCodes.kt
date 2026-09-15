package dev.injun.fullkeys.core

/**
 * The Linux input scan code a hardware keyboard reports for this key
 * (`linux/input-event-codes.h`).
 *
 * Key codes alone do not tell every key apart: the key beside left Shift on an ISO
 * keyboard and the Backslash key share one. The scan code is what keeps them apart, and
 * sending it with every key is what a hardware keyboard does.
 */
val KeyId.scanCode: Int
    get() = when (this) {
        KeyId.ESCAPE -> 1
        KeyId.F1 -> 59
        KeyId.F2 -> 60
        KeyId.F3 -> 61
        KeyId.F4 -> 62
        KeyId.F5 -> 63
        KeyId.F6 -> 64
        KeyId.F7 -> 65
        KeyId.F8 -> 66
        KeyId.F9 -> 67
        KeyId.F10 -> 68
        KeyId.F11 -> 87
        KeyId.F12 -> 88
        KeyId.PRINT_SCREEN -> 99
        KeyId.PAUSE -> 119
        KeyId.FORWARD_DELETE -> 111
        KeyId.INSERT -> 110

        KeyId.GRAVE -> 41
        KeyId.DIGIT_1 -> 2
        KeyId.DIGIT_2 -> 3
        KeyId.DIGIT_3 -> 4
        KeyId.DIGIT_4 -> 5
        KeyId.DIGIT_5 -> 6
        KeyId.DIGIT_6 -> 7
        KeyId.DIGIT_7 -> 8
        KeyId.DIGIT_8 -> 9
        KeyId.DIGIT_9 -> 10
        KeyId.DIGIT_0 -> 11
        KeyId.MINUS -> 12
        KeyId.EQUALS -> 13
        KeyId.YEN -> 124
        KeyId.BACKSPACE -> 14

        KeyId.TAB -> 15
        KeyId.Q -> 16
        KeyId.W -> 17
        KeyId.E -> 18
        KeyId.R -> 19
        KeyId.T -> 20
        KeyId.Y -> 21
        KeyId.U -> 22
        KeyId.I -> 23
        KeyId.O -> 24
        KeyId.P -> 25
        KeyId.LEFT_BRACKET -> 26
        KeyId.RIGHT_BRACKET -> 27
        KeyId.BACKSLASH -> 43

        KeyId.CAPS_LOCK -> 58
        KeyId.A -> 30
        KeyId.S -> 31
        KeyId.D -> 32
        KeyId.F -> 33
        KeyId.G -> 34
        KeyId.H -> 35
        KeyId.J -> 36
        KeyId.K -> 37
        KeyId.L -> 38
        KeyId.SEMICOLON -> 39
        KeyId.APOSTROPHE -> 40
        KeyId.ENTER -> 28

        KeyId.SHIFT_LEFT -> 42
        KeyId.INTL_BACKSLASH -> 86
        KeyId.Z -> 44
        KeyId.X -> 45
        KeyId.C -> 46
        KeyId.V -> 47
        KeyId.B -> 48
        KeyId.N -> 49
        KeyId.M -> 50
        KeyId.COMMA -> 51
        KeyId.PERIOD -> 52
        KeyId.SLASH -> 53
        KeyId.RO -> 89
        KeyId.SHIFT_RIGHT -> 54

        KeyId.CTRL_LEFT -> 29
        KeyId.CTRL_RIGHT -> 97
        KeyId.ALT_LEFT -> 56
        KeyId.ALT_RIGHT -> 100
        KeyId.META_LEFT -> 125
        KeyId.SPACE -> 57

        KeyId.MUHENKAN -> 94
        KeyId.HENKAN -> 92
        KeyId.KATAKANA_HIRAGANA -> 93

        KeyId.LEFT -> 105
        KeyId.DOWN -> 108
        KeyId.UP -> 103
        KeyId.RIGHT -> 106
        KeyId.HOME -> 102
        KeyId.END -> 107
        KeyId.PAGE_UP -> 104
        KeyId.PAGE_DOWN -> 109

        KeyId.FN -> 464
    }
