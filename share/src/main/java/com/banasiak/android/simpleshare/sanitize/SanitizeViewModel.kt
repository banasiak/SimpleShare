package com.banasiak.android.simpleshare.sanitize

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.banasiak.android.simpleshare.R
import com.banasiak.android.simpleshare.common.BuildInfo
import com.banasiak.android.simpleshare.common.ClipboardHelper
import com.banasiak.android.simpleshare.data.RedirectResult
import com.banasiak.android.simpleshare.data.Repository
import com.banasiak.android.simpleshare.data.TrackerRules
import com.banasiak.android.simpleshare.extensions.restore
import com.banasiak.android.simpleshare.extensions.save
import com.banasiak.android.simpleshare.extensions.toHttpsUrlOrNull
import com.linkedin.urls.detection.UrlDetector
import com.linkedin.urls.detection.UrlDetectorOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import timber.log.Timber
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
class SanitizeViewModel @Inject constructor(
  private val buildInfo: BuildInfo,
  private val clipboard: ClipboardHelper,
  private val repository: Repository,
  private val savedState: SavedStateHandle,
  private val trackerRules: TrackerRules
) : ViewModel() {
  private var state = savedState.restore() ?: SanitizeState()
    set(value) {
      field = value
      Timber.v("state: $value")
      savedState.save(value)
      _stateFlow.value = value
    }

  private val _stateFlow = MutableStateFlow(state)
  val stateFlow = _stateFlow.asStateFlow()

  // a Channel buffers effects emitted before the Activity subscribes; a SharedFlow would drop them
  private val _effectFlow = Channel<SanitizeEffect>(Channel.BUFFERED)
  val effectFlow = _effectFlow.receiveAsFlow()

  fun postAction(action: SanitizeAction) {
    viewModelScope.launch {
      when (action) {
        is SanitizeAction.ButtonTapped -> onButtonTapped(action.type, state.sanitizedUrl)
        is SanitizeAction.FetchRedirect -> onFetchRedirect(state.originalUrl)
        is SanitizeAction.Dismiss -> _effectFlow.send(SanitizeEffect.Finish)
        is SanitizeAction.IntentReceived -> onIntentReceived(action.text)
        is SanitizeAction.ParamToggled -> onParamToggle(action.index, action.enabled)
      }
    }
  }

  private suspend fun onIntentReceived(text: String?) {
    if (state.intentProcessed) {
      Timber.w("Intent already processed")
      return
    }

    val url = text?.let { extractUrl(it) }
    if (url == null) {
      Timber.e("Unable to detect URL in received intent data: $text")
      _effectFlow.send(SanitizeEffect.ShowErrorAndFinish(R.string.url_not_detected))
      return
    }

    val okHttpUrl = url.toHttpsUrlOrNull()
    val params = buildParameterList(okHttpUrl)

    val launchCount = repository.getLaunchCountThenIncrement()
    // potentially prompt for a review every 10 app launches
    if (launchCount % 10 == 0) _effectFlow.send(SanitizeEffect.ShowRateAppDialog)

    state =
      state.copy(
        originalUrl = okHttpUrl,
        sanitizedUrl = sanitizeUrl(okHttpUrl, params),
        parameters = params,
        launchCount = launchCount,
        intentProcessed = true
      )
  }

  private fun extractUrl(text: String): String? {
    // attempt to extract the URL found in the string using this ancient library from LinkedIn
    // https://github.com/linkedin/URL-Detector
    val detector = UrlDetector(text, UrlDetectorOptions.Default)
    return detector
      .detect()
      .map { it.toString() }
      .maxByOrNull { it.length } // if the detector returns multiple URLs, the longest is probably the correct one
  }

  private suspend fun onParamToggle(index: Int, enabled: Boolean) {
    Timber.d("onParamToggle: index=$index, enabled=$enabled")
    val updatedParams = state.parameters.mapIndexed { i, param -> if (i == index) param.copy(enabled = enabled) else param }
    state =
      state.copy(
        parameters = updatedParams,
        sanitizedUrl = sanitizeUrl(state.originalUrl, updatedParams)
      )
    persistParamPreferences()
  }

  private suspend fun onFetchRedirect(originalUrl: HttpUrl?) {
    if (originalUrl == null) return

    state = state.copy(loading = true)

    state =
      when (val result = repository.fetchRedirectUrl(originalUrl, minimumDuration = 1.seconds)) {
        is RedirectResult.Redirected -> {
          Timber.d("URL redirect detected: ${result.url}")
          val parameters = buildParameterList(result.url)
          val sanitizedUrl = sanitizeUrl(result.url, parameters)
          // the new URL may redirect again, so leave the action available and clear any stale error
          state.copy(
            hint = R.string.hint_decode_short_url,
            originalUrl = result.url,
            parameters = parameters,
            sanitizedUrl = sanitizedUrl,
            loading = false
          )
        }
        // there is genuinely nothing to follow, so stop offering the action
        RedirectResult.NoRedirect -> {
          state.copy(hint = R.string.hint_redirect_not_detected, canFetchRedirect = false, loading = false)
        }
        // we never reached the host, so keep the action enabled and invite another attempt
        RedirectResult.Failed -> {
          state.copy(hint = R.string.hint_redirect_failed, loading = false)
        }
      }
  }

  private suspend fun onButtonTapped(type: ButtonType, sanitizedUrl: String) {
    Timber.d("onButtonTapped: $type")
    when (type) {
      ButtonType.COPY -> onCopyUrl(sanitizedUrl)
      ButtonType.OPEN -> _effectFlow.send(SanitizeEffect.OpenUrl(sanitizedUrl))
      ButtonType.SHARE -> _effectFlow.send(SanitizeEffect.ShareUrl(sanitizedUrl))
    }
  }

  private suspend fun onCopyUrl(url: String) {
    clipboard.copy(url)

    if (!isTiramisu()) {
      // only show a toast notification for devices < Android 13 (otherwise the system overlays its own UI)
      _effectFlow.send(SanitizeEffect.ShowToast(R.string.url_copied))
    }
    _effectFlow.send(SanitizeEffect.Finish)
  }

  private suspend fun sanitizeUrl(url: HttpUrl?, params: List<QueryParam>): String {
    if (url == null) {
      Timber.e("Unable to parse URL")
      _effectFlow.send(SanitizeEffect.ShowErrorAndFinish(R.string.unable_to_parse))
      return ""
    }

    val address =
      HttpUrl.Builder()
        .scheme(url.scheme)
        .host(url.host)
        .port(url.port) // a non-default port is part of the address, not tracking cruft
        .encodedPath(url.encodedPath)
        .build()
        .toString()
    // joined by hand: HttpUrl.Builder would escape every value again and undo the readable form
    val query = params.filter { it.enabled }.joinToString("&") { it.toReadableQueryPair() }

    return buildString {
      append(address)
      if (query.isNotEmpty()) append('?').append(query)
      // the fragment identifies a location within the page, so dropping it can break the link
      url.encodedFragment?.let { append('#').append(it) }
    }
  }

  private fun QueryParam.toReadableQueryPair(): String {
    val readableName = readableQueryComponent(name, isName = true)
    return if (value == null) readableName else "$readableName=${readableQueryComponent(value, isName = false)}"
  }

  // decoded for reading, except where that would change the query once it is parsed again: a space is
  // written as +, so a literal + stays escaped, & would end the pair, # would start the fragment, % an
  // escape, and = would end a name. Characters a URL cannot hold stay escaped so the result still pastes
  // as one link, and so do invisible and bidi characters, which would make the displayed link misleading
  private fun readableQueryComponent(text: String, isName: Boolean): String =
    buildString {
      var i = 0
      while (i < text.length) {
        val codePoint = text.codePointAt(i)
        when {
          codePoint == ' '.code -> append('+')
          mustEscape(codePoint, isName) -> appendEscaped(codePoint)
          else -> appendCodePoint(codePoint)
        }
        i += Character.charCount(codePoint)
      }
    }

  private fun mustEscape(codePoint: Int, isName: Boolean): Boolean =
    (codePoint < 0x80 && codePoint.toChar() in QUERY_ESCAPED) ||
      (isName && codePoint == '='.code) ||
      Character.getType(codePoint) in INVISIBLE_CHARACTER_TYPES

  private fun StringBuilder.appendEscaped(codePoint: Int) {
    for (byte in String(Character.toChars(codePoint)).toByteArray(Charsets.UTF_8)) {
      val bits = byte.toInt()
      append('%').append(HEX_DIGITS[(bits shr 4) and 0xF]).append(HEX_DIGITS[bits and 0xF])
    }
  }

  private suspend fun buildParameterList(url: HttpUrl?): List<QueryParam> {
    if (url == null) return emptyList()

    val (preferences, flagged) =
      coroutineScope {
        val preferences = async { repository.getParamPreferencesForHost(url.host) }
        val flagged = async { trackerRules.flaggedNames(url) }
        preferences.await() to flagged.await()
      }
    // walk the query by index: queryParameterNames is a Set and queryParameter() returns only the
    // first match, so a name-keyed read silently drops every repeat before the user ever sees it
    return (0 until url.querySize).map { index ->
      val name = url.queryParameterName(index)
      QueryParam(
        name = name,
        value = url.queryParameterValue(index),
        // the user's own choice for this host outranks the rules, and repeats of a name share one answer
        enabled = name in preferences.kept || (name !in preferences.removed && name !in flagged),
        flagged = name in flagged
      )
    }
  }

  private suspend fun persistParamPreferences() {
    val url = state.originalUrl ?: return

    // a name counts as kept when any of its occurrences is, and the names left unchecked go along so
    // the repository can tell a parameter that was removed from one this link simply didn't carry
    val enabled = state.parameters.filter { it.enabled }.map { it.name }.toSet()
    val disabled = state.parameters.map { it.name }.toSet() - enabled
    val flagged = state.parameters.filter { it.flagged }.map { it.name }.toSet()
    repository.updateParamPreferencesForHost(url.host, enabled, disabled, flagged)
  }

  @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
  private fun isTiramisu(): Boolean = buildInfo.apiLevel >= Build.VERSION_CODES.TIRAMISU
}

private const val HEX_DIGITS = "0123456789ABCDEF"

// ASCII that readableQueryComponent keeps escaped
private const val QUERY_ESCAPED = "%+&#\"<>\\^`{|}"

private val INVISIBLE_CHARACTER_TYPES =
  setOf(
    Character.CONTROL,
    Character.FORMAT,
    Character.SPACE_SEPARATOR,
    Character.LINE_SEPARATOR,
    Character.PARAGRAPH_SEPARATOR,
    Character.PRIVATE_USE,
    Character.SURROGATE,
    Character.UNASSIGNED
  ).map { it.toInt() }