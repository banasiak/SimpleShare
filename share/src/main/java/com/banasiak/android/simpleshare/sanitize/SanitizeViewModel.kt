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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
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
  private val _stateFlow = MutableStateFlow(SanitizeState())
  val stateFlow = _stateFlow.asStateFlow()

  private val _effectFlow = MutableSharedFlow<SanitizeEffect>(extraBufferCapacity = 1)
  val effectFlow = _effectFlow.asSharedFlow()

  private var state: SanitizeState = savedState.restore() ?: SanitizeState()
    set(value) {
      field = value
      Timber.v("state: $value")
      _stateFlow.tryEmit(value)
    }

  override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
    Timber.v("Lifecycle onStateChanged(): $event")
    when (event) {
      Lifecycle.Event.ON_PAUSE -> {
        viewModelScope.launch {
          persistEnabledParameters()
          savedState.save(state)
        }
      }
      // Not necessary, because if this ViewModel is recreated, the state will be restored when SanitizeState is instantiated
      // Lifecycle.Event.ON_RESUME -> {
      //   viewModelScope.launch {
      //     state = savedState.restore<SanitizeState>() ?: SanitizeState()
      //   }
      // }
      else -> { /* NO-OP */ }
    }
  }

  fun postAction(action: SanitizeAction) {
    viewModelScope.launch {
      when (action) {
        is SanitizeAction.ButtonTapped -> onButtonTapped(action.type, state.sanitizedUrl)
        is SanitizeAction.FetchRedirect -> onFetchRedirect(state.originalUrl)
        is SanitizeAction.Dismiss -> _effectFlow.emit(SanitizeEffect.Finish)
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
      _effectFlow.emit(SanitizeEffect.ShowErrorAndFinish(R.string.url_not_detected))
      return
    }

    val okHttpUrl = url.toHttpsUrlOrNull()
    val params = buildParameterMap(okHttpUrl)

    val launchCount = repository.getLaunchCountThenIncrement()
    // potentially prompt for a review every 10 app launches
    if (launchCount % 10 == 0) _effectFlow.emit(SanitizeEffect.ShowRateAppDialog)

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
      ButtonType.OPEN -> _effectFlow.emit(SanitizeEffect.OpenUrl(sanitizedUrl))
      ButtonType.SHARE -> _effectFlow.emit(SanitizeEffect.ShareUrl(sanitizedUrl))
    }
  }

  private suspend fun onCopyUrl(url: String) {
    val clip = ClipData.newPlainText("url", url)
    val isSensitive = if (isTiramisu()) ClipDescription.EXTRA_IS_SENSITIVE else Constants.EXTRA_IS_SENSITIVE

    clip.apply { description.extras = PersistableBundle().apply { putBoolean(isSensitive, false) } }
    clipboardManager.setPrimaryClip(clip)

    if (!isTiramisu()) {
      // only show a toast notification for devices < Android 13 (otherwise the system overlays its own UI)
      _effectFlow.emit(SanitizeEffect.ShowToast(R.string.url_copied))
    }
    _effectFlow.emit(SanitizeEffect.Finish)
  }

  private suspend fun sanitizeUrl(url: HttpUrl?, params: Map<QueryParam, Boolean>): String {
    if (url == null) {
      Timber.e("Unable to parse URL")
      _effectFlow.emit(SanitizeEffect.ShowErrorAndFinish(R.string.unable_to_parse))
      return ""
    }

    return HttpUrl.Builder()
      .scheme(url.scheme)
      .host(url.host)
      .encodedPath(url.encodedPath)
      .apply {
        params.filter { item -> item.value }
          .forEach { item -> addQueryParameter(name = item.key.name, value = item.key.value) }
      }.build().toString()
  }

  private suspend fun buildParameterMap(url: HttpUrl?): Map<QueryParam, Boolean> {
    if (url == null) return emptyMap()

    val paramMap = mutableMapOf<QueryParam, Boolean>()
    val enabledParamNames = repository.getEnabledParamsForHost(url.host)
    for (name in url.queryParameterNames) {
      paramMap[QueryParam(name = name, value = url.queryParameter(name))] = enabledParamNames.contains(name)
    }
    return paramMap
  }

  private suspend fun persistEnabledParameters() {
    val url = state.originalUrl ?: return

    val enabledParams = state.parameters.filter { it.value }.map { it.key.name }
    repository.setEnabledParamsForHost(url.host, enabledParams)
  }

  @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
  private fun isTiramisu(): Boolean = buildInfo.apiLevel >= Build.VERSION_CODES.TIRAMISU
}