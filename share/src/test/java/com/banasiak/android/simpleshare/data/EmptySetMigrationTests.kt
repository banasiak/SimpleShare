package com.banasiak.android.simpleshare.data

import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeFalse
import org.amshove.kluent.shouldBeTrue
import org.junit.jupiter.api.Test

class EmptySetMigrationTests {
  private val launchCount = intPreferencesKey("launchCount")
  private val youtube = stringSetPreferencesKey("www.youtube.com")
  private val banasiak = stringSetPreferencesKey("www.banasiak.com")
  private val example = stringSetPreferencesKey("example.com")

  @Test
  fun `given hosts an older release recorded with nothing kept, when migrating, then only those hosts go`() =
    runTest {
      // an older release wrote an entry for every host a link was shared from. The ones holding a
      // choice are the user's data; the empty ones are only a record of where they have been.
      val stored =
        preferencesOf(
          launchCount to 7,
          youtube to setOf("v", "t"),
          banasiak to emptySet(),
          example to emptySet()
        )

      EmptySetMigration.shouldMigrate(stored).shouldBeTrue()
      EmptySetMigration.migrate(stored) shouldBeEqualTo preferencesOf(launchCount to 7, youtube to setOf("v", "t"))
    }

  @Test
  fun `given a store with no empty sets, when checked, then there is nothing to migrate`() =
    runTest {
      // this runs on every start, so it has to stand down once the store is clean
      val stored = preferencesOf(launchCount to 7, youtube to setOf("v", "t"))

      EmptySetMigration.shouldMigrate(stored).shouldBeFalse()
      EmptySetMigration.migrate(stored) shouldBeEqualTo stored
    }

  @Test
  fun `given a store that was just migrated, when checked again, then there is nothing left to do`() =
    runTest {
      val migrated = EmptySetMigration.migrate(preferencesOf(youtube to setOf("v"), banasiak to emptySet()))

      EmptySetMigration.shouldMigrate(migrated).shouldBeFalse()
    }

  @Test
  fun `given a store that has never been written, when checked, then there is nothing to migrate`() =
    runTest {
      EmptySetMigration.shouldMigrate(preferencesOf()).shouldBeFalse()
    }
}