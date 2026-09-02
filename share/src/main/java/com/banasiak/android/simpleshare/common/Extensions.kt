package com.banasiak.android.simpleshare.common

import android.os.Parcelable
import androidx.lifecycle.SavedStateHandle
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import timber.log.Timber

private const val KEY_STATE = "state"

fun <T : Parcelable> SavedStateHandle.save(state: T) {
  Timber.d("Persisting state to SavedStateHandle: $state")
  this[KEY_STATE] = state
}

fun <T : Parcelable> SavedStateHandle.restore(): T? {
  val state = this.get<T>(KEY_STATE)
  Timber.d("Loaded state from SavedStateHandle: $state")
  return state
}

// subtle abuse of an extension function to promote my opinion on the subject...
fun String.toHttpsUrlOrNull(): HttpUrl? {
  return this.toHttpUrlOrNull()?.newBuilder()?.scheme("https")?.build()
}