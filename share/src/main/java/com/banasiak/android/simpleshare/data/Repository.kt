package com.banasiak.android.simpleshare.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.banasiak.android.simpleshare.common.DurationClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
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

data class ParamPreferences(
  val kept: Set<String> = emptySet(),
  val removed: Set<String> = emptySet()
)

@Singleton
class Repository @Inject constructor(
  private val dataStore: DataStore<Preferences>,
  private val durationClock: DurationClock,
  private val httpClient: OkHttpClient
) {
  /**
   * Records what the user decided on one link. [enabled] and [disabled] split the names the link
   * carried by how its boxes were left, and [flagged] holds the names the rules had marked.
   *
   * Only a box left against the rules is remembered: a flagged name that was kept, or an unflagged
   * one that was removed. Leaving a box as the rules had it stores nothing, but it does not erase an
   * earlier choice the box still agrees with.
   */
  suspend fun updateParamPreferencesForHost(host: String, enabled: Set<String>, disabled: Set<String>, flagged: Set<String>) {
    Timber.d("Update param preferences for '$host': enabled=$enabled, disabled=$disabled, flagged=$flagged")
    // a link only speaks for the names it carries, so merge rather than replace: overwriting the sets
    // would let a link without some parameter (or with no query at all) erase an earlier choice.
    // read and write inside a single edit{} so a concurrent update for the same host isn't lost
    dataStore.edit { prefs ->
      prefs.setOrRemove(keptKey(host), prefs[keptKey(host)].orEmpty() - disabled + (enabled intersect flagged))
      prefs.setOrRemove(removedKey(host), prefs[removedKey(host)].orEmpty() - enabled + (disabled - flagged))
    }
  }

  suspend fun getParamPreferencesForHost(host: String): ParamPreferences {
    val prefs = dataStore.data.first()
    val preferences = ParamPreferences(kept = prefs[keptKey(host)].orEmpty(), removed = prefs[removedKey(host)].orEmpty())
    Timber.d("Retrieve param preferences for '$host': $preferences")
    return preferences
  }

  // the host alone, as every release has written it, so choices saved by an older install still read
  private fun keptKey(host: String) = stringSetPreferencesKey(host)

  // a host cannot contain '#', so this never collides with another host's kept names
  private fun removedKey(host: String) = stringSetPreferencesKey("$host#removed")

  // a host with nothing to remember leaves no entry, so the store holds choices, not a list of every
  // host that was ever shared
  private fun MutablePreferences.setOrRemove(key: Preferences.Key<Set<String>>, value: Set<String>) {
    if (value.isEmpty()) remove(key) else this[key] = value
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