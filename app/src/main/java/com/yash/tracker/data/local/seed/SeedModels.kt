package com.yash.tracker.data.local.seed

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Shapes of the gzipped JSON in assets/seed/, produced by the scripts in tools/. */

@Serializable
data class SeedFoodFile(
    val source: String,
    val citation: String? = null,
    val licence: String? = null,
    val foods: List<SeedFood> = emptyList(),
)

@Serializable
data class SeedFood(
    val name: String,
    @SerialName("alt_names") val altNames: List<String> = emptyList(),
    val category: String? = null,
    val source: String,
    @SerialName("source_ref") val sourceRef: String? = null,
    @SerialName("is_composite") val isComposite: Boolean = false,
    @SerialName("per_100g") val per100g: SeedMacros,
    @SerialName("is_verified") val isVerified: Boolean = false,
    @SerialName("default_portion_g") val defaultPortionG: Double = 100.0,
    @SerialName("portion_label") val portionLabel: String? = null,
)

@Serializable
data class SeedMacros(
    val kcal: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fat: Double = 0.0,
    val fibre: Double? = null,
    val sugar: Double? = null,
    @SerialName("sodium_mg") val sodiumMg: Double? = null,
)

@Serializable
data class SeedPortionFile(val measures: List<SeedMeasure> = emptyList())

@Serializable
data class SeedMeasure(val label: String, val grams: Double, val src: String? = null)

@Serializable
data class SeedExerciseFile(
    /** Bumped by tools/build_exercises.py so an existing install knows to top itself up. */
    val version: Int = 1,
    val exercises: List<SeedExercise> = emptyList(),
)

@Serializable
data class SeedExercise(
    val name: String,
    @SerialName("muscle_group") val muscleGroup: String,
    val equipment: String,
    val type: String,
    @SerialName("met_value") val metValue: Double = 5.0,
    @SerialName("primary_muscles") val primaryMuscles: List<String> = emptyList(),
    @SerialName("secondary_muscles") val secondaryMuscles: List<String> = emptyList(),
    val instructions: List<String> = emptyList(),
    val force: String? = null,
    val mechanic: String? = null,
    val level: String? = null,
    val aliases: List<String> = emptyList(),
    @SerialName("default_rest_sec") val defaultRestSec: Int = 90,
    /** ExerciseDB id of the animation; absent for the rows with no honest match. */
    val art: String? = null,
)
