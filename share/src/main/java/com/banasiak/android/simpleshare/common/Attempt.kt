package com.banasiak.android.simpleshare.common

import kotlin.coroutines.cancellation.CancellationException

/** [block]'s value, or null once [onFailure] has had what it threw. Cancellation is thrown on. */
inline fun <T> attempt(onFailure: (Exception) -> Unit = {}, block: () -> T): T? =
  try {
    block()
  } catch (e: CancellationException) {
    throw e
  } catch (e: Exception) {
    onFailure(e)
    null
  }