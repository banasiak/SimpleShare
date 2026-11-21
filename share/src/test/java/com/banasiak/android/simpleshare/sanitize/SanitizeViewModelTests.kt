package com.banasiak.android.simpleshare.sanitize

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.banasiak.android.simpleshare.MainDispatcherRule
import com.banasiak.android.simpleshare.R
import com.banasiak.android.simpleshare.common.BuildInfo
import com.banasiak.android.simpleshare.common.ClipboardHelper
import com.banasiak.android.simpleshare.data.ParamPreferences
import com.banasiak.android.simpleshare.data.RedirectResult
import com.banasiak.android.simpleshare.data.Repository
import com.banasiak.android.simpleshare.data.TrackerRules
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeFalse
import org.amshove.kluent.shouldBeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherRule::class)
class SanitizeViewModelTests {
  private val clipboard: ClipboardHelper = mockk(relaxed = true)
  private val repository: Repository = mockk(relaxed = true)
  private val savedState: SavedStateHandle = mockk(relaxed = true)
  private val trackerRules: TrackerRules = mockk()

  private fun viewModel(apiLevel: Int = 33) =
    SanitizeViewModel(
      buildInfo = BuildInfo(apiLevel, "com.banasiak.android.simpleshare", "TEST", 1),
      clipboard = clipboard,
      repository = repository,
      savedState = savedState,
      trackerRules = trackerRules
    )

  @BeforeEach
  fun beforeEach() {
    every { savedState.get<SanitizeState>("state") } returns null
    coEvery { repository.getParamPreferencesForHost(any()) } returns ParamPreferences()
    coEvery { repository.getLaunchCountThenIncrement() } returns 1
    coEvery { repository.fetchRedirectUrl(any(), any()) } returns RedirectResult.NoRedirect
    // stands in for the catalog: utm_source is the one tracker these tests know about
    coEvery { trackerRules.flaggedNames(any()) } answers { firstArg<HttpUrl>().queryParameterNames intersect setOf("utm_source") }
  }

  // region intent handling

  @Test
  fun `given text around a link carrying a known tracker, when the intent arrives, then only the tracker starts unchecked`() =
    runTest {
      // given
      val vm = viewModel()

      // when
      vm.postAction(SanitizeAction.IntentReceived("Look at this https://www.banasiak.com/p?utm_source=a&id=1"))

      // then
      val state = vm.stateFlow.value
      state.parameters shouldBeEqualTo
        listOf(QueryParam("utm_source", "a", enabled = false, flagged = true), QueryParam("id", "1", enabled = true))
      state.sanitizedUrl shouldBeEqualTo "https://www.banasiak.com/p?id=1"
    }

  @Test
  fun `given a link the rules have nothing to say about, when the intent arrives, then it comes through whole`() =
    runTest {
      // boxes used to start unchecked, which stripped the v from a video link and the q from a search
      val vm = viewModel()

      vm.postAction(SanitizeAction.IntentReceived("https://www.banasiak.com/watch?v=abc&t=42"))

      vm.stateFlow.value.sanitizedUrl shouldBeEqualTo "https://www.banasiak.com/watch?v=abc&t=42"
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
      // application/x-www-form-urlencoded produces repeated names for multi-valued form controls, so
      // this is ordinary rather than malformed. Reading the query by name would return only the
      // first match and drop tag=y before the user ever saw it.
      val vm = viewModel()

      vm.postAction(SanitizeAction.IntentReceived("https://www.banasiak.com/p?tag=x&tag=y"))

      vm.stateFlow.value.parameters shouldBeEqualTo
        listOf(QueryParam("tag", "x"), QueryParam("tag", "y"))
      vm.stateFlow.value.sanitizedUrl shouldBeEqualTo "https://www.banasiak.com/p?tag=x&tag=y"

      // each occurrence has a box of its own
      vm.postAction(SanitizeAction.ParamToggled(0, false))
      vm.stateFlow.value.sanitizedUrl shouldBeEqualTo "https://www.banasiak.com/p?tag=y"
    }

