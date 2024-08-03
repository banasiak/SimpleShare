package com.banasiak.android.simpleshare.target

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ShareTargetViewModel @Inject constructor(
  private val okHttpClient: OkHttpClient,
  private val savedState: SavedStateHandle
) : ViewModel() {

  private val _stateFlow = MutableStateFlow(ShareTargetState())
  val stateFlow = _stateFlow.asStateFlow()

  private val _effectFlow = MutableSharedFlow<ShareTargetEffect>(extraBufferCapacity = 1)
  val effectFlow = _effectFlow.asSharedFlow()

  private var state: ShareTargetState = ShareTargetState()
    set(value) {
      field = value
      _stateFlow.tryEmit(value)
    }

  fun postAction(action: ShareTargetAction) {
    when (action) {
      is ShareTargetAction.IntentReceived -> onIntentReceived(action.text)
      is ShareTargetAction.ShareUrlTapped -> onShareUrl(state.sanitizedUrl)
    }
  }

  private fun onIntentReceived(text: String) {
    val okHttpUrl = text.toHttpUrlOrNull()
    if (okHttpUrl!=null) {
      Timber.wtf("URL successfully parsed: $okHttpUrl")
      state = state.copy(
        originalUrl = okHttpUrl.toString(),
        sanitizedUrl = sanitizeUrl(okHttpUrl),
        parameters = buildParameterMap(okHttpUrl).toList()
      )
    }
  }

  private fun onShareUrl(url: String?) {
    if (url==null) {
      Timber.w("not sharing null url")
      return
    }
    _effectFlow.tryEmit(ShareTargetEffect.ShareUrl(url))
  }

  private fun sanitizeUrl(url: HttpUrl): String {
    val sanitized = HttpUrl.Builder()
      .scheme(url.scheme)
      .host(url.host)
      .encodedPath(url.encodedPath)
      .build()
      .toString()
    Timber.wtf("sanitized URL: $sanitized")
    return sanitized
  }

  private fun buildParameterMap(url: HttpUrl): Map<String, String?> {
    val map: MutableMap<String, String?> = mutableMapOf()
    for (name in url.queryParameterNames) {
      map[name] = url.queryParameter(name)
    }
    return map.toMap()
  }
}