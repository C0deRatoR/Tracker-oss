package com.yash.tracker.data.remote

import com.yash.tracker.data.remote.dto.ExerciseDbReply
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Url

interface ExerciseDbService {

    /** A whole URL, like [GeminiService], because the shared Retrofit's base is Google's. */
    @GET
    suspend fun exercise(@Url url: String): Response<ExerciseDbReply>
}
