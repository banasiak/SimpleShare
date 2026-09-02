package com.banasiak.android.simpleshare.sanitize

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.os.Build
import android.os.PersistableBundle
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.banasiak.android.simpleshare.R
import com.banasiak.android.simpleshare.common.BuildInfo
import com.banasiak.android.simpleshare.common.Constants
import com.banasiak.android.simpleshare.common.restore
import com.banasiak.android.simpleshare.common.save
import com.banasiak.android.simpleshare.common.toHttpsUrlOrNull
import com.banasiak.android.simpleshare.data.Repository
import com.linkedin.urls.detection.UrlDetector
import com.linkedin.urls.detection.UrlDetectorOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import timber.log.Timber
import javax.inject.Inject
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@HiltViewModel
class SanitizeViewModel @Inject constructor(
  private val buildInfo: BuildInfo,
  private val clipboardManager: ClipboardManager,
  private val repository: Repository,
  private val savedState: SavedStateHandle
) : ViewModel(), LifecycleEventObserver {
  // seed the flow from the SavedStateHandle -- a property initializer bypasses the custom setter below,
  // so restoring into `state` alone would leave the UI showing an empty screen after process death
  private val _stateFlow = MutableStateFlow(savedState.restore<SanitizeState>() ?: SanitizeState())
  val stateFlow = _stateFlow.asStateFlow()

  // a Channel buffers effects emitted before the Activity subscribes; a SharedFlow would drop them
  private val _effectFlow = Channel<SanitizeEffect>(Channel.BUFFERED)
  val effectFlow = _effectFlow.receiveAsFlow()

  private var state: SanitizeState
    get() = _stateFlow.value
    set(value) {
      Timber.v("state: $value")
      _stateFlow.value = value
    }

  override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
    Timber.v("Lifecycle onStateChanged(): $event")
    when (event) {
      Lifecycle.Event.ON_PAUSE -> {
        // write to the SavedStateHandle synchronously; the process may not survive long enough for a coroutine to run
        savedState.save(state)
        viewModelScope.launch { persistEnabledParameters() }
      }
      // Not necessary, because if this ViewModel is recreated, the state will be restored when SanitizeState is instantiated
      else -> { /* NO-OP */ }
    }
  }

  fun postAction(action: SanitizeAction) {
    viewModelScope.launch {
      when (action) {
        is SanitizeAction.ButtonTapped -> onButtonTapped(action.type, state.sanitizedUrl)
        is SanitizeAction.FetchRedirect -> onFetchRedirect(state.originalUrl)
        is SanitizeAction.Dismiss -> _effectFlow.send(SanitizeEffect.Finish)
        is SanitizeAction.IntentReceived -> onIntentReceived(action.text)
        is SanitizeAction.ParamToggled -> onParamToggle(action.param, action.value)
      }
    }
  }

  private suspend fun onIntentReceived(text: String?) {
    if (state.intentProcessed) {
      Timber.w("Intent already processed")
      return
    }

    val url = text?.let { extractUrl(it) }
    if (text == null || url == null) {
      Timber.e("Unable to detect URL in received intent data: $text")
      _effectFlow.send(SanitizeEffect.ShowErrorAndFinish(R.string.url_not_detected))
      return
    }

    val okHttpUrl = url.toHttpsUrlOrNull()
    val params = buildParameterMap(okHttpUrl)

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
      .sortedByDescending { it.length } // if the detector returns multiple URLs, the longest is probably the correct one
      .firstOrNull()
  }

  private suspend fun onParamToggle(param: QueryParam, value: Boolean) {
    Timber.d("onParamToggle: param=$param, value=$value")
    val updatedParams = state.parameters.toMutableMap()
    updatedParams[param] = value
    state =
      state.copy(
        parameters = updatedParams,
        sanitizedUrl = sanitizeUrl(state.originalUrl, updatedParams)
      )
  }

  private suspend fun onFetchRedirect(originalUrl: HttpUrl?) {
    if (originalUrl == null) return

    state = state.copy(loading = true)

    repository.fetchRedirectUrl(originalUrl, minimumDuration = 1000.toDuration(DurationUnit.MILLISECONDS))?.let { newUrl ->
      Timber.d("URL redirect detected: $newUrl")
      val parameters = buildParameterMap(newUrl)
      val sanitizedUrl = sanitizeUrl(newUrl, parameters)
      state =
        state.copy(
          originalUrl = newUrl,
          parameters = parameters,
          sanitizedUrl = sanitizedUrl,
          loading = false
        )
      return
    }

    state = state.copy(hint = R.string.hint_redirect_not_detected, loading = false)
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
    val clip = ClipData.newPlainText("url", url)
    val isSensitive = if (isTiramisu()) ClipDescription.EXTRA_IS_SENSITIVE else Constants.EXTRA_IS_SENSITIVE

    clip.apply { description.extras = PersistableBundle().apply { putBoolean(isSensitive, false) } }
    clipboardManager.setPrimaryClip(clip)

    if (!isTiramisu()) {
      // only show a toast notification for devices < Android 13 (otherwise the system overlays its own UI)
      _effectFlow.send(SanitizeEffect.ShowToast(R.string.url_copied))
    }
    _effectFlow.send(SanitizeEffect.Finish)
  }

  private suspend fun sanitizeUrl(url: HttpUrl?, params: Map<QueryParam, Boolean>): String {
    if (url == null) {
      Timber.e("Unable to parse URL")
      _effectFlow.send(SanitizeEffect.ShowErrorAndFinish(R.string.unable_to_parse))
      return ""
    }

    return HttpUrl.Builder()
      .scheme(url.scheme)
      .host(url.host)
      .port(url.port) // a non-default port is part of the address, not tracking cruft
      .encodedPath(url.encodedPath)
      .apply {
        params.filter { item -> item.value }
          .forEach { item -> addQueryParameter(name = item.key.name, value = item.key.value) }
        // the fragment identifies a location within the page, so dropping it can break the link
        encodedFragment(url.encodedFragment)
      }.build().toString()
  }

  private suspend fun buildParameterMap(url: HttpUrl?): Map<QueryParam, Boolean> {
    if (url == null) return emptyMap()

    val enabledParamNames = repository.getEnabledParamsForHost(url.host)
    // walking the query by index rather than by name costs nothing and keeps ?tag=x&tag=y as two
    // entries; only parameters identical in both name and value collapse into one
    return (0 until url.querySize).associate { index ->
      val name = url.queryParameterName(index)
      QueryParam(name = name, value = url.queryParameterValue(index)) to enabledParamNames.contains(name)
    }
  }

  private suspend fun persistEnabledParameters() {
    val url = state.originalUrl ?: return

    val enabledParams = state.parameters.filter { it.value }.map { it.key.name }.distinct()
    repository.setEnabledParamsForHost(url.host, enabledParams)
  }

  @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
  private fun isTiramisu(): Boolean = buildInfo.apiLevel >= Build.VERSION_CODES.TIRAMISU
}