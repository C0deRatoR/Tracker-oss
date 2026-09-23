package com.yash.tracker.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /** Injected rather than hardcoded so tests can run the seed import on a test dispatcher. */
    @Provides
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
