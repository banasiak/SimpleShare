package com.banasiak.android.simpleshare.common

import android.os.Parcelable
import androidx.lifecycle.SavedStateHandle
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import timber.log.Timber

fun <T> SavedStateHandle.save(state: T) {
  Timber.d("Persisting state to SavedStateHandle: $state")
  if (state !is Parcelable) {
    throw IllegalArgumentException("Unable to save state because it is not Parcelable")
  }
  this.set("state", state)
}

fun <T> SavedStateHandle.restore(): T? {
  val state = this.get<T>("state")
  Timber.d("Loaded state from SavedStateHandle: $state")
  return state
}

// subtle abuse of an extension function to promote my opinion on the subject...
fun String.toHttpsUrlOrNull(): HttpUrl? {
  return this.toHttpUrlOrNull()?.newBuilder()?.scheme("https")?.build()
}