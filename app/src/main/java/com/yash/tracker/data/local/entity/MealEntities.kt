package com.yash.tracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.yash.tracker.domain.nutrition.Micros

/** A meal eaten repeatedly, saved with exact items and portions so it logs in two taps. */
@Entity(tableName = "meal_template")
data class MealTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "photo_uri") val photoUri: String?,
    @ColumnInfo(name = "default_meal_type") val defaultMealType: String?,
    val kcal: Double,
    @ColumnInfo(name = "protein_g") val proteinG: Double,
    @ColumnInfo(name = "carbs_g") val carbsG: Double,
    @ColumnInfo(name = "fat_g") val fatG: Double,
    @ColumnInfo(name = "fibre_g") val fibreG: Double? = null,
    @ColumnInfo(name = "sugar_g") val sugarG: Double? = null,
    @ColumnInfo(name = "sodium_mg") val sodiumMg: Double? = null,
    @ColumnInfo(name = "micro_kcal") val microKcal: Double = 0.0,
    @ColumnInfo(name = "times_logged") val timesLogged: Int = 0,
    @ColumnInfo(name = "last_logged_at") val lastLoggedAt: Long?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(
    tableName = "meal_template_item",
    foreignKeys = [
        ForeignKey(
            entity = MealTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["template_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("template_id")],
)
data class MealTemplateItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "template_id") val templateId: Long,
    @ColumnInfo(name = "food_id") val foodId: Long?,
    @ColumnInfo(name = "product_id") val productId: Long?,
    val name: String,
    val quantity: Double,
    val unit: String,
    val grams: Double,
    val kcal: Double,
    @ColumnInfo(name = "protein_g") val proteinG: Double,
    @ColumnInfo(name = "carbs_g") val carbsG: Double,
    @ColumnInfo(name = "fat_g") val fatG: Double,
    @ColumnInfo(name = "fibre_g") val fibreG: Double? = null,
    @ColumnInfo(name = "sugar_g") val sugarG: Double? = null,
    @ColumnInfo(name = "sodium_mg") val sodiumMg: Double? = null,
)

val MealTemplateItemEntity.micros: Micros
    get() = Micros(fibreG = fibreG, sugarG = sugarG, sodiumMg = sodiumMg)

/**
 * The saved-meal twin of [com.yash.tracker.data.local.entity.withMicrosFrom] for diary
 * entries. A template is a meal that will be logged again, so it has to carry the same
 * figures the diary does — otherwise the two-tap log quietly drops them.
 */
fun MealTemplateEntity.withMicrosFrom(items: List<MealTemplateItemEntity>): MealTemplateEntity {
    val total = items.fold(Micros.UNKNOWN) { sum, item -> sum + item.micros }
    return copy(
        fibreG = total.fibreG,
        sugarG = total.sugarG,
        sodiumMg = total.sodiumMg,
        microKcal = items.filter { it.micros.isKnown }.sumOf { it.kcal },
    )
}
