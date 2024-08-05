package com.banasiak.android.simpleshare.sanitize

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.os.Build
import android.os.PersistableBundle
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.banasiak.android.simpleshare.R
import com.banasiak.android.simpleshare.common.BuildInfo
import com.linkedin.urls.detection.UrlDetector
import com.linkedin.urls.detection.UrlDetectorOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SanitizeViewModel @Inject constructor(
  private val buildInfo: BuildInfo,
  private val clipboardManager: ClipboardManager,
  private val savedState: SavedStateHandle
) : ViewModel() {

  companion object {
    const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"
  }

  private val _stateFlow = MutableStateFlow(SanitizeState())
  val stateFlow = _stateFlow.asStateFlow()

  private val _effectFlow = MutableSharedFlow<SanitizeEffect>(extraBufferCapacity = 1)
  val effectFlow = _effectFlow.asSharedFlow()

  private var state: SanitizeState = SanitizeState()
    set(value) {
      field = value
      Timber.v("state: $value")
      _stateFlow.tryEmit(value)
    }

  fun postAction(action: SanitizeAction) {
    viewModelScope.launch {
      when (action) {
        is SanitizeAction.ButtonTapped -> onButtonTapped(action.type, state.sanitizedUrl)
        is SanitizeAction.Dismiss -> _effectFlow.emit(SanitizeEffect.Finish)
        is SanitizeAction.IntentReceived -> onIntentReceived(action.text, action.readOnly)
        is SanitizeAction.ParamToggled -> onParamToggle(action.param, action.value)
      }
    }
  }

  private suspend fun onIntentReceived(text: String, readOnly: Boolean) {
    val url = extractUrl(text)
    if (url == null) {
      Timber.w("Unable to extract URL from shared text")
      _effectFlow.emit(SanitizeEffect.ShowErrorAndFinish(R.string.url_not_detected))
      return
    }

    val okHttpUrl = url.toHttpUrlOrNull()
    val params = buildParameterMap(okHttpUrl)
    state = state.copy(
      originalUrl = okHttpUrl,
      sanitizedUrl = sanitizeUrl(okHttpUrl, params),
      parameters = params,
      readOnly = readOnly
    )
  }

  private fun extractUrl(text: String): String? {
    // attempt to extract the first URL found in the string using this ancient library from LinkedIn
    // https://github.com/linkedin/URL-Detector
    val detector = UrlDetector(text, UrlDetectorOptions.Default)
    return detector.detect().firstOrNull()?.toString()
  }

  private suspend fun onParamToggle(param: QueryParam, value: Boolean) {
    Timber.d("onParamToggle: param=$param, value=$value")
    val updatedParams = state.parameters.toMutableMap()
    updatedParams[param] = value
    state = state.copy(
      parameters = updatedParams,
      sanitizedUrl = sanitizeUrl(state.originalUrl, updatedParams)
    )
  }

  private suspend fun onButtonTapped(type: ButtonType, sanitizedUrl: String) {
    Timber.d("onButtonTapped: $type")
    when (type) {
      ButtonType.COPY -> onCopyUrl(sanitizedUrl)
      ButtonType.OPEN -> _effectFlow.emit(SanitizeEffect.OpenUrl(sanitizedUrl))
      ButtonType.SANITIZE -> _effectFlow.emit(SanitizeEffect.ReturnUrl(sanitizedUrl))
      ButtonType.SHARE -> _effectFlow.emit(SanitizeEffect.ShareUrl(sanitizedUrl))
    }
  }

  private suspend fun onCopyUrl(url: String) {
    val clip = ClipData.newPlainText("url", url)
    val isSensitive = if (isTiramisu()) ClipDescription.EXTRA_IS_SENSITIVE else EXTRA_IS_SENSITIVE

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
      _effectFlow.emit(SanitizeEffect.ShowToast(R.string.unable_to_parse))
      return ""
    }

    val builder = HttpUrl.Builder()
      .scheme(url.scheme)
      .host(url.host)
      .encodedPath(url.encodedPath)

    for (item in params) {
      if (item.value) {
        builder.addQueryParameter(name = item.key.name, value = item.key.value)
      }
    }

    return builder.build().toString()
  }

  private fun buildParameterMap(url: HttpUrl?): Map<QueryParam, Boolean> {
    if (url == null) return emptyMap()

    val map = mutableMapOf<QueryParam, Boolean>()
    for (name in url.queryParameterNames) {
      map[QueryParam(name = name, value = url.queryParameter(name))] = false
    }

    return map.toMap()
  }

  @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
  private fun isTiramisu(): Boolean = buildInfo.apiLevel >= Build.VERSION_CODES.TIRAMISU
}