package com.banasiak.android.simpleshare.data

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences

// drops the empty sets that releases through EIGHT wrote for every host a link was shared from
object EmptySetMigration : DataMigration<Preferences> {
  override suspend fun shouldMigrate(currentData: Preferences): Boolean = currentData.asMap().values.any { it.isEmptySet() }

  override suspend fun migrate(currentData: Preferences): Preferences {
    val migrated = currentData.toMutablePreferences()
    currentData.asMap().filterValues { it.isEmptySet() }.keys.forEach { migrated.remove(it) }
    return migrated.toPreferences()
  }

  override suspend fun cleanUp() = Unit

  private fun Any.isEmptySet(): Boolean = this is Set<*> && isEmpty()
}