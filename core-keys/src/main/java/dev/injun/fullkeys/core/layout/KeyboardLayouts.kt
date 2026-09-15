package dev.injun.fullkeys.core.layout

import dev.injun.fullkeys.core.KeyId

/**
 * What is printed on one key.
 *
 * [base] and [shifted] are the characters the layout's system types with the key, without
 * and with Shift. [secondary] is the character of a second script, printed small, for
 * languages whose keyboards carry Latin letters and their own script side by side.
 */
data class Legend(val base: String, val shifted: String? = null, val secondary: String? = null)

/**
 * A keyboard someone can choose: a physical [shape] and what is printed on its keys.
 *
 * Only the legends change between layouts; the keys sent are the same physical keys, and
 * the system receiving them decides what they type. Choosing the layout that system uses
 * is what makes the caps match what comes out.
 */
data class KeyboardLayout(
    val id: String,
    val name: String,
    val shapeId: ShapeId,
    /** ISO 3166-1 alpha-2 codes of the countries this layout is for. */
    val countries: Set<String>,
    /** ISO 639-2/T codes of the languages this layout is for. */
    val languages: Set<String>,
    val legends: Map<KeyId, Legend>,
) {
    val shape: Shape get() = Shapes.of(shapeId)
}

/**
 * Every layout, read from the table generated from xkeyboard-config by
 * `tools/layouts/generate.py`. Nothing about a particular language lives in code.
 */
object KeyboardLayouts {

    const val DEFAULT_ID = "us"

    private const val RESOURCE = "/dev/injun/fullkeys/core/layout/layouts.tsv"

    val all: List<KeyboardLayout> by lazy {
        val stream = checkNotNull(KeyboardLayouts::class.java.getResourceAsStream(RESOURCE)) { "missing $RESOURCE" }
        stream.bufferedReader(Charsets.UTF_8).useLines { parse(it) }
    }

    fun byId(id: String?): KeyboardLayout = all.firstOrNull { it.id == id } ?: all.first { it.id == DEFAULT_ID }

    /**
     * The layout a phone set to [language] (ISO 639-2/T) in [country] (ISO 3166-1 alpha-2)
     * most likely types on: one made for both, then one for the country, then one for the
     * language. Among several, the one made for the fewest places is the most specific.
     */
    fun forLocale(language: String, country: String): KeyboardLayout {
        val tiers = listOf<(KeyboardLayout) -> Boolean>(
            { country in it.countries && language in it.languages },
            { country in it.countries },
            { language in it.languages },
        )
        for (matches in tiers) {
            all.filter(matches)
                .minWithOrNull(compareBy<KeyboardLayout>({ it.countries.size }, { it.languages.size }))
                ?.let { return it }
        }
        return byId(DEFAULT_ID)
    }

    /**
     * The table is line-based and tab-separated:
     *
     * ```
     * layout  <id>  <shape>  <name>  <countries, comma-separated>  <languages, comma-separated>
     * key     <KeyId>  <base>  <shifted>  <secondary>
     * ```
     *
     * Key lines belong to the layout line above them; empty fields are absent legends, and
     * lines starting with `#` are comments.
     */
    internal fun parse(lines: Sequence<String>): List<KeyboardLayout> {
        val layouts = mutableListOf<KeyboardLayout>()
        var header: List<String>? = null
        val legends = LinkedHashMap<KeyId, Legend>()

        fun flush() {
            val h = header ?: return
            layouts += KeyboardLayout(
                id = h[1],
                name = h[3],
                shapeId = ShapeId.valueOf(h[2]),
                countries = h[4].split(',').filter { it.isNotEmpty() }.toSet(),
                languages = h[5].split(',').filter { it.isNotEmpty() }.toSet(),
                legends = LinkedHashMap(legends),
            )
            legends.clear()
        }

        for (line in lines) {
            if (line.isEmpty() || line.startsWith("#")) continue
            val fields = line.split('\t')
            when (fields[0]) {
                "layout" -> {
                    flush()
                    header = fields.also { require(it.size == 6) { "bad layout line: $line" } }
                }

                "key" -> {
                    require(fields.size == 5 && header != null) { "bad key line: $line" }
                    legends[KeyId.valueOf(fields[1])] = Legend(
                        base = fields[2],
                        shifted = fields[3].ifEmpty { null },
                        secondary = fields[4].ifEmpty { null },
                    )
                }

                else -> error("unknown line: $line")
            }
        }
        flush()
        return layouts
    }
}
