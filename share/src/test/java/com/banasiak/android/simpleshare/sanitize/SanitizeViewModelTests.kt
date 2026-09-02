package com.banasiak.android.simpleshare.sanitize

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.os.PersistableBundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.banasiak.android.simpleshare.MainDispatcherRule
import com.banasiak.android.simpleshare.R
import com.banasiak.android.simpleshare.common.BuildInfo
import com.banasiak.android.simpleshare.data.RedirectResult
import com.banasiak.android.simpleshare.data.Repository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkConstructor
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeFalse
import org.amshove.kluent.shouldBeTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherRule::class)
class SanitizeViewModelTests {
  private val clipboardManager: ClipboardManager = mockk(relaxed = true)
  private val repository: Repository = mockk(relaxed = true)
  private val savedState: SavedStateHandle = mockk(relaxed = true)
  private val lifecycleOwner: LifecycleOwner = mockk(relaxed = true)

  private fun viewModel(apiLevel: Int = 33) =
    SanitizeViewModel(
      buildInfo = BuildInfo(apiLevel, "com.banasiak.android.simpleshare", "TEST", 1),
      clipboardManager = clipboardManager,
      repository = repository,
      savedState = savedState
    )

  @BeforeEach
  fun beforeEach() {
    every { savedState.get<SanitizeState>("state") } returns null
    coEvery { repository.getEnabledParamsForHost(any()) } returns emptyList()
    coEvery { repository.getLaunchCountThenIncrement() } returns 1
    coEvery { repository.fetchRedirectUrl(any(), any()) } returns RedirectResult.NoRedirect

    // ClipData.newPlainText and the PersistableBundle constructor are android.jar stubs that throw
    // when called on the JVM; stubbing them keeps the copy path testable without Robolectric
    mockkStatic(ClipData::class)
    mockkConstructor(PersistableBundle::class)
    val description: ClipDescription = mockk(relaxed = true)
    val clip: ClipData = mockk(relaxed = true)
    every { clip.description } returns description
    every { ClipData.newPlainText(any(), any()) } returns clip
  }

  @AfterEach
  fun afterEach() {
    unmockkStatic(ClipData::class)
    unmockkConstructor(PersistableBundle::class)
  }

  // region intent handling

  @Test
  fun `given text around a tracked url, when the intent arrives, then the parameters are listed and stripped`() =
    runTest {
      // given
      val vm = viewModel()

      // when
      vm.postAction(SanitizeAction.IntentReceived("Look at this https://www.banasiak.com/p?utm_source=a&id=1"))

      // then
      val state = vm.stateFlow.value
      state.parameters.keys.map { it.name } shouldBeEqualTo listOf("utm_source", "id")
      state.parameters.values.all { it }.shouldBeFalse()
      state.sanitizedUrl shouldBeEqualTo "https://www.banasiak.com/p"
    }

  @Test
  fun `given a non-default port and a fragment, when the intent arrives, then both survive sanitizing`() =
    runTest {
      // a port is part of the address and a fragment points at a place in the page; dropping either
      // silently rewrites the link into one that no longer goes where the sender meant
      val vm = viewModel()

      vm.postAction(SanitizeAction.IntentReceived("https://www.banasiak.com:8443/p?utm_source=a#section"))

      vm.stateFlow.value.sanitizedUrl shouldBeEqualTo "https://www.banasiak.com:8443/p#section"
    }

  @Test
  fun `given a url that repeats a parameter name, when the intent arrives, then every occurrence is listed`() =
    runTest {
      // reading the query by name returns only the first match, which used to drop tag=y outright
      val vm = viewModel()

      vm.postAction(SanitizeAction.IntentReceived("https://www.banasiak.com/p?tag=x&tag=y"))

      val parameters = vm.stateFlow.value.parameters.keys.toList()
      parameters.map { it.name } shouldBeEqualTo listOf("tag", "tag")
      parameters.map { it.value } shouldBeEqualTo listOf("x", "y")
    }

  @Test
  fun `given a parameter kept for this host before, when the intent arrives, then it is pre-checked and preserved`() =
    runTest {
      // given
      coEvery { repository.getEnabledParamsForHost("www.banasiak.com") } returns listOf("id")
      val vm = viewModel()

      // when
      vm.postAction(SanitizeAction.IntentReceived("https://www.banasiak.com/p?utm_source=a&id=1"))

      // then
      vm.stateFlow.value.parameters[QueryParam("id", "1")] shouldBeEqualTo true
      vm.stateFlow.value.sanitizedUrl shouldBeEqualTo "https://www.banasiak.com/p?id=1"
    }

