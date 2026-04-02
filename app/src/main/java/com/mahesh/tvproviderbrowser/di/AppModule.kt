package com.mahesh.tvproviderbrowser.di

import android.content.ContentResolver
import android.content.Context
import com.mahesh.tvproviderbrowser.data.repository.TvProviderRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideContentResolver(
        @ApplicationContext context: Context,
    ): ContentResolver = context.contentResolver

    @Provides
    @Singleton
    fun provideTvProviderRepository(
        contentResolver: ContentResolver,
    ): TvProviderRepository = TvProviderRepository(contentResolver)
}

