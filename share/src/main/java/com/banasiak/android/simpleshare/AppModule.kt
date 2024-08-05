package com.banasiak.android.simpleshare

import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import com.banasiak.android.simpleshare.common.BuildInfo
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

  @Provides
  fun provideBuildInfo(@ApplicationContext context: Context): BuildInfo {
    return BuildInfo(Build.VERSION.SDK_INT, context.packageName, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
  }

  @Provides
  fun provideClipboardManager(@ApplicationContext context: Context): ClipboardManager {
    return context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
  }
}