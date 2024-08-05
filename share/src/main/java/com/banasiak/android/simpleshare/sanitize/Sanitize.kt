package com.banasiak.android.simpleshare.sanitize

import androidx.annotation.StringRes
import okhttp3.HttpUrl

data class SanitizeState(
  val originalUrl: HttpUrl? = null,
  val sanitizedUrl: String = "",
  val parameters: Map<QueryParam, Boolean> = emptyMap(),
  val readOnly: Boolean = true // if false, URL can be passed back to caller via setResult()
)

sealed class SanitizeAction {
  data class IntentReceived(val text: String, val readOnly: Boolean) : SanitizeAction()
  data class ParamToggled(val param: QueryParam, val value: Boolean) : SanitizeAction()
  data class ButtonTapped(val type: ButtonType) : SanitizeAction()
}

sealed class SanitizeEffect {
  data class OpenUrl(val url: String) : SanitizeEffect()
  data class ReturnUrl(val url: String) : SanitizeEffect()
  data class ShareUrl(val url: String) : SanitizeEffect()
  data class ShowErrorAndFinish(@StringRes val message: Int) : SanitizeEffect()
  data class ShowToast(@StringRes val message: Int) : SanitizeEffect()
  data object Finish : SanitizeEffect()
}

data class QueryParam(
  val name: String,
  val value: String?
)

enum class ButtonType {
  COPY, OPEN, SANITIZE, SHARE
}