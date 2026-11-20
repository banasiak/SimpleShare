package com.banasiak.android.simpleshare

import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.banasiak.android.simpleshare.common.BuildInfo
import com.banasiak.android.simpleshare.common.Constants
import com.banasiak.android.simpleshare.common.DurationClock
import com.banasiak.android.simpleshare.data.EmptySetMigration
import com.banasiak.android.simpleshare.data.RuleSource
import dagger.Module
import dagger.Provides
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlin.time.DurationUnit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
  private const val DATASTORE_NAME = "datastore"
  private const val RULES_ASSET = "clearurls/data.min.json"

  @Provides
  @Reusable
  fun provideBuildInfo(@ApplicationContext context: Context): BuildInfo =
    BuildInfo(
      Build.VERSION.SDK_INT,
      context.packageName,
      BuildConfig.VERSION_NAME,
      BuildConfig.VERSION_CODE
    )

  @Provides
  @Singleton
  fun provideClipboardManager(@ApplicationContext context: Context): ClipboardManager =
    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

  @Provides
  @Singleton
  fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(
      corruptionHandler = ReplaceFileCorruptionHandler(produceNewData = { emptyPreferences() }),
      migrations = listOf(SharedPreferencesMigration(context, DATASTORE_NAME), EmptySetMigration),
      scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
      produceFile = { context.preferencesDataStoreFile(DATASTORE_NAME) }
    )

  @Provides
  @Singleton
  fun provideOkHttpClient(): OkHttpClient =
    OkHttpClient.Builder()
      .callTimeout(10, TimeUnit.SECONDS)
      .followRedirects(true)
      .addNetworkInterceptor { chain ->
        chain.proceed(
          chain.request()
            .newBuilder()
            .header("User-Agent", Constants.USER_AGENT)
            .build()
        )
      }
      .build()

  @Provides
  @Singleton
  fun provideDurationClock(): DurationClock = DurationClock(DurationUnit.MILLISECONDS)

  @Provides
  @DefaultDispatcher
  fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

  @Provides
  @IoDispatcher
  fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

  @Provides
  @Singleton
  fun provideRuleSource(@ApplicationContext context: Context): RuleSource = RuleSource { context.assets.open(RULES_ASSET) }
}