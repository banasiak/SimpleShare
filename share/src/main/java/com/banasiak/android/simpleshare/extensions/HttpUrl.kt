package com.banasiak.android.simpleshare.extensions

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

// subtle abuse of an extension function to promote my opinion on the subject...
fun String.toHttpsUrlOrNull(): HttpUrl? {
  return this.toHttpUrlOrNull()?.newBuilder()?.scheme("https")?.build()
}