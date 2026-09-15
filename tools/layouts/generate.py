#!/usr/bin/env python3
"""
Generates core-keys' layouts.tsv from xkeyboard-config.

Every layout in xkeyboard-config's registry becomes one keyboard: the characters its
system types with each key, without and with Shift, read through libxkbcommon so that
includes and overrides are resolved exactly as a real system resolves them.

Two decisions are made from that data rather than written down per language:

- Shape. A keyboard needs a key that ANSI keyboards lack when that key carries a legend no
  other key carries: the key right of Equals (JIS), the key left of right Shift (ABNT2) or
  the key beside left Shift (ISO). Otherwise it is ANSI. Only printed legends count, so
  the decision is about exactly what the caps would show.
- Legends are what would be printed. A combining mark (a vowel sign, a tone mark, the
  accent a dead key adds) is printed on a no-break space, the base Unicode gives for
  showing a mark on its own. A dotted circle reads better where a font has one, but the
  default Latin font does not, and a mark and its base drawn from two fonts do not join.
- Second script. When most letter keys type a non-Latin script, the caps carry the US
  Latin legends with that script beside them, as those countries' keyboards do. Scripts
  typed through an input method rather than by the layout itself (Hangul, Zhuyin, kana)
  come from SCRIPT_SOURCES.

Run it on a system with xkeyboard-config and libxkbcommon installed:

    python3 tools/layouts/generate.py
"""
import ctypes
import ctypes.util
import pathlib
import subprocess
import unicodedata
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[2]
OUTPUT = ROOT / "core-keys/src/main/resources/dev/injun/fullkeys/core/layout/layouts.tsv"
TABLES = pathlib.Path(__file__).resolve().parent / "tables"
REGISTRY = pathlib.Path("/usr/share/X11/xkb/rules/evdev.xml")

# Layouts that are not a keyboard for a language: a placeholder and braille chording.
SKIPPED = {"custom", "brai"}

LATIN_COMPANION = "us"

# Scripts whose legends do not come from the layout's own first group.
SCRIPT_SOURCES = {
    "jp": ("xkb", "jp", "kana"),
    "kr": ("table", "hangul-2set.tsv"),
    "tw": ("table", "bopomofo-dachen.tsv"),
}

# Keys that can carry a character, by Linux scan code. Names match core's KeyId.
CHARACTER_KEYS = {
    "GRAVE": 41, "DIGIT_1": 2, "DIGIT_2": 3, "DIGIT_3": 4, "DIGIT_4": 5, "DIGIT_5": 6, "DIGIT_6": 7,
    "DIGIT_7": 8, "DIGIT_8": 9, "DIGIT_9": 10, "DIGIT_0": 11, "MINUS": 12, "EQUALS": 13, "YEN": 124,
    **{c: 16 + i for i, c in enumerate("QWERTYUIOP")}, "LEFT_BRACKET": 26, "RIGHT_BRACKET": 27,
    **{c: 30 + i for i, c in enumerate("ASDFGHJKL")}, "SEMICOLON": 39, "APOSTROPHE": 40, "BACKSLASH": 43,
    "INTL_BACKSLASH": 86, **{c: 44 + i for i, c in enumerate("ZXCVBNM")}, "COMMA": 51, "PERIOD": 52,
    "SLASH": 53, "RO": 89,
}
LETTER_KEYS = list("QWERTYUIOPASDFGHJKLZXCVBNM")

# The character keys each shape has. The extra keys are what the shape is detected by.
ANSI_KEYS = [k for k in CHARACTER_KEYS if k not in ("YEN", "INTL_BACKSLASH", "RO")]
SHAPE_KEYS = {
    "ANSI": ANSI_KEYS,
    "ISO": ANSI_KEYS + ["INTL_BACKSLASH"],
    "ABNT2": ANSI_KEYS + ["INTL_BACKSLASH", "RO"],
    "JIS": ANSI_KEYS + ["YEN", "RO"],
}
DETECTION = [("JIS", "YEN"), ("ABNT2", "RO"), ("ISO", "INTL_BACKSLASH")]

# Dead keys have no character of their own; keyboards print the accent they add.
DEAD_KEYS = {
    "dead_acute": "´", "dead_grave": "`", "dead_circumflex": "^", "dead_diaeresis": "¨", "dead_tilde": "~",
    "dead_cedilla": "¸", "dead_abovering": "°", "dead_caron": "ˇ", "dead_macron": "¯", "dead_doubleacute": "˝",
    "dead_ogonek": "˛", "dead_breve": "˘", "dead_abovedot": "˙", "dead_belowdot": "̣", "dead_hook": "̉",
    "dead_horn": "̛", "dead_iota": "ͅ", "dead_greek": "µ", "dead_stroke": "/", "dead_currency": "¤",
}


class RuleNames(ctypes.Structure):
    _fields_ = [(name, ctypes.c_char_p) for name in ("rules", "model", "layout", "variant", "options")]


