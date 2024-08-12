package com.banasiak.android.simpleshare.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.coroutines.executeAsync
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Repository @Inject constructor(
  private val dataStore: DataStore<Preferences>,
  private val httpClient: OkHttpClient
) {
  suspend fun setEnabledParamsForHost(host: String, params: List<String>) {
    Timber.d("Persist enabled params for '$host': $params")
    val key = stringSetPreferencesKey(host)
    dataStore.edit { prefs ->
      prefs[key] = params.toSet()
    }
  }

  suspend fun getEnabledParamsForHost(host: String): List<String> {
    val key = stringSetPreferencesKey(host)
    val params = dataStore.data.map { it[key] }.map { it?.toList() ?: emptyList() }.first()
    Timber.d("Retrieve enabled params for '$host': $params")
    return params
  }

  suspend fun getLaunchCountThenIncrement(): Int {
    val key = intPreferencesKey("launchCount")
    val count = dataStore.data.map { it[key] }.firstOrNull() ?: 1
    dataStore.edit { it[key] = count + 1 }
    return count
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  suspend fun fetchRedirectUrl(url: HttpUrl): HttpUrl? {
    val request = Request.Builder().url(url).build()
    val response = httpClient.newCall(request).executeAsync()
    val newUrl = response.request.url
    return if (newUrl != url) newUrl else null
  }
}