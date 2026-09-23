package com.yash.tracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.yash.tracker.domain.nutrition.Micros

/**
 * One diary entry: a meal, however it was logged. Totals are denormalised here so the
 * dashboard never has to SUM across [LogItemEntity].
 *
 * [date] is resolved against the profile's day-start hour, so a 1 a.m. snack belongs to the
 * previous day.
 */
@Entity(tableName = "log_entry", indices = [Index("date")])
data class LogEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    @ColumnInfo(name = "logged_at") val loggedAt: Long,
    @ColumnInfo(name = "meal_type") val mealType: String,
    val source: String,
    @ColumnInfo(name = "photo_uri") val photoUri: String?,
    @ColumnInfo(name = "user_hint") val userHint: String?,
    val note: String?,
    @ColumnInfo(name = "grounding_source") val groundingSource: String?,
    val kcal: Double,
    @ColumnInfo(name = "protein_g") val proteinG: Double,
    @ColumnInfo(name = "carbs_g") val carbsG: Double,
    @ColumnInfo(name = "fat_g") val fatG: Double,
    /**
     * The rest of the panel, summed over whichever items stated it. Null when none of them
     * did — the entry then has no figure rather than a figure of zero.
     */
    @ColumnInfo(name = "fibre_g") val fibreG: Double? = null,
    @ColumnInfo(name = "sugar_g") val sugarG: Double? = null,
    @ColumnInfo(name = "sodium_mg") val sodiumMg: Double? = null,
    /**
     * How many of this entry's calories came from items that stated any of the above.
     *
     * Denormalised for the same reason the macros are: it is what says whether the three
     * numbers describe the meal or one corner of it, and the review and the day panel both
     * need it without reading every row back.
     */
    @ColumnInfo(name = "micro_kcal") val microKcal: Double = 0.0,
)

/**
 * Macros are frozen at save time. Editing a food's catalogue values later never rewrites
 * history, which is why [name] and every macro are copied rather than joined.
 */
@Entity(
    tableName = "log_item",
    foreignKeys = [
        ForeignKey(
            entity = LogEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entry_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("entry_id")],
)
data class LogItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "entry_id") val entryId: Long,
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
    @ColumnInfo(name = "ai_confidence") val aiConfidence: Double?,
    @ColumnInfo(name = "was_edited") val wasEdited: Boolean = false,
)

@Entity(tableName = "water_log", indices = [Index("date")])
data class WaterLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    @ColumnInfo(name = "logged_at") val loggedAt: Long,
    val ml: Int,
)

@Entity(tableName = "weight_log", indices = [Index(value = ["date"], unique = true)])
data class WeightLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    @ColumnInfo(name = "weight_kg") val weightKg: Double,
    val note: String?,
    @ColumnInfo(name = "logged_at") val loggedAt: Long,
)

/** What this row states beyond the four macros, as the domain reads it. */
val LogItemEntity.micros: Micros
    get() = Micros(fibreG = fibreG, sugarG = sugarG, sodiumMg = sodiumMg)

/**
 * Copies the rows' fibre, sugar and sodium onto the entry that holds them.
 *
 * Applied by the DAO on the way in rather than by each caller, because the entry's totals are
 * never anything other than the sum of its rows — five write paths each remembering to add
 * them up is five chances for the diary to disagree with itself.
 */
fun LogEntryEntity.withMicrosFrom(items: List<LogItemEntity>): LogEntryEntity {
    val total = items.fold(Micros.UNKNOWN) { sum, item -> sum + item.micros }
    return copy(
        fibreG = total.fibreG,
        sugarG = total.sugarG,
        sodiumMg = total.sodiumMg,
        microKcal = items.filter { it.micros.isKnown }.sumOf { it.kcal },
    )
}
