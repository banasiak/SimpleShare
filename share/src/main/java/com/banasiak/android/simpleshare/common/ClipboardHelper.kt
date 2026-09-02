package com.banasiak.android.simpleshare.common

import android.content.ClipData
import android.content.ClipboardManager
import android.os.PersistableBundle
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClipboardHelper @Inject constructor(
  private val clipboardManager: ClipboardManager
) {
  fun copy(text: String) {
    val clip = ClipData.newPlainText("url", text)
    // marked not sensitive so that Android 13 and later show the sanitized URL in their own
    // clipboard preview rather than masking it
    clip.description.extras = PersistableBundle().apply { putBoolean(Constants.EXTRA_IS_SENSITIVE, false) }
    clipboardManager.setPrimaryClip(clip)
  }
}