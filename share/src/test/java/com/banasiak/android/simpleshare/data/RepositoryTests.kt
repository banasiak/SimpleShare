package com.banasiak.android.simpleshare.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.banasiak.android.simpleshare.common.DurationClock
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeInstanceOf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds

class RepositoryTests {
  private lateinit var server: MockWebServer
  private val dataStore: DataStore<Preferences> = FakeDataStore()
  private val durationClock: DurationClock = mockk()

  private fun repository() = Repository(dataStore, durationClock, OkHttpClient())

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

  @Test
  fun `given a name kept on an earlier link, when a link without that name is saved, then the earlier choice survives`() =
    runTest {
      // given
      val repository = repository()
      repository.updateEnabledParamsForHost("www.youtube.com", present = setOf("v", "si"), enabled = setOf("v"))

      // when
      repository.updateEnabledParamsForHost("www.youtube.com", present = setOf("list", "si"), enabled = setOf("list"))

      // then
      repository.getEnabledParamsForHost("www.youtube.com").toSet() shouldBeEqualTo setOf("v", "list")
    }

  @Test
  fun `given a name kept on an earlier link, when a link with no query is saved, then nothing is forgotten`() =
    runTest {
      // given
      val repository = repository()
      repository.updateEnabledParamsForHost("www.youtube.com", present = setOf("v", "si"), enabled = setOf("v"))

      // when
      repository.updateEnabledParamsForHost("www.youtube.com", present = emptySet(), enabled = emptySet())

      // then
      repository.getEnabledParamsForHost("www.youtube.com") shouldBeEqualTo listOf("v")
    }

  @Test
  fun `given a name kept on an earlier link, when a link carrying it unchecked is saved, then only that name is forgotten`() =
    runTest {
      // given
      val repository = repository()
      repository.updateEnabledParamsForHost("www.youtube.com", present = setOf("v", "t"), enabled = setOf("v", "t"))

      // when
      repository.updateEnabledParamsForHost("www.youtube.com", present = setOf("v"), enabled = emptySet())

      // then
      repository.getEnabledParamsForHost("www.youtube.com") shouldBeEqualTo listOf("t")
    }
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