  @Test
  fun `given a parameter repeated with the same value, when the intent arrives, then both are still listed`() =
    runTest {
      // a map keyed on name and value could not represent this pair at all; a list can
      val vm = viewModel()

      vm.postAction(SanitizeAction.IntentReceived("https://www.banasiak.com/p?a=1&a=1"))

      vm.stateFlow.value.parameters shouldBeEqualTo
        listOf(QueryParam("a", "1"), QueryParam("a", "1"))
    }

  @Test
  fun `given a tracker the user kept on this host before, when the intent arrives, then their choice outranks the rules`() =
    runTest {
      // given
      coEvery { repository.getParamPreferencesForHost("www.banasiak.com") } returns ParamPreferences(kept = setOf("utm_source"))
      val vm = viewModel()

      // when
      vm.postAction(SanitizeAction.IntentReceived("https://www.banasiak.com/p?utm_source=a&id=1"))

      // then
      vm.stateFlow.value.parameters shouldBeEqualTo
        listOf(QueryParam("utm_source", "a", enabled = true, flagged = true), QueryParam("id", "1", enabled = true))
      vm.stateFlow.value.sanitizedUrl shouldBeEqualTo "https://www.banasiak.com/p?utm_source=a&id=1"
    }

  @Test
  fun `given a parameter the user removed on this host before, when the intent arrives, then it starts unchecked`() =
    runTest {
      // given
      coEvery { repository.getParamPreferencesForHost("www.banasiak.com") } returns ParamPreferences(removed = setOf("id"))
      val vm = viewModel()

      // when
      vm.postAction(SanitizeAction.IntentReceived("https://www.banasiak.com/p?page=2&id=1"))

      // then
      vm.stateFlow.value.parameters shouldBeEqualTo
        listOf(QueryParam("page", "2", enabled = true), QueryParam("id", "1", enabled = false))
      vm.stateFlow.value.sanitizedUrl shouldBeEqualTo "https://www.banasiak.com/p?page=2"
    }

