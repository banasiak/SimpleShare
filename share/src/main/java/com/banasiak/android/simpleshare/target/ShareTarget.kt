package com.banasiak.android.simpleshare.target

import androidx.annotation.StringRes
import okhttp3.HttpUrl

data class ShareTargetState(
  val originalUrl: HttpUrl? = null,
  val sanitizedUrl: String? = null,
  val parameters: Map<QueryParam, Boolean> = emptyMap()
)

sealed class ShareTargetAction {
  data class IntentReceived(val text: String) : ShareTargetAction()
  data class ParamToggled(val param: QueryParam, val value: Boolean) : ShareTargetAction()
  data object CopyUrlTapped : ShareTargetAction()
  data object ShareUrlTapped : ShareTargetAction()
}

sealed class ShareTargetEffect {
  data object Finish : ShareTargetEffect()
  data class ShareUrl(val url: String) : ShareTargetEffect()
  data class ShowToast(@StringRes val message: Int) : ShareTargetEffect()
}

data class QueryParam(
  val name: String,
  val value: String?
)