  @Test
  fun `given text with no url in it, when the intent arrives, then the screen reports the failure and finishes`() =
    runTest {
      val vm = viewModel()

      vm.effectFlow.test {
        vm.postAction(SanitizeAction.IntentReceived("there is no link here"))
        awaitItem() shouldBeEqualTo SanitizeEffect.ShowErrorAndFinish(R.string.url_not_detected)
      }
    }

  @Test
  fun `given the intent was already handled, when it arrives again, then the state is left alone`() =
    runTest {
      // onCreate runs again after a configuration change while the ViewModel survives, so without
      // this guard a rotation would re-parse the intent and discard the user's checkbox choices
      val vm = viewModel()
      vm.postAction(SanitizeAction.IntentReceived("https://www.banasiak.com/p?utm_source=a"))
      val afterFirst = vm.stateFlow.value

      vm.postAction(SanitizeAction.IntentReceived("https://example.com/other"))

      vm.stateFlow.value shouldBeEqualTo afterFirst
    }

  @Test
  fun `given every tenth launch, when the intent arrives, then the review prompt is requested`() =
    runTest {
      coEvery { repository.getLaunchCountThenIncrement() } returns 10
      val vm = viewModel()

      vm.effectFlow.test {
        vm.postAction(SanitizeAction.IntentReceived("https://www.banasiak.com/p"))
        awaitItem() shouldBeEqualTo SanitizeEffect.ShowRateAppDialog
      }
    }

  // endregion

  @Test
  fun `given a stripped parameter, when it is toggled back on, then it returns to the sanitized url`() =
    runTest {
      val vm = viewModel()
      vm.postAction(SanitizeAction.IntentReceived("https://www.banasiak.com/p?utm_source=a&id=1"))

      vm.postAction(SanitizeAction.ParamToggled(QueryParam("id", "1"), true))

      vm.stateFlow.value.sanitizedUrl shouldBeEqualTo "https://www.banasiak.com/p?id=1"
    }

  // region short url decoding

  @Test
  fun `given the host redirects, when decoding, then the resolved url replaces the original`() =
    runTest {
      // given
      coEvery { repository.fetchRedirectUrl(any(), any()) } returns
        RedirectResult.Redirected("https://www.banasiak.com/full?utm_source=a".toHttpUrl())
      val vm = viewModel()
      vm.postAction(SanitizeAction.IntentReceived("https://ban.co/abc"))

      // when
      vm.postAction(SanitizeAction.FetchRedirect)

      // then
      val state = vm.stateFlow.value
      state.sanitizedUrl shouldBeEqualTo "https://www.banasiak.com/full"
      state.parameters.keys.map { it.name } shouldBeEqualTo listOf("utm_source")
      // the resolved url may itself be a redirect, so the action stays available
      state.canFetchRedirect.shouldBeTrue()
      state.loading.shouldBeFalse()
    }

  @Test
  fun `given the host does not redirect, when decoding, then the action is disabled`() =
    runTest {
      coEvery { repository.fetchRedirectUrl(any(), any()) } returns RedirectResult.NoRedirect
      val vm = viewModel()
      vm.postAction(SanitizeAction.IntentReceived("https://www.banasiak.com/p"))

      vm.postAction(SanitizeAction.FetchRedirect)

      val state = vm.stateFlow.value
      state.canFetchRedirect.shouldBeFalse()
      state.hint shouldBeEqualTo R.string.hint_redirect_not_detected
    }

  @Test
  fun `given the lookup never reached the host, when decoding, then the action stays available to retry`() =
    runTest {
      // a flaky network says nothing about whether the url redirects; conflating the two used to
      // disable the button for the rest of the session, with re-sharing the only way back
      coEvery { repository.fetchRedirectUrl(any(), any()) } returns RedirectResult.Failed
      val vm = viewModel()
      vm.postAction(SanitizeAction.IntentReceived("https://ban.co/abc"))

      vm.postAction(SanitizeAction.FetchRedirect)

      val state = vm.stateFlow.value
      state.canFetchRedirect.shouldBeTrue()
      state.hint shouldBeEqualTo R.string.hint_redirect_failed
      state.loading.shouldBeFalse()
    }

