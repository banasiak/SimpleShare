package com.banasiak.android.simpleshare.common

import android.os.SystemClock
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class DurationClock(private val durationUnit: DurationUnit) {
  fun now(): Duration {
    return SystemClock.elapsedRealtime().toDuration(durationUnit)
  }
}