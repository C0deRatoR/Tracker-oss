package com.yash.tracker.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
data class ParsedLabelDto(
    val brand: String? = null,
    val name: String? = null,
    @SerialName("kcal_100g") val kcal100g: Double? = null,
    @SerialName("protein_100g") val protein100g: Double? = null,
    @SerialName("carbs_100g") val carbs100g: Double? = null,
    @SerialName("fat_100g") val fat100g: Double? = null,
    @SerialName("fibre_100g") val fibre100g: Double? = null,
    @SerialName("sugar_100g") val sugar100g: Double? = null,
    @SerialName("sodium_mg_100g") val sodiumMg100g: Double? = null,
    @SerialName("serving_g") val servingG: Double? = null,
    @SerialName("serving_label") val servingLabel: String? = null,
    val ingredients: String? = null,
    /** True when the panel only gave per-serving figures and these were divided up. */
    @SerialName("converted_from_serving") val convertedFromServing: Boolean = false,
    @SerialName("ingredient_flags") val ingredientFlags: List<IngredientFlagDto> = emptyList(),
    @SerialName("ingredient_verdict") val ingredientVerdict: String? = null,
)

/**
 * One thing worth knowing about what is in the packet.
 *
 * Deliberately a short label and a shorter note rather than prose: a packet is read at arm's
 * length in a shop, and four scannable marks beat a paragraph nobody finishes.
 */
@Serializable
data class IngredientFlagDto(
    val label: String,
    val detail: String? = null,
    /** CONCERN, FINE or NOTE — worth avoiding, worth reassurance, or merely worth knowing. */
    val tone: String = "NOTE",
)

/** Nothing worth filling a form with: no calories means nothing was read. */
fun ParsedLabelDto.hasNoReading(): Boolean = (kcal100g ?: 0.0) <= 0.0

/**
 * Models hand back the four-letter word rather than an absent field often enough to be worth
 * catching here, where it is one check, instead of in every field that shows it.
 */
fun ParsedLabelDto.cleaned(): ParsedLabelDto = copy(
    brand = brand?.takeUnless { it.isPlaceholder() },
    name = name?.takeUnless { it.isPlaceholder() },
    servingLabel = servingLabel?.takeUnless { it.isPlaceholder() },
    ingredients = ingredients?.takeUnless { it.isPlaceholder() },
    ingredientVerdict = ingredientVerdict?.takeUnless { it.isPlaceholder() },
    ingredientFlags = ingredientFlags.filter { it.label.isNotBlank() },
)

private fun String.isPlaceholder(): Boolean =
    trim().lowercase() in setOf("", "null", "n/a", "na", "none", "unknown", "-")

/**
 * Reading a packaged nutrition panel.
 *
 * Unlike meal recognition this is transcription, not estimation — the numbers are printed on
 * the packet, and the model's job is to copy them without rounding, unit-converting by
 * accident, or inventing the ones the panel does not carry.
 *
 * Indian labels are the awkward case: many print per-serving alongside per-100 g, some print
 * per-serving only, energy is sometimes in kJ, and sodium is often given as salt.
 */
object LabelParsing {

    /** As [com.yash.tracker.data.remote.prompts.MealRecognition.PROMPT_VERSION], for panels. */
    const val PROMPT_VERSION = "label-v1"