xkb = ctypes.CDLL(ctypes.util.find_library("xkbcommon"))
xkb.xkb_context_new.restype = ctypes.c_void_p
xkb.xkb_keymap_new_from_names.restype = ctypes.c_void_p
xkb.xkb_keymap_new_from_names.argtypes = [ctypes.c_void_p, ctypes.POINTER(RuleNames), ctypes.c_int]
xkb.xkb_keymap_unref.argtypes = [ctypes.c_void_p]
xkb.xkb_keymap_key_get_syms_by_level.argtypes = [
    ctypes.c_void_p, ctypes.c_uint32, ctypes.c_uint32, ctypes.c_uint32, ctypes.POINTER(ctypes.POINTER(ctypes.c_uint32))]
xkb.xkb_keysym_to_utf32.restype = ctypes.c_uint32
xkb.xkb_keysym_get_name.argtypes = [ctypes.c_uint32, ctypes.c_char_p, ctypes.c_size_t]
context = xkb.xkb_context_new(0)


NO_BREAK_SPACE = "\u00a0"


def printed(char):
    """The legend for a character: a combining mark goes on a no-break space."""
    if char is None:
        return None
    return NO_BREAK_SPACE + char if unicodedata.category(char[0]).startswith("M") else char


def character(keysym):
    code = xkb.xkb_keysym_to_utf32(keysym)
    if code:
        char = chr(code)
        return None if unicodedata.category(char)[0] in "CZ" else printed(char)
    name = ctypes.create_string_buffer(64)
    xkb.xkb_keysym_get_name(keysym, name, 64)
    return printed(DEAD_KEYS.get(name.value.decode()))


def xkb_legends(layout, variant=""):
    """{key: (base, shifted)} for every character key the layout types something with."""
    names = RuleNames(b"evdev", b"pc105", layout.encode(), variant.encode(), None)
    keymap = xkb.xkb_keymap_new_from_names(context, ctypes.byref(names), 0)
    if not keymap:
        raise SystemExit(f"xkbcommon has no keymap for {layout}({variant})")
    legends = {}
    for key, scan in CHARACTER_KEYS.items():
        levels = []
        for level in (0, 1):
            syms = ctypes.POINTER(ctypes.c_uint32)()
            count = xkb.xkb_keymap_key_get_syms_by_level(keymap, scan + 8, 0, level, ctypes.byref(syms))
            levels.append(character(syms[0]) if count > 0 else None)
        if levels[0]:
            legends[key] = (levels[0], levels[1] if levels[1] != levels[0] else None)
    xkb.xkb_keymap_unref(keymap)
    return legends


def table(name):
    rows = (line.split("\t") for line in (TABLES / name).read_text("utf-8").splitlines() if line and not line.startswith("#"))
    return {key: char for key, char in rows}


def is_latin(legend):
    return unicodedata.name(legend[-1], "").startswith("LATIN")


def detect_shape(printed):
    """printed: {key: set of legends printed on it}."""
    def unique_on(key):
        elsewhere = set().union(*(chars for other, chars in printed.items() if other != key))
        return bool(printed.get(key, set()) - elsewhere)

    return next((shape for shape, key in DETECTION if unique_on(key)), "ANSI")


def registry():
    root = ET.parse(REGISTRY).getroot()
    for layout in root.find("layoutList").findall("layout"):
        item = layout.find("configItem")
        name = item.find("name").text
        if name in SKIPPED:
            continue
        yield (
            name,
            item.find("description").text,
            sorted(e.text for e in item.findall("countryList/iso3166Id")),
            sorted(e.text for e in item.findall("languageList/iso639Id")),
        )


def main():
    companion = xkb_legends(LATIN_COMPANION)
    version = subprocess.run(["dpkg-query", "-W", "-f=${Version}", "xkb-data"], capture_output=True, text=True).stdout
    lines = [
        f"# Generated by tools/layouts/generate.py from xkeyboard-config {version or '(unknown version)'}; do not edit.",
        "# xkeyboard-config is distributed under MIT/X11-style licenses.",
    ]
    for name, description, countries, languages in registry():
        own = xkb_legends(name)
        letters = [own[k][0] for k in LETTER_KEYS if k in own]
        latin = sum(map(is_latin, letters)) * 2 >= len(letters)

        source = SCRIPT_SOURCES.get(name)
        if source and source[0] == "xkb":
            script = {k: base for k, (base, _) in xkb_legends(source[1], source[2]).items()}
        elif source:
            script = table(source[1])
        elif not latin:
            script = {k: base for k, (base, _) in own.items()}
        else:
            script = {}
        primary = own if latin else {**own, **companion}

        printed = {
            key: {c for c in (*primary.get(key, ()), script.get(key)) if c}
            for key in CHARACTER_KEYS
        }
        shape = detect_shape(printed)
        lines.append("\t".join(["layout", name, shape, description, ",".join(countries), ",".join(languages)]))
        for key in SHAPE_KEYS[shape]:
            if key not in primary:
                continue
            base, shifted = primary[key]
            secondary = script.get(key)
            if secondary in (base, shifted):
                secondary = None
            lines.append("\t".join(["key", key, base, shifted or "", secondary or ""]))

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text("\n".join(lines) + "\n", "utf-8")
    print(f"wrote {sum(1 for l in lines if l.startswith('layout'))} layouts to {OUTPUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
