package dev.injun.fullkeys.core

/**
 * A physical key, named for its place on a hardware keyboard rather than for what it
 * types.
 *
 * Whatever receives a key decides what it means. A letter key sends the letter key, and
 * the receiving system's layout and input method turn it into a Latin letter, a Cyrillic
 * one or a Hangul jamo; the right Alt key sends right Alt, and a system that switches
 * input languages on right Alt switches. Nothing here knows a character.
 */
enum class KeyId {
    ESCAPE,
    F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12,
    PRINT_SCREEN,
    PAUSE,
    FORWARD_DELETE,
    INSERT,

    GRAVE,
    DIGIT_1, DIGIT_2, DIGIT_3, DIGIT_4, DIGIT_5, DIGIT_6, DIGIT_7, DIGIT_8, DIGIT_9, DIGIT_0,
    MINUS,
    EQUALS,

    /** The key right of Equals on JIS keyboards. */
    YEN,
    BACKSPACE,

    TAB,
    Q, W, E, R, T, Y, U, I, O, P,
    LEFT_BRACKET,
    RIGHT_BRACKET,

    /** Above Enter on ANSI keyboards; left of the lower part of Enter on ISO and JIS ones. */
    BACKSLASH,

    CAPS_LOCK,
    A, S, D, F, G, H, J, K, L,
    SEMICOLON,
    APOSTROPHE,
    ENTER,

    SHIFT_LEFT,

    /** The key between left Shift and Z on ISO and ABNT2 keyboards. */
    INTL_BACKSLASH,
    Z, X, C, V, B, N, M,
    COMMA,
    PERIOD,
    SLASH,

    /** The key left of right Shift on JIS and ABNT2 keyboards. */
    RO,
    SHIFT_RIGHT,

    CTRL_LEFT,
    CTRL_RIGHT,
    ALT_LEFT,
    ALT_RIGHT,
    META_LEFT,
    SPACE,

    /** JIS keys beside the space bar. */
    MUHENKAN,
    HENKAN,
    KATAKANA_HIRAGANA,

    LEFT,
    DOWN,
    UP,
    RIGHT,
    HOME,
    END,
    PAGE_UP,
    PAGE_DOWN,

    /**
     * Switches the layer of the next key and is never sent. Nothing that receives keys has
     * a code for it, exactly as a hardware keyboard's Fn never reaches the operating system.
     */
    FN,
    ;

    val isModifier: Boolean
        get() = this in MODIFIERS

    /**
     * Held down, this key repeats, as it would on a hardware keyboard. Modifiers only
     * change other keys, Fn never leaves the phone, and a key that toggles a mode, such as
     * Caps Lock or a kana key, would flip that mode back and forth for as long as it was
     * held.
     */
    val repeats: Boolean
        get() = !isModifier && this != FN && this !in TOGGLES

    private companion object {
        val MODIFIERS = setOf(SHIFT_LEFT, SHIFT_RIGHT, CTRL_LEFT, CTRL_RIGHT, ALT_LEFT, ALT_RIGHT, META_LEFT)
        val TOGGLES = setOf(CAPS_LOCK, MUHENKAN, HENKAN, KATAKANA_HIRAGANA)
    }
}
