package com.banasiak.android.simpleshare.sanitize

import android.os.Parcelable
import androidx.annotation.StringRes
import com.banasiak.android.simpleshare.R
import com.banasiak.android.simpleshare.data.HttpUrlParceler
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler
import okhttp3.HttpUrl

@Parcelize
@TypeParceler<HttpUrl?, HttpUrlParceler>
data class SanitizeState(
  @StringRes val hint: Int = R.string.hint_decode_short_url,
  val intentProcessed: Boolean = false,
  val launchCount: Int = 0,
  val loading: Boolean = false,
  val originalUrl: HttpUrl? = null,
  val parameters: Map<QueryParam, Boolean> = emptyMap(),
  val readOnly: Boolean = true, // if false, URL can be passed back to caller via setResult()
  val sanitizedUrl: String = ""
) : Parcelable

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

@Parcelize
data class QueryParam(
  val name: String,
  val value: String?
) : Parcelable

enum class ButtonType {
  COPY,
  OPEN,
  RETURN,
  SHARE
}