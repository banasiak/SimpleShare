package com.banasiak.android.simpleshare.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.banasiak.android.simpleshare.common.DurationClock
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.amshove.kluent.shouldBeEmpty
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeInstanceOf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class RepositoryTests {
  private lateinit var server: MockWebServer
  private val dataStore: DataStore<Preferences> = FakeDataStore()
  private val durationClock: DurationClock = mockk()

  private fun TestScope.repository() = Repository(dataStore, durationClock, OkHttpClient(), UnconfinedTestDispatcher(testScheduler))

  @BeforeEach
  fun beforeEach() {
    server = MockWebServer()
    server.start()
    // start and end of the call; the gap decides how much of minimumDuration is left to wait out
    every { durationClock.now() } returns 0.milliseconds
  }

  @AfterEach
  fun afterEach() {
    server.close()
  }

  @Test
  fun `given the host redirects, when fetching, then the final url is reported`() =
    runTest {
      // given
      server.enqueue(
        MockResponse.Builder()
          .code(301)
          .setHeader("Location", "/landing?utm_source=a")
          .build()
      )
      server.enqueue(MockResponse.Builder().code(200).body("done").build())

      // when
      val result = repository().fetchRedirectUrl(server.url("/short"))

      // then
      result.shouldBeInstanceOf<RedirectResult.Redirected>()
      (result as RedirectResult.Redirected).url.toString() shouldBeEqualTo server.url("/landing?utm_source=a").toString()
    }

  @Test
  fun `given the host answers without redirecting, when fetching, then no redirect is reported`() =
    runTest {
      server.enqueue(MockResponse.Builder().code(200).body("done").build())

      repository().fetchRedirectUrl(server.url("/page")) shouldBeEqualTo RedirectResult.NoRedirect
    }

  @Test
  fun `given the host cannot be reached, when fetching, then the failure is distinct from no redirect`() =
    runTest {
      // the two used to be indistinguishable, which is what stopped the UI offering a retry
      val url = server.url("/page")
      server.close()

      repository().fetchRedirectUrl(url) shouldBeEqualTo RedirectResult.Failed
    }

  @Test
  fun `given a response that is never read, when fetching, then the connection is released`() =
    runTest {
      // the body is deliberately larger than nothing so a leaked response would hold the connection;
      // a second call reusing the pool proves the first one was closed
      server.enqueue(MockResponse.Builder().code(200).body("x".repeat(8192)).build())
      server.enqueue(MockResponse.Builder().code(200).body("x".repeat(8192)).build())
      val repository = repository()

      repository.fetchRedirectUrl(server.url("/one"))
      repository.fetchRedirectUrl(server.url("/two"))

      server.takeRequest()
      server.takeRequest()
      server.requestCount shouldBeEqualTo 2
    }

  // region parameter preferences

  @Test
  fun `given boxes left the way the rules set them, when the link is saved, then nothing is remembered`() =
    runTest {
      // only a choice the user made is a preference. Remembering untouched boxes would pin them, and a
      // later catalog that starts or stops flagging a name could never reach this host again
      val repository = repository()

      repository.updateParamPreferencesForHost("www.youtube.com", enabled = setOf("v"), disabled = setOf("si"), flagged = setOf("si"))

      repository.getParamPreferencesForHost("www.youtube.com") shouldBeEqualTo ParamPreferences()
    }

  @Test
  fun `given a flagged parameter the user kept, when the link is saved, then it is remembered as kept`() =
    runTest {
      val repository = repository()

      repository.updateParamPreferencesForHost("x.com", enabled = setOf("t"), disabled = setOf("s"), flagged = setOf("s", "t"))

      repository.getParamPreferencesForHost("x.com") shouldBeEqualTo ParamPreferences(kept = setOf("t"))
    }

  @Test
  fun `given an unflagged parameter the user removed, when the link is saved, then it is remembered as removed`() =
    runTest {
      val repository = repository()

      repository.updateParamPreferencesForHost("www.banasiak.com", enabled = setOf("id"), disabled = setOf("session"), flagged = emptySet())

      repository.getParamPreferencesForHost("www.banasiak.com") shouldBeEqualTo ParamPreferences(removed = setOf("session"))
    }

  @Test
  fun `given a name kept on an earlier link, when a link without that name is saved, then the earlier choice survives`() =
    runTest {
      // given
      val repository = repository()
      repository.updateParamPreferencesForHost("x.com", enabled = setOf("t"), disabled = setOf("lang"), flagged = setOf("t"))

      // when
      repository.updateParamPreferencesForHost("x.com", enabled = setOf("s"), disabled = setOf("ref"), flagged = setOf("s"))

      // then
      repository.getParamPreferencesForHost("x.com") shouldBeEqualTo
        ParamPreferences(kept = setOf("t", "s"), removed = setOf("lang", "ref"))
    }

  @Test
  fun `given choices made on an earlier link, when a link with no query is saved, then nothing is forgotten`() =
    runTest {
      // given
      val repository = repository()
      repository.updateParamPreferencesForHost("x.com", enabled = setOf("t"), disabled = setOf("lang"), flagged = setOf("t"))

      // when
      repository.updateParamPreferencesForHost("x.com", enabled = emptySet(), disabled = emptySet(), flagged = emptySet())

      // then
      repository.getParamPreferencesForHost("x.com") shouldBeEqualTo ParamPreferences(kept = setOf("t"), removed = setOf("lang"))
    }

  @Test
  fun `given a name kept on an earlier link, when a link carrying it unchecked is saved, then only that name is forgotten`() =
    runTest {
      // given
      val repository = repository()
      repository.updateParamPreferencesForHost("x.com", enabled = setOf("s", "t"), disabled = emptySet(), flagged = setOf("s", "t"))

      // when
      repository.updateParamPreferencesForHost("x.com", enabled = emptySet(), disabled = setOf("s"), flagged = setOf("s"))

      // then
      repository.getParamPreferencesForHost("x.com") shouldBeEqualTo ParamPreferences(kept = setOf("t"))
    }

  @Test
  fun `given a name removed on an earlier link, when a link carrying it checked is saved, then only that name is forgotten`() =
    runTest {
      // given
      val repository = repository()
      repository.updateParamPreferencesForHost("x.com", enabled = emptySet(), disabled = setOf("lang", "ref"), flagged = emptySet())

      // when
      repository.updateParamPreferencesForHost("x.com", enabled = setOf("lang"), disabled = emptySet(), flagged = emptySet())

      // then
      repository.getParamPreferencesForHost("x.com") shouldBeEqualTo ParamPreferences(removed = setOf("ref"))
    }

  @Test
  fun `given a name the user kept, when the rules stop flagging it and the box is left checked, then the choice survives`() =
    runTest {
      // the box still agrees with what the user chose, so nothing here says they changed their mind;
      // if the catalog flags the name again, their choice should still be waiting
      val repository = repository()
      repository.updateParamPreferencesForHost("x.com", enabled = setOf("t"), disabled = emptySet(), flagged = setOf("t"))

      repository.updateParamPreferencesForHost("x.com", enabled = setOf("t"), disabled = emptySet(), flagged = emptySet())

      repository.getParamPreferencesForHost("x.com") shouldBeEqualTo ParamPreferences(kept = setOf("t"))
    }

  @Test
  fun `given a name the user removed, when the rules start flagging it and the box is left unchecked, then the choice survives`() =
    runTest {
      val repository = repository()
      repository.updateParamPreferencesForHost("x.com", enabled = emptySet(), disabled = setOf("ref"), flagged = emptySet())

      repository.updateParamPreferencesForHost("x.com", enabled = emptySet(), disabled = setOf("ref"), flagged = setOf("ref"))

      repository.getParamPreferencesForHost("x.com") shouldBeEqualTo ParamPreferences(removed = setOf("ref"))
    }

  @Test
  fun `given names kept by a release that stored only kept names, when they are read, then they are still kept`() =
    runTest {
      // every release before the rules wrote one set per host, keyed on the bare host, holding the
      // names whose boxes were checked. Those are real choices and have to keep reading as such.
      dataStore.edit { it[stringSetPreferencesKey("www.youtube.com")] = setOf("v", "t") }

      repository().getParamPreferencesForHost("www.youtube.com") shouldBeEqualTo ParamPreferences(kept = setOf("v", "t"))
    }

  @Test
  fun `given names kept by an older release, when a link is saved, then the ones it does not contradict stay`() =
    runTest {
      // given
      dataStore.edit { it[stringSetPreferencesKey("www.youtube.com")] = setOf("v", "t", "si") }
      val repository = repository()

      // when: v is left checked, t is unchecked, and si is kept although the rules flag it
      repository.updateParamPreferencesForHost("www.youtube.com", enabled = setOf("v", "si"), disabled = setOf("t"), flagged = setOf("si"))

      // then
      repository.getParamPreferencesForHost("www.youtube.com") shouldBeEqualTo
        ParamPreferences(kept = setOf("v", "si"), removed = setOf("t"))
    }

  @Test
  fun `given a host with nothing left to remember, when the link is saved, then the host leaves the store`() =
    runTest {
      // the store is backed up, so it should hold the user's choices and not a history of every host
      // they ever shared a link from
      val repository = repository()
      repository.updateParamPreferencesForHost("x.com", enabled = setOf("t"), disabled = setOf("lang"), flagged = setOf("t"))

      repository.updateParamPreferencesForHost("x.com", enabled = setOf("lang"), disabled = setOf("t"), flagged = setOf("t"))

      dataStore.data.first().asMap().shouldBeEmpty()
    }

  @Test
  fun `given a host that was only looked at, when the link is saved, then the host never enters the store`() =
    runTest {
      repository().updateParamPreferencesForHost("www.youtube.com", enabled = setOf("v"), disabled = setOf("si"), flagged = setOf("si"))

      dataStore.data.first().asMap().shouldBeEmpty()
    }

  @Test
  fun `given two hosts, when choices are saved for one, then the other is untouched`() =
    runTest {
      val repository = repository()
      repository.updateParamPreferencesForHost("x.com", enabled = setOf("t"), disabled = setOf("lang"), flagged = setOf("t"))

      repository.updateParamPreferencesForHost(
        "www.banasiak.com",
        enabled = emptySet(),
        disabled = setOf("t", "lang"),
        flagged = emptySet()
      )

      repository.getParamPreferencesForHost("x.com") shouldBeEqualTo ParamPreferences(kept = setOf("t"), removed = setOf("lang"))
      repository.getParamPreferencesForHost("www.banasiak.com") shouldBeEqualTo ParamPreferences(removed = setOf("t", "lang"))
    }

  // endregion
}

// edit{} still runs the real Preferences transform against this, so the merge is exercised exactly as
// on a device; only the file behind the store is left out
private class FakeDataStore : DataStore<Preferences> {
  private val state = MutableStateFlow(emptyPreferences())
  override val data: Flow<Preferences> = state

  override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
    state.value = transform(state.value)
    return state.value
  }
}