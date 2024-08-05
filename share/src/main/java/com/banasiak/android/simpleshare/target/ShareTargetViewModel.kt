package com.banasiak.android.simpleshare.target

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.os.Build
import android.os.PersistableBundle
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.banasiak.android.simpleshare.R
import com.banasiak.android.simpleshare.common.BuildInfo
import com.linkedin.urls.detection.UrlDetector
import com.linkedin.urls.detection.UrlDetectorOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ShareTargetViewModel @Inject constructor(
  private val buildInfo: BuildInfo,
  private val clipboardManager: ClipboardManager,
  private val savedState: SavedStateHandle
) : ViewModel() {

  companion object {
    const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"
  }

  private val _stateFlow = MutableStateFlow(ShareTargetState())
  val stateFlow = _stateFlow.asStateFlow()

  private val _effectFlow = MutableSharedFlow<ShareTargetEffect>(extraBufferCapacity = 1)
  val effectFlow = _effectFlow.asSharedFlow()

  private var state: ShareTargetState = ShareTargetState()
    set(value) {
      field = value
      Timber.v("state: $value")
      _stateFlow.tryEmit(value)
    }

  fun postAction(action: ShareTargetAction) {
    when (action) {
      is ShareTargetAction.CopyUrlTapped -> onCopyUrl(state.sanitizedUrl)
      is ShareTargetAction.IntentReceived -> onIntentReceived(action.text)
      is ShareTargetAction.ParamToggled -> onParamToggle(action.param, action.value)
      is ShareTargetAction.ShareUrlTapped -> onShareUrl(state.sanitizedUrl)
    }
  }

  private fun onIntentReceived(text: String) {
    val url = extractUrl(text)
    val okHttpUrl = url?.toHttpUrlOrNull()
    val params = buildParameterMap(okHttpUrl)
    state = state.copy(
      originalUrl = okHttpUrl,
      sanitizedUrl = sanitizeUrl(okHttpUrl, params),
      parameters = buildParameterMap(okHttpUrl)
    )
  }

  private fun extractUrl(text: String): String? {
    // attempt to extract the first URL found in the string using this ancient library from LinkedIn
    // https://github.com/linkedin/URL-Detector
    val detector = UrlDetector(text, UrlDetectorOptions.Default)
    return detector.detect().firstOrNull()?.toString()
  }

  private fun onParamToggle(param: QueryParam, value: Boolean) {
    Timber.d("onParamToggle: param = $param, value = $value")
    val updatedParams = state.parameters.toMutableMap()
    updatedParams[param] = value
    state = state.copy(parameters = updatedParams, sanitizedUrl = sanitizeUrl(state.originalUrl, updatedParams))
  }

  private fun onShareUrl(url: String?) {
    if (url==null) return
    _effectFlow.tryEmit(ShareTargetEffect.ShareUrl(url))
    _effectFlow.tryEmit(ShareTargetEffect.Finish)
  }

  private fun onCopyUrl(url: String?) {
    if (url==null) return

    val clip = ClipData.newPlainText("url", state.sanitizedUrl)
    val isSensitive = if (isTiramisu()) ClipDescription.EXTRA_IS_SENSITIVE else EXTRA_IS_SENSITIVE
    clip.apply { description.extras = PersistableBundle().apply { putBoolean(isSensitive, false) } }
    clipboardManager.setPrimaryClip(clip)

    if (!isTiramisu()) {
      // only show a toast notification for devices < Android 13 (otherwise the system overlays its own UI)
      _effectFlow.tryEmit(ShareTargetEffect.ShowToast(R.string.url_copied))
    }

    _effectFlow.tryEmit(ShareTargetEffect.Finish)
  }

  private fun sanitizeUrl(url: HttpUrl?, params: Map<QueryParam, Boolean>): String? {
    if (url==null) {
      _effectFlow.tryEmit(ShareTargetEffect.ShowToast(R.string.unable_to_parse))
      return null
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
    if (url==null) return emptyMap()

    val map = mutableMapOf<QueryParam, Boolean>()
    for (name in url.queryParameterNames) {
      map[QueryParam(name = name, value = url.queryParameter(name))] = false
    }

    return map.toMap()
  }

  @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
  private fun isTiramisu(): Boolean = buildInfo.apiLevel >= Build.VERSION_CODES.TIRAMISU
}