  @Test
  fun `given a choice made on one host, when a link to another host arrives, then the choice does not follow it`() =
    runTest {
      coEvery { repository.getParamPreferencesForHost("www.banasiak.com") } returns ParamPreferences(kept = setOf("utm_source"))
      val vm = viewModel()

      vm.postAction(SanitizeAction.IntentReceived("https://example.com/p?utm_source=a"))

      vm.stateFlow.value.sanitizedUrl shouldBeEqualTo "https://example.com/p"
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
  fun `given a tracker that started unchecked, when it is toggled back on, then it returns to the sanitized url`() =
    runTest {
      val vm = viewModel()
      vm.postAction(SanitizeAction.IntentReceived("https://www.banasiak.com/p?utm_source=a&id=1"))

      vm.postAction(SanitizeAction.ParamToggled(index = 0, enabled = true))

      vm.stateFlow.value.sanitizedUrl shouldBeEqualTo "https://www.banasiak.com/p?utm_source=a&id=1"
    }

  @Test
  fun `given a parameter that started checked, when it is toggled off, then it leaves the sanitized url`() =
    runTest {
      val vm = viewModel()
      vm.postAction(SanitizeAction.IntentReceived("https://www.banasiak.com/p?utm_source=a&id=1"))

      vm.postAction(SanitizeAction.ParamToggled(index = 1, enabled = false))

      vm.stateFlow.value.sanitizedUrl shouldBeEqualTo "https://www.banasiak.com/p"
    }

  // region readable query

  @Test
  fun `given kept values with escaped punctuation, when sanitizing, then the query reads decoded`() =
    runTest {
      // given
      val vm = viewModel()

      // when
      vm.postAction(SanitizeAction.IntentReceived("https://www.banasiak.com/p?list=a%2Cb%2Cc&next=https%3A%2F%2Fexample.com%2Fpage"))

      // then
      vm.stateFlow.value.sanitizedUrl shouldBeEqualTo "https://www.banasiak.com/p?list=a,b,c&next=https://example.com/page"
    }

  @Test
  fun `given a kept value in another script, when sanitizing, then it reads as written`() =
    runTest {
      val vm = viewModel()

      vm.postAction(SanitizeAction.IntentReceived("https://www.banasiak.com/s?q=caf%C3%A9"))

      vm.stateFlow.value.sanitizedUrl shouldBeEqualTo "https://www.banasiak.com/s?q=café"
    }

  @Test
  fun `given kept values holding a plus, an ampersand or a hash, when sanitizing, then those stay escaped`() =
    runTest {
      // decoded, the plus would read as a space, the ampersand would split the nested link into a
      // second parameter, and the hash would turn the rest of the query into a fragment
      val vm = viewModel()

      vm.postAction(
        SanitizeAction.IntentReceived(
          "https://www.banasiak.com/s?q=c%2B%2B+tutorial&u=https%3A%2F%2Fshop.com%2Fp%3Fa%3D1%26b%3D2&tag=%23news"
        )
      )

      vm.stateFlow.value.sanitizedUrl shouldBeEqualTo
        "https://www.banasiak.com/s?q=c%2B%2B+tutorial&u=https://shop.com/p?a=1%26b=2&tag=%23news"
    }

  @Test
  fun `given kept values with invisible or bidi characters, when sanitizing, then they stay escaped`() =
    runTest {
      // a decoded right-to-left override reorders everything after it, so the link on screen would no
      // longer read the way it actually goes
      val vm = viewModel()

      vm.postAction(SanitizeAction.IntentReceived("https://www.banasiak.com/p?f=%E2%80%AEevil&g=nb%C2%A0sp"))

      vm.stateFlow.value.sanitizedUrl shouldBeEqualTo "https://www.banasiak.com/p?f=%E2%80%AEevil&g=nb%C2%A0sp"
    }

  @Test
  fun `given a kept value holding an apostrophe, when sanitizing, then the apostrophe stays escaped`() =
    runTest {
      // decoded, it would close a single-quoted attribute in a page that pastes the link in unescaped,
      // and the parentheses beside it would then read as script
      val vm = viewModel()

      vm.postAction(SanitizeAction.IntentReceived("https://www.banasiak.com/s?q=don%27t+(stop)&r=x'y"))

      vm.stateFlow.value.sanitizedUrl shouldBeEqualTo "https://www.banasiak.com/s?q=don%27t+(stop)&r=x%27y"
    }

  @Test
  fun `given a kept value holding a link to a lookalike host, when sanitizing, then the nested host's non-ASCII stays escaped`() =
    runTest {
      // decoded, the Cyrillic a and the ideographic full stop would read as apple.com; the rest of the
      // nested link, and the value beside it, still read decoded
      val vm = viewModel()

      vm.postAction(
        SanitizeAction.IntentReceived(
          "https://www.banasiak.com/r?next=https%3A%2F%2F%D0%B0pple%E3%80%82com%2Fcaf%C3%A9%3Fq%3Dcaf%C3%A9" +
            "&u=%2F%2Fb%C3%BCcher.de&q=caf%C3%A9"
        )
      )

      vm.stateFlow.value.sanitizedUrl shouldBeEqualTo
        "https://www.banasiak.com/r?next=https://%D0%B0pple%E3%80%82com/café?q=café&u=//b%C3%BCcher.de&q=café"
    }

  @Test
  fun `given a nested link written another way a browser follows, when sanitizing, then its host's non-ASCII stays escaped`() =
    runTest {
      // a browser skips any run of slashes and backslashes before the host, drops tabs and line breaks
      // anywhere, and reads http: with no slashes at all as a host when the page is https
      val vm = viewModel()

      vm.postAction(
        SanitizeAction.IntentReceived(
          "https://www.banasiak.com/r?a=https%3A%2F%2F%2F%D0%B0pple.com%2Flogin&b=https%3A%5C%5C%D0%B0pple.com" +
            "&c=%2F%5C%D0%B0pple.com&d=https%3A%2F%09%2F%D0%B0pple.com&e=https%3A%2F%2Fa%09%D1%80ple.com" +
            "&f=http%3A%D0%B0pple.com&g=HTTP%3A%2F%D0%B0pple.com&h=note%3Acaf%C3%A9"
        )
      )

      vm.stateFlow.value.sanitizedUrl shouldBeEqualTo
        "https://www.banasiak.com/r?a=https:///%D0%B0pple.com/login&b=https:%5C%5C%D0%B0pple.com" +
        "&c=/%5C%D0%B0pple.com&d=https:/%09/%D0%B0pple.com&e=https://a%09%D1%80ple.com" +
        "&f=http:%D0%B0pple.com&g=HTTP:/%D0%B0pple.com&h=note:café"
    }

  @Test
  fun `given values that decoding could corrupt, when sanitizing, then the result parses back to the same parameters`() =
    runTest {
      val names = listOf("a", "b", "c", "d", "e=name", "f", "g", "h", "i", "j", "k", "l", "m", "n")
      val vm = viewModel()

      vm.postAction(
        SanitizeAction.IntentReceived(
          "https://www.banasiak.com/p?a=1%2B1%3D2&b=x%26y%23z&c=100%25&d=%22q%22+and+%3Ctag%3E" +
            "&e%3Dname=v&f=%E2%80%AEevil&g=nb%C2%A0sp&h=%F0%9F%98%80&i=%00&j=&k" +
            "&l=%27&m=https%3A%2F%2F%D0%B0pple.com%2F%2F%2F%D0%B0&n=http%3A%2F%5C%09%D0%B0"
        )
      )

      val state = vm.stateFlow.value
      state.parameters.map { it.name } shouldBeEqualTo names
      val reparsed = state.sanitizedUrl.toHttpUrl()
      (0 until reparsed.querySize).map { reparsed.queryParameterName(it) to reparsed.queryParameterValue(it) } shouldBeEqualTo
        state.parameters.map { it.name to it.value }
    }

  // endregion

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
      state.parameters.map { it.name } shouldBeEqualTo listOf("utm_source")
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
      verify { clipboard.copy("https://www.banasiak.com/p") }
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

  // region saving

  @Test
  fun `when the state changes, then it is saved at once`() =
    runTest {
      // nothing tells a ViewModel that its process is about to be killed, so there is no later
      // moment to save at
      val vm = viewModel()

      vm.postAction(SanitizeAction.IntentReceived("https://www.banasiak.com/p?utm_source=a&id=1"))

      verify { savedState.set("state", vm.stateFlow.value) }
    }

  @Test
  fun `when a box is toggled, then the boxes for this link are written out`() =
    runTest {
      val vm = viewModel()
      vm.postAction(SanitizeAction.IntentReceived("https://www.banasiak.com/p?utm_source=a&id=1&page=2"))

      vm.postAction(SanitizeAction.ParamToggled(index = 2, enabled = false))

      // what the rules flagged goes along, because the repository remembers only the boxes that were
      // left against them
      coVerify {
        repository.updateParamPreferencesForHost(
          "www.banasiak.com",
          enabled = setOf("id"),
          disabled = setOf("utm_source", "page"),
          flagged = setOf("utm_source")
        )
      }
    }

  @Test
  fun `given a link whose boxes are never touched, when it is shared, then nothing is written`() =
    runTest {
      // an untouched box is not a choice, and writing for every link would turn the store into a
      // history of the hosts links were shared from
      val vm = viewModel()
      vm.postAction(SanitizeAction.IntentReceived("https://www.banasiak.com/p?utm_source=a&id=1"))

      vm.postAction(SanitizeAction.ButtonTapped(ButtonType.SHARE))

      coVerify(exactly = 0) { repository.updateParamPreferencesForHost(any(), any(), any(), any()) }
    }

  @Test
  fun `given a repeated name with one occurrence unchecked, when it is toggled, then the name still counts as kept`() =
    runTest {
      // preferences are per name, so two boxes have to collapse into one answer; keeping errs toward
      // the link still working
      val vm = viewModel()
      vm.postAction(SanitizeAction.IntentReceived("https://www.banasiak.com/p?tag=x&tag=y"))

      vm.postAction(SanitizeAction.ParamToggled(index = 0, enabled = false))

      coVerify {
        repository.updateParamPreferencesForHost("www.banasiak.com", enabled = setOf("tag"), disabled = emptySet(), flagged = emptySet())
      }
    }

  @Test
  fun `given a short link that was decoded, when a box is toggled, then the boxes are saved for the host it led to`() =
    runTest {
      coEvery { repository.fetchRedirectUrl(any(), any()) } returns
        RedirectResult.Redirected("https://www.banasiak.com/full?utm_source=a&id=1".toHttpUrl())
      val vm = viewModel()
      vm.postAction(SanitizeAction.IntentReceived("https://ban.co/abc"))
      vm.postAction(SanitizeAction.FetchRedirect)

      vm.postAction(SanitizeAction.ParamToggled(index = 0, enabled = true))

      coVerify {
        repository.updateParamPreferencesForHost(
          "www.banasiak.com",
          enabled = setOf("utm_source", "id"),
          disabled = emptySet(),
          flagged = setOf("utm_source")
        )
      }
    }

  @Test
  fun `given a box unchecked on a short link, when it is decoded, then the box stays unchecked on the link it led to`() =
    runTest {
      // the decoded link's boxes were built afresh from its own host, so a parameter unchecked on
      // youtu.be came back checked on www.youtube.com the moment the cloud was tapped
      coEvery { repository.fetchRedirectUrl(any(), any()) } returns
        RedirectResult.Redirected("https://www.banasiak.com/watch?is=x&v=1".toHttpUrl())
      val vm = viewModel()
      vm.postAction(SanitizeAction.IntentReceived("https://ban.co/abc?is=x"))
      vm.postAction(SanitizeAction.ParamToggled(index = 0, enabled = false))

      vm.postAction(SanitizeAction.FetchRedirect)

      val state = vm.stateFlow.value
      state.parameters shouldBeEqualTo listOf(QueryParam("is", "x", enabled = false), QueryParam("v", "1"))
      state.sanitizedUrl shouldBeEqualTo "https://www.banasiak.com/watch?v=1"
    }

  @Test
  fun `given a box unchecked on a short link, when it is decoded, then the choice is saved for the host it led to`() =
    runTest {
      // otherwise the next link shared straight from that host would come up with the box checked
      coEvery { repository.fetchRedirectUrl(any(), any()) } returns
        RedirectResult.Redirected("https://www.banasiak.com/watch?is=x&v=1".toHttpUrl())
      val vm = viewModel()
      vm.postAction(SanitizeAction.IntentReceived("https://ban.co/abc?is=x"))
      vm.postAction(SanitizeAction.ParamToggled(index = 0, enabled = false))

      vm.postAction(SanitizeAction.FetchRedirect)

      coVerify {
        repository.updateParamPreferencesForHost("www.banasiak.com", enabled = setOf("v"), disabled = setOf("is"), flagged = emptySet())
      }
    }

  @Test
  fun `given a tracker kept on a short link, when it is decoded, then it stays kept on the link it led to`() =
    runTest {
      coEvery { repository.fetchRedirectUrl(any(), any()) } returns
        RedirectResult.Redirected("https://www.banasiak.com/full?utm_source=a&id=1".toHttpUrl())
      val vm = viewModel()
      vm.postAction(SanitizeAction.IntentReceived("https://ban.co/abc?utm_source=a"))
      vm.postAction(SanitizeAction.ParamToggled(index = 0, enabled = true))

      vm.postAction(SanitizeAction.FetchRedirect)

      vm.stateFlow.value.parameters shouldBeEqualTo
        listOf(QueryParam("utm_source", "a", enabled = true, flagged = true), QueryParam("id", "1"))
    }

  @Test
  fun `given a short link whose boxes are never touched, when it is decoded, then nothing is written`() =
    runTest {
      coEvery { repository.fetchRedirectUrl(any(), any()) } returns
        RedirectResult.Redirected("https://www.banasiak.com/full?utm_source=a&id=1".toHttpUrl())
      val vm = viewModel()
      vm.postAction(SanitizeAction.IntentReceived("https://ban.co/abc?utm_source=a"))

      vm.postAction(SanitizeAction.FetchRedirect)

      coVerify(exactly = 0) { repository.updateParamPreferencesForHost(any(), any(), any(), any()) }
    }

  // endregion
}