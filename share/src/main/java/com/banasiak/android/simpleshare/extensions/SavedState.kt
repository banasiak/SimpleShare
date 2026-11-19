package com.banasiak.android.simpleshare.extensions

import android.os.Parcelable
import androidx.lifecycle.SavedStateHandle
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