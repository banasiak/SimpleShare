package com.banasiak.android.simpleshare.target

data class ShareTargetState(
  val originalUrl: String? = null,
  val sanitizedUrl: String? = null,
  val parameters: List<Pair<String, String?>> = emptyList()
)

sealed class ShareTargetAction {
  data class IntentReceived(val text: String) : ShareTargetAction()
  data object ShareUrlTapped : ShareTargetAction()
}

sealed class ShareTargetEffect {
  data class ShareUrl(val url: String) : ShareTargetEffect()
}