  @Test
  fun `given a lookup that failed, when a later attempt succeeds, then the error hint is cleared`() =
    runTest {
      val vm = viewModel()
      vm.postAction(SanitizeAction.IntentReceived("https://ban.co/abc"))

      coEvery { repository.fetchRedirectUrl(any(), any()) } returns RedirectResult.Failed
      vm.postAction(SanitizeAction.FetchRedirect)
      vm.stateFlow.value.hint shouldBeEqualTo R.string.hint_redirect_failed

      coEvery { repository.fetchRedirectUrl(any(), any()) } returns
        RedirectResult.Redirected("https://www.banasiak.com/full".toHttpUrl())
      vm.postAction(SanitizeAction.FetchRedirect)

      vm.stateFlow.value.hint shouldBeEqualTo R.string.hint_decode_short_url
    }

  // endregion

  // region buttons

  @Test
  fun `given a sanitized url, when share is tapped, then it is handed to the share sheet`() =
    runTest {
      val vm = viewModel()
      vm.postAction(SanitizeAction.IntentReceived("https://www.banasiak.com/p?utm_source=a"))

      vm.effectFlow.test {
        vm.postAction(SanitizeAction.ButtonTapped(ButtonType.SHARE))
        awaitItem() shouldBeEqualTo SanitizeEffect.ShareUrl("https://www.banasiak.com/p")
      }
    }

  @Test
  fun `given a sanitized url, when open is tapped, then it is handed to the browser`() =
    runTest {
      val vm = viewModel()
      vm.postAction(SanitizeAction.IntentReceived("https://www.banasiak.com/p?utm_source=a"))

      vm.effectFlow.test {
        vm.postAction(SanitizeAction.ButtonTapped(ButtonType.OPEN))
        awaitItem() shouldBeEqualTo SanitizeEffect.OpenUrl("https://www.banasiak.com/p")
      }
    }

  @Test
  fun `given android 13 or later, when copy is tapped, then the system shows its own confirmation`() =
    runTest {
      // Android 13 draws its own clipboard toast, so a second one from us would be a duplicate
      val vm = viewModel(apiLevel = 33)
      vm.postAction(SanitizeAction.IntentReceived("https://www.banasiak.com/p?utm_source=a"))

      vm.effectFlow.test {
        vm.postAction(SanitizeAction.ButtonTapped(ButtonType.COPY))
        awaitItem() shouldBeEqualTo SanitizeEffect.Finish
      }
      verify { clipboardManager.setPrimaryClip(any()) }
    }

  @Test
  fun `given a device older than android 13, when copy is tapped, then we show the toast ourselves`() =
    runTest {
      val vm = viewModel(apiLevel = 32)
      vm.postAction(SanitizeAction.IntentReceived("https://www.banasiak.com/p?utm_source=a"))

      vm.effectFlow.test {
        vm.postAction(SanitizeAction.ButtonTapped(ButtonType.COPY))
        awaitItem() shouldBeEqualTo SanitizeEffect.ShowToast(R.string.url_copied)
        awaitItem() shouldBeEqualTo SanitizeEffect.Finish
      }
    }

  // endregion

  @Test
  fun `given state saved by a previous process, when the view model is built, then the ui sees it`() =
    runTest {
      // a property initializer bypasses the custom setter, so restoring into the backing field alone
      // left the flow on its default and the screen came back empty after a process death
      val restored = SanitizeState(sanitizedUrl = "https://www.banasiak.com/restored", intentProcessed = true)
      every { savedState.get<SanitizeState>("state") } returns restored

      viewModel().stateFlow.value shouldBeEqualTo restored
    }

  @Test
  fun `given an effect raised before anyone subscribes, when a collector arrives, then it still arrives`() =
    runTest {
      // the Activity installs its collector asynchronously; a SharedFlow drops anything emitted in
      // the meantime, which left the sheet on screen with no way out
      val vm = viewModel()

      vm.postAction(SanitizeAction.Dismiss)

      vm.effectFlow.test { awaitItem() shouldBeEqualTo SanitizeEffect.Finish }
    }

  @Test
  fun `when the screen pauses, then state is stored and the kept parameters are written out`() =
    runTest {
      coEvery { repository.getEnabledParamsForHost("www.banasiak.com") } returns listOf("id")
      val vm = viewModel()
      vm.postAction(SanitizeAction.IntentReceived("https://www.banasiak.com/p?utm_source=a&id=1"))

      vm.onStateChanged(lifecycleOwner, Lifecycle.Event.ON_PAUSE)

      // written straight through rather than from a coroutine: the process may not live long enough
      verify { savedState.set("state", vm.stateFlow.value) }
      coVerify { repository.setEnabledParamsForHost("www.banasiak.com", listOf("id")) }
    }
}