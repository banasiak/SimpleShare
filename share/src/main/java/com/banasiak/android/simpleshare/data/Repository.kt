package com.banasiak.android.simpleshare.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Repository @Inject constructor(
  private val dataStore: DataStore<Preferences>
) {
  suspend fun setEnabledParamsForHost(host: String, params: List<String>) {
    Timber.d("Persist enabled params for '$host': $params")
    val key = stringSetPreferencesKey(host)
    dataStore.edit { prefs ->
      prefs[key] = params.toSet()
    }
  }

  suspend fun getEnabledParamsForHost(host: String): List<String> {
    val key = stringSetPreferencesKey(host)
    val params = dataStore.data.map { it[key] }.map { it?.toList() ?: emptyList() }.first()
    Timber.d("Retrieve enabled params for '$host': $params")
    return params
  }
}