package dev.injun.fullkeys.core.layout

import dev.injun.fullkeys.core.KeyId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KeyboardLayoutsTest {

    private val layouts = KeyboardLayouts.all

    private fun layout(id: String) = KeyboardLayouts.byId(id).also { assertEquals(id, it.id) }

    @Test
    fun `the generated table holds every registry layout with a unique id`() {
        assertTrue(layouts.size >= 90, "only ${layouts.size} layouts")
        assertEquals(layouts.size, layouts.map { it.id }.toSet().size)
    }

    // A legend for a key the shape does not have would be a character nobody can see or
    // press, which is exactly what choosing the shape from the legends is meant to rule out.
    @Test
    fun `every legend is on a key the layout's shape has`() {
        for (layout in layouts) {
            val keys = layout.shape.keys.map { it.id }.toSet()
            val stray = layout.legends.keys - keys
            assertTrue(stray.isEmpty(), "${layout.id} has legends for $stray, which ${layout.shapeId} lacks")
        }
    }

    @Test
    fun `every layout prints something on every letter key`() {
        val letters = "QWERTYUIOPASDFGHJKLZXCVBNM".map { KeyId.valueOf("$it") }
        for (layout in layouts) {
            val blank = letters.filter { it !in layout.legends }
            assertTrue(blank.isEmpty(), "${layout.id} leaves $blank blank")
        }
    }

    @Test
    fun `layouts get the shape their extra keys call for`() {
        assertEquals(ShapeId.ANSI, layout("us").shapeId)
        assertEquals(ShapeId.ISO, layout("de").shapeId)
        assertEquals(ShapeId.ISO, layout("gb").shapeId)
        assertEquals(ShapeId.JIS, layout("jp").shapeId)
        assertEquals(ShapeId.ABNT2, layout("br").shapeId)
    }

    @Test
    fun `legends follow the layout, key by key`() {
        assertEquals(Legend("z", "Z"), layout("de").legends[KeyId.Y])
        assertEquals("a", layout("fr").legends[KeyId.Q]?.base)
        assertEquals("ö", layout("de").legends[KeyId.SEMICOLON]?.base)
    }

    // Keyboards for scripts other than Latin print both, and the Latin legends are the US
    // ones those keyboards carry.
    @Test
    fun `a second script is printed beside the Latin legends`() {
        assertEquals(Legend("q", "Q", "й"), layout("ru").legends[KeyId.Q])
        assertEquals(Legend("q", "Q", "ㅂ"), layout("kr").legends[KeyId.Q])
        assertEquals("ㄆ", layout("tw").legends[KeyId.Q]?.secondary)
        assertEquals("タ", layout("jp").legends[KeyId.Q]?.secondary)
        assertNull(layout("de").legends[KeyId.Q]?.secondary)
    }

    @Test
    fun `the default layout follows the phone's language and country`() {
        assertEquals("kr", KeyboardLayouts.forLocale("kor", "KR").id)
        assertEquals("us", KeyboardLayouts.forLocale("eng", "US").id)
        assertEquals("gb", KeyboardLayouts.forLocale("eng", "GB").id)
        assertEquals("de", KeyboardLayouts.forLocale("deu", "DE").id)
        assertEquals("ch", KeyboardLayouts.forLocale("deu", "CH").id)
        assertEquals("jp", KeyboardLayouts.forLocale("jpn", "JP").id)
        assertEquals("br", KeyboardLayouts.forLocale("por", "BR").id)
    }

    @Test
    fun `a phone set to somewhere no layout names falls back to the default`() {
        assertEquals(KeyboardLayouts.DEFAULT_ID, KeyboardLayouts.forLocale("zzz", "ZZ").id)
        assertEquals(KeyboardLayouts.DEFAULT_ID, KeyboardLayouts.byId("no-such-layout").id)
    }

    @Test
    fun `the table format reads layouts, keys and absent fields`() {
        val parsed = KeyboardLayouts.parse(
            sequenceOf(
                "# comment",
                "layout\txx\tISO\tExample\tAA,BB\tabc",
                "key\tQ\tq\tQ\tㅂ",
                "key\tDIGIT_1\t1\t\t",
            ),
        )
        val only = parsed.single()
        assertEquals(ShapeId.ISO, only.shapeId)
        assertEquals(setOf("AA", "BB"), only.countries)
        assertEquals(Legend("q", "Q", "ㅂ"), only.legends[KeyId.Q])
        assertEquals(Legend("1"), only.legends[KeyId.DIGIT_1])
    }
}
