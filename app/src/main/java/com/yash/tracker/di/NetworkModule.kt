package com.yash.tracker.di

import com.yash.tracker.data.prefs.SettingsRepository
import com.yash.tracker.data.backup.BackupCodeStore
import com.yash.tracker.data.remote.CoachNotes
import com.yash.tracker.data.remote.GeminiClient
import com.yash.tracker.data.remote.GeminiConfig
import com.yash.tracker.data.remote.AppIdentity
import com.yash.tracker.data.remote.ExerciseDbService
import com.yash.tracker.data.remote.FirebaseAppIdentity
import com.yash.tracker.data.remote.GeminiService
import com.google.firebase.auth.FirebaseAuth
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /**
     * Retrofit insists on a base URL, but every call passes a whole one: a request goes to
     * Google or to our proxy depending on whose key is paying for it. This is only the
     * fallback the builder will not start without.
     */
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    /**
     * No logging interceptor at any build type: request bodies carry the API key header and
     * base64 image data, and TRD §9 forbids logging either.
     */
    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        // A label photo is a few hundred KB of base64 going up a mobile connection, and
        // OkHttp's default write timeout is 10 s — enough to fail the first send and succeed
        // on the retry, which is exactly what a flaky first scan looks like.
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build()

    /** Anonymous sign-in, which is the only thing the proxy will accept as proof of an install. */
    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(client)
        // Retrofit 3 ships this first-party; the old JakeWharton fork is gone.
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideGeminiService(retrofit: Retrofit): GeminiService =
        retrofit.create(GeminiService::class.java)

    @Provides
    @Singleton
    fun provideExerciseDbService(retrofit: Retrofit): ExerciseDbService =
        retrofit.create(ExerciseDbService::class.java)

    @Provides
    @Singleton
    fun provideGeminiConfig(settings: SettingsRepository): GeminiConfig = settings

    @Provides
    @Singleton
    fun provideBackupCodeStore(settings: SettingsRepository): BackupCodeStore = settings

    @Provides
    @Singleton
    fun provideAppIdentity(identity: FirebaseAppIdentity): AppIdentity = identity

    @Provides
    @Singleton
    fun provideCoachNotes(client: GeminiClient): CoachNotes = client
}
