package com.yash.tracker.domain.nutrition

/**
 * One food as the user wrote it.
 *
 * "2 roti" is two of the measure `roti`; "100 g paneer" is a hundred of the measure `g`; a bare
 * "dal" has no measure at all, which the resolver reads as one of whatever that food's own
 * serving happens to be.
 */
data class ParsedPortion(
    val quantity: Double,
    val unit: String?,
    val name: String,
)

/**
 * Takes typed meal text apart far enough to ask the food tables about it.
 *
 * Deliberately shallow: it understands a count, a measure and a name, and gives up on anything
 * else. Giving up is cheap — the caller then sends the text to the model, which is what it
 * would have done anyway. The only outcome worth avoiding is a confident wrong split, so every
 * rule here is exact rather than fuzzy.
 */
object MealTextParser {

    /** What a bare number of grams or millilitres is called once normalised. */
    const val GRAMS = "g"
    const val MILLILITRES = "ml"

    private val BASE_UNITS = mapOf(
        "g" to GRAMS, "gm" to GRAMS, "gms" to GRAMS, "gram" to GRAMS, "grams" to GRAMS,
        "gramme" to GRAMS, "grammes" to GRAMS,
        "ml" to MILLILITRES, "mls" to MILLILITRES, "millilitre" to MILLILITRES,
        "millilitres" to MILLILITRES, "milliliter" to MILLILITRES, "milliliters" to MILLILITRES,
    )

    private val WORD_NUMBERS = mapOf(
        "a" to 1.0, "an" to 1.0, "one" to 1.0, "two" to 2.0, "three" to 3.0, "four" to 4.0,
        "five" to 5.0, "six" to 6.0, "seven" to 7.0, "eight" to 8.0, "nine" to 9.0,
        "ten" to 10.0, "half" to 0.5, "quarter" to 0.25, "couple" to 2.0,
    )

    /** Words that join two foods rather than describing one. */
    private val SEPARATORS = Regex("""\s*(?:,|;|\+|&|\n|\band\b|\bwith\b|\bplus\b)\s*""", RegexOption.IGNORE_CASE)

    /** "100g" is one token to a human and two to this parser. */
    private val DIGIT_LETTER_BOUNDARY = Regex("""(?<=\d)(?=[a-z])""")

    private val FRACTION = Regex("""^(\d+)/(\d+)$""")

    /** Filler that carries no meaning between a measure and the food it measures. */
    private val NOISE = setOf("of", "the", "some", "my", "i", "ate", "had", "small", "large")

    /**
     * @param units the measures the food tables actually know about, lowercased — passed in
     *   rather than hardcoded so the vocabulary here cannot drift from the seeded portion table.
     */
    fun parse(text: String, units: Set<String>): List<ParsedPortion> {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.none(Char::isLetter)) return emptyList()

        val parts = SEPARATORS.split(trimmed.lowercase())
            .map(String::trim)
            .filter { it.isNotEmpty() }

        // One unparseable fragment makes the whole sentence the model's problem: half a meal
        // logged locally and half sent away would be two rows written from different readings.
        return parts.map { parsePart(it, units) ?: return emptyList() }
    }

    private fun parsePart(part: String, units: Set<String>): ParsedPortion? {
        val tokens = part
            .replace(DIGIT_LETTER_BOUNDARY, " ")
            .split(Regex("""\s+"""))
            .map { it.trim(' ', '.', '!', '?', ':', '(', ')') }
            .filter { it.isNotEmpty() }
            .toMutableList()
        if (tokens.isEmpty()) return null

        var quantity: Double? = null
        var unit: String? = null

        val leadingNumber = number(tokens.first())
        if (leadingNumber != null) {
            quantity = leadingNumber
            tokens.removeAt(0)
            unitOf(tokens.firstOrNull(), units)?.let {
                unit = it
                tokens.removeAt(0)
            }
        } else {
            // The other way round: "paneer 100 g", or just "roti 2".
            val trailingUnit = unitOf(tokens.lastOrNull(), units)
            if (trailingUnit != null && tokens.size >= 2) {
                val before = number(tokens[tokens.size - 2])
                if (before != null) {
                    quantity = before
                    unit = trailingUnit
                    tokens.removeAt(tokens.size - 1)
                    tokens.removeAt(tokens.size - 1)
                }
            } else {
                val trailingNumber = number(tokens.last())
                if (trailingNumber != null && tokens.size >= 2) {
                    quantity = trailingNumber
                    tokens.removeAt(tokens.size - 1)
                }
            }
        }

        while (tokens.firstOrNull() in NOISE) tokens.removeAt(0)
        while (tokens.lastOrNull() in NOISE) tokens.removeAt(tokens.size - 1)

        // "2 roti" names its food with the same word as its measure.
        val name = tokens.joinToString(" ").ifBlank { unit ?: return null }
        if (name.none(Char::isLetter)) return null

        return ParsedPortion(quantity = quantity ?: 1.0, unit = unit, name = name)
    }

    private fun number(token: String?): Double? {
        val value = token ?: return null
        WORD_NUMBERS[value]?.let { return it }
        FRACTION.matchEntire(value)?.let { match ->
            val (top, bottom) = match.destructured
            val divisor = bottom.toDouble()
            return if (divisor == 0.0) null else top.toDouble() / divisor
        }
        return value.toDoubleOrNull()?.takeIf { it > 0 }
    }

    /** A measure the tables know, allowing for the plural nobody writes in a database. */
    private fun unitOf(token: String?, units: Set<String>): String? {
        val value = token ?: return null
        BASE_UNITS[value]?.let { return it }
        if (value in units) return value
        return value.removeSuffix("s").takeIf { it.isNotEmpty() && it in units }
    }
}
