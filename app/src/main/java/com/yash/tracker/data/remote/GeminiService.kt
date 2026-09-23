package com.yash.tracker.data.remote

import com.yash.tracker.data.remote.dto.GenerateContentRequest
import com.yash.tracker.data.remote.dto.GenerateContentResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.HeaderMap
import retrofit2.http.POST
import retrofit2.http.Url

interface GeminiService {

    /**
     * A whole URL rather than a path against one base, because the same request goes to one of
     * two hosts: Google directly when the user brought their own key, our proxy when they are
     * borrowing the shared one. [GeminiAuth] decides which, and hands back the header that
     * goes with it — the credential never rides in the query string, which leaks into logs.
     */
    @POST
    suspend fun generateContent(
        @Url url: String,
        @HeaderMap headers: Map<String, String>,
        @Body request: GenerateContentRequest,
    ): Response<GenerateContentResponse>
}
