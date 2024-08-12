package com.banasiak.android.simpleshare.data

import android.os.Parcel
import kotlinx.parcelize.Parceler
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object HttpUrlParceler : Parceler<HttpUrl?> {
  override fun HttpUrl?.write(parcel: Parcel, flags: Int) {
    parcel.writeString(this?.toString())
  }

  override fun create(parcel: Parcel): HttpUrl? {
    return parcel.readString()?.toHttpUrlOrNull()
  }
}