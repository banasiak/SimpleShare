package com.banasiak.android.simpleshare

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
  @Provides
  fun provideOkHttp(): OkHttpClient {
    return OkHttpClient()
  }
}