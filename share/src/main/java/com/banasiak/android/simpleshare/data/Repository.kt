package com.banasiak.android.simpleshare.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.banasiak.android.simpleshare.common.DurationClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.coroutines.executeAsync
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

sealed interface RedirectResult {
  data class Redirected(val url: HttpUrl) : RedirectResult
  data object NoRedirect : RedirectResult
  data object Failed : RedirectResult
}

@Singleton
class Repository @Inject constructor(
  private val dataStore: DataStore<Preferences>,
  private val durationClock: DurationClock,
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
    var count = 1
    // read and write inside a single edit{} so concurrent callers can't observe the same count twice
    dataStore.edit { prefs ->
      count = prefs[key] ?: 1
      prefs[key] = count + 1
    }
    return count
  }

  suspend fun fetchRedirectUrl(url: HttpUrl, minimumDuration: Duration = 0.milliseconds): RedirectResult {
    val start = durationClock.now()

    val request = Request.Builder().url(url).build()
    // executeAsync() resumes on the caller's dispatcher, which is the main thread for viewModelScope.
    // Closing an unread HTTP/2 response body writes a RST_STREAM frame, so the close is real network
    // I/O and throws NetworkOnMainThreadException unless the whole call is confined to Dispatchers.IO.
    val result =
      withContext(Dispatchers.IO) {
        try {
          // the response body is never read, but it still has to be closed to release the connection
          httpClient.newCall(request).executeAsync().use { response ->
            val newUrl = response.request.url
            if (newUrl != url) RedirectResult.Redirected(newUrl) else RedirectResult.NoRedirect
          }
        } catch (e: IOException) {
          // a network failure says nothing about whether the URL redirects, so it stays retryable
          Timber.w(e, "Unable to resolve redirects for: $url")
          RedirectResult.Failed
        }
      }

    val duration = durationClock.now() - start
    if (duration < minimumDuration) {
      val delay = minimumDuration - duration
      Timber.d("Delaying fetchRedirectUrl() for: $delay")
      delay(delay)
    }

    return result
  }
}