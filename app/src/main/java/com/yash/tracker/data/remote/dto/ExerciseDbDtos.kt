package com.yash.tracker.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * The slice of ExerciseDB's free-tier reply this app reads: `GET /api/v1/exercises/{id}`.
 *
 * Everything else it returns — muscles, steps — is left unread. The catalogue already has its
 * own, and the free tier forbids keeping any of it.
 */
@Serializable
data class ExerciseDbReply(
    val success: Boolean = false,
    val data: ExerciseDbExercise? = null,
)

@Serializable
data class ExerciseDbExercise(
    val exerciseId: String? = null,
    val gifUrl: String? = null,
)
