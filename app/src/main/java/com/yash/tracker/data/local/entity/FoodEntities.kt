package com.yash.tracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import com.yash.tracker.domain.nutrition.NutritionPer100g
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.yash.tracker.domain.nutrition.IngredientFlag

/**
 * The bundled catalogue plus anything learned at runtime. [source] records provenance so a
 * wrong number can be traced: INDB and USDA rows ship with the app, AI_ESTIMATE rows are
 * written when Gemini names a dish we do not know, USER rows are hand-entered.
 */
@Entity(tableName = "food", indices = [Index("name")])
data class FoodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /**
     * Transliterations, regional names and whatever this user calls it, joined by spaces —
     * the shape the FTS index over this column wants. Anything matching a single name out of
     * it has to anchor on spaces.
     */
    @ColumnInfo(name = "alt_names") val altNames: String?,
    val category: String?,
    val source: String,
    @ColumnInfo(name = "source_ref") val sourceRef: String?,
    @ColumnInfo(name = "is_composite") val isComposite: Boolean = false,
    @ColumnInfo(name = "kcal_100g") override val kcal100g: Double,
    @ColumnInfo(name = "protein_100g") override val protein100g: Double,
    @ColumnInfo(name = "carbs_100g") override val carbs100g: Double,
    @ColumnInfo(name = "fat_100g") override val fat100g: Double,
    @ColumnInfo(name = "fibre_100g") override val fibre100g: Double?,
    @ColumnInfo(name = "sugar_100g") override val sugar100g: Double?,
    @ColumnInfo(name = "sodium_mg_100g") override val sodiumMg100g: Double?,
    @ColumnInfo(name = "default_portion_g") val defaultPortionG: Double,
    @ColumnInfo(name = "portion_label") val portionLabel: String?,
    @ColumnInfo(name = "is_verified") val isVerified: Boolean = false,
    @ColumnInfo(name = "times_logged") val timesLogged: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long,
) : NutritionPer100g {
    companion object {
        /** Written when a confirmed meal named a food the tables did not already hold. */
        const val SOURCE_AI_ESTIMATE = "AI_ESTIMATE"
    }
}

@Entity(tableName = "food_fts")
@Fts4(contentEntity = FoodEntity::class)
data class FoodFts(
    val name: String,
    @ColumnInfo(name = "alt_names") val altNames: String?,
)

/** Household measures resolved to grams. A null [foodId] makes the measure generic. */
@Entity(
    tableName = "portion_measure",
    foreignKeys = [
        ForeignKey(
            entity = FoodEntity::class,
            parentColumns = ["id"],
            childColumns = ["food_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("food_id")],
)
data class PortionMeasureEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "food_id") val foodId: Long?,
    val label: String,
    val grams: Double,
)

/**
 * Packaged food the user owns. Products are authoritative: an entry referencing one never
 * gets its macros recalculated from an AI estimate.
 */
@Entity(tableName = "product")
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val brand: String?,
    val name: String,
    @ColumnInfo(name = "kcal_100g") override val kcal100g: Double,
    @ColumnInfo(name = "protein_100g") override val protein100g: Double,
    @ColumnInfo(name = "carbs_100g") override val carbs100g: Double,
    @ColumnInfo(name = "fat_100g") override val fat100g: Double,
    @ColumnInfo(name = "fibre_100g") override val fibre100g: Double?,
    @ColumnInfo(name = "sugar_100g") override val sugar100g: Double?,
    @ColumnInfo(name = "sodium_mg_100g") override val sodiumMg100g: Double?,
    @ColumnInfo(name = "serving_g") val servingG: Double?,
    @ColumnInfo(name = "serving_label") val servingLabel: String?,
    val ingredients: String?,
    /** [IngredientFlag]s as JSON, written whenever the label is read. */
    @ColumnInfo(name = "ingredient_flags") val ingredientFlags: String? = null,
    @ColumnInfo(name = "ingredient_verdict") val ingredientVerdict: String? = null,
    @ColumnInfo(name = "label_photo_uri") val labelPhotoUri: String?,
    val barcode: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
) : NutritionPer100g
