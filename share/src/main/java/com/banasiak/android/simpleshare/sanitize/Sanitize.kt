package com.banasiak.android.simpleshare.sanitize

import androidx.annotation.StringRes
import com.banasiak.android.simpleshare.R
import okhttp3.HttpUrl

data class SanitizeState(
  val hint: HintType = HintType.DEFAULT,
  val launchCount: Int = 0,
  val loading: Boolean = false,
  val originalUrl: HttpUrl? = null,
  val parameters: Map<QueryParam, Boolean> = emptyMap(),
  val readOnly: Boolean = true, // if false, URL can be passed back to caller via setResult()
  val sanitizedUrl: String = ""
)

sealed class SanitizeAction {
  data class ButtonTapped(val type: ButtonType) : SanitizeAction()
  data class IntentReceived(val text: String?, val readOnly: Boolean) : SanitizeAction()
  data class ParamToggled(val param: QueryParam, val value: Boolean) : SanitizeAction()
  data object Dismiss : SanitizeAction()
  data object FetchRedirect : SanitizeAction()
}

sealed class SanitizeEffect {
  data class OpenUrl(val url: String) : SanitizeEffect()
  data class ReturnUrl(val url: String) : SanitizeEffect()
  data class ShareUrl(val url: String) : SanitizeEffect()
  data class ShowErrorAndFinish(@StringRes val message: Int) : SanitizeEffect()
  data class ShowToast(@StringRes val message: Int) : SanitizeEffect()
  data object Finish : SanitizeEffect()
  data object ShowRateAppDialog : SanitizeEffect()
}

data class QueryParam(
  val name: String,
  val value: String?
)

enum class ButtonType {
  COPY,
  OPEN,
  SANITIZE,
  SHARE
}

enum class HintType(
  @StringRes val string: Int,
  val isError: Boolean
) {
  DEFAULT(R.string.hint_decode_short_url, false),
  NO_REDIRECT(R.string.redirect_not_detected, true)
}