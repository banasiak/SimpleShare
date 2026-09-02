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
  val canFetchRedirect: Boolean = true,
  val intentProcessed: Boolean = false,
  val launchCount: Int = 0,
  val loading: Boolean = false,
  val originalUrl: HttpUrl? = null,
  val parameters: List<QueryParam> = emptyList(),
  val sanitizedUrl: String = ""
) : Parcelable

sealed class SanitizeAction {
  data class ButtonTapped(val type: ButtonType) : SanitizeAction()
  data class IntentReceived(val text: String?) : SanitizeAction()
  data class ParamToggled(val index: Int, val enabled: Boolean) : SanitizeAction()
  data object Dismiss : SanitizeAction()
  data object FetchRedirect : SanitizeAction()
}

sealed class SanitizeEffect {
  data class OpenUrl(val url: String) : SanitizeEffect()
  data class ShareUrl(val url: String) : SanitizeEffect()
  data class ShowErrorAndFinish(@StringRes val message: Int) : SanitizeEffect()
  data class ShowToast(@StringRes val message: Int) : SanitizeEffect()
  data object Finish : SanitizeEffect()
  data object ShowRateAppDialog : SanitizeEffect()
}

// a query is an ordered list of name/value pairs, not a map: a name may legitimately repeat, and
// application/x-www-form-urlencoded actively produces that for multi-valued form controls
@Parcelize
data class QueryParam(
  val name: String,
  val value: String?,
  val enabled: Boolean = false
) : Parcelable

enum class ButtonType {
  COPY,
  OPEN,
  SHARE
}