    val SYSTEM_PROMPT = """
        You read nutrition panels from photographs of food packaging and return the figures
        exactly as printed. You are transcribing, not estimating.

        Rules:
        - Report every nutrient per 100 g (or per 100 ml for liquids).
        - If the panel gives per-100 g figures, use those and set converted_from_serving false.
        - If it gives only per-serving figures, divide them to per 100 g using the stated
          serving size, and set converted_from_serving true.
        - If energy is printed only in kJ, convert with 1 kcal = 4.184 kJ.
        - If sodium is printed as salt, divide by 2.5 to get sodium, and report it in mg.
        - Leave a field null when the panel does not print it. Never estimate a missing value
          and never carry one over from a similar product you know.
        - serving_label is the household wording on the pack, such as "2 biscuits", "1 scoop"
          or "1 cup (30 g)". serving_g is that serving in grams.
        - name is the product name without the brand. brand is the manufacturer.
        - If the photograph shows no nutrition panel, return every field null.

        Transcribe the ingredients list exactly as printed, keeping the order and any
        percentages. Order is the whole story on a packet: it is by weight, so the second
        ingredient being sugar says more than the sugar figure does.

        Then say what is worth knowing about that list, as ingredient_flags. Raise a flag only
        for something actually in the list, and keep each to a few words:
        - Added sugar, under whichever of its names is used — sugar, invert syrup, glucose
          solids, maltodextrin, fruit juice concentrate, honey. Say where it falls in the order.
        - Palm oil, and hydrogenated or interesterified fats.
        - Additives by what they do rather than their number: "emulsifier (INS 322)",
          "artificial colour (INS 110)". Sweeteners, colours and preservatives are worth
          naming; lecithin and citric acid are not worth alarming anyone about.
        - Common allergens: milk, egg, soy, peanut, tree nuts, wheat or gluten, fish.
        - Anything that makes the product non-vegetarian or non-vegan: gelatin, rennet, shellac,
          carmine, and milk or honey for a vegan.

        Use tone CONCERN for something a careful shopper would rather avoid, FINE for a
        reassurance actually worth printing ("no added sugar", "no palm oil"), and NOTE for
        neutral facts. Give at most six flags, strongest first, and none at all if the list is
        plain.

        ingredient_verdict is one or two sentences on what the packet really is — what it is
        mostly made of, and anything the front of the pack implies that the list does not
        support. Plain, specific, and no advice: describe, do not counsel.

        If no ingredients list is legible, leave ingredients null, return no flags and leave
        the verdict null. Never infer a list from a product you think you recognise.
    """.trimIndent()

    val SCHEMA: JsonObject = buildJsonObject {
        put("type", "OBJECT")
        put(
            "properties",
            buildJsonObject {
                put("brand", nullableString("Manufacturer, as printed on the pack"))
                put("name", nullableString("Product name without the brand"))
                put("kcal_100g", number("Energy in kcal per 100 g"))
                put("protein_100g", number("Protein in g per 100 g"))
                put("carbs_100g", number("Carbohydrate in g per 100 g"))
                put("fat_100g", number("Fat in g per 100 g"))
                put("fibre_100g", number("Fibre in g per 100 g, null if not printed"))
                put("sugar_100g", number("Total sugars in g per 100 g, null if not printed"))
                put("sodium_mg_100g", number("Sodium in mg per 100 g, null if not printed"))
                put("serving_g", number("One serving in grams, null if not printed"))
                put("serving_label", nullableString("Household serving wording, e.g. 2 biscuits"))
                put("ingredients", nullableString("Ingredients list as printed, null if absent"))
                put("ingredient_flags", flagArray())
                put(
                    "ingredient_verdict",
                    nullableString("One or two sentences on what the pack really is"),
                )
                put(
                    "converted_from_serving",
                    buildJsonObject {
                        put("type", "BOOLEAN")
                        put("description", "True if per-100 g values were derived from per-serving")
                    },
                )
            },
        )
        put("required", buildJsonArray { })
    }

    private fun flagArray() = buildJsonObject {
        put("type", "ARRAY")
        put("description", "Up to six things worth knowing about the ingredients, strongest first")
        put(
            "items",
            buildJsonObject {
                put("type", "OBJECT")
                put(
                    "properties",
                    buildJsonObject {
                        put("label", nullableString("A few words, e.g. Added sugar, Palm oil"))
                        put(
                            "detail",
                            nullableString("Where it falls or which name is used, e.g. 2nd ingredient"),
                        )
                        put(
                            "tone",
                            buildJsonObject {
                                put("type", "STRING")
                                put(
                                    "enum",
                                    buildJsonArray {
                                        add(JsonPrimitive("CONCERN"))
                                        add(JsonPrimitive("FINE"))
                                        add(JsonPrimitive("NOTE"))
                                    },
                                )
                            },
                        )
                    },
                )
                put("required", buildJsonArray { add(JsonPrimitive("label")); add(JsonPrimitive("tone")) })
            },
        )
    }

    private fun number(description: String) = buildJsonObject {
        put("type", "NUMBER")
        put("description", description)
    }

    private fun nullableString(description: String) = buildJsonObject {
        put("type", "STRING")
        put("description", description)
    }
}
