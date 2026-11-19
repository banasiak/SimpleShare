package com.banasiak.android.simpleshare.common

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeFalse
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldBeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException

class AttemptTests {
  @Test
  fun `given a block that returns, when attempted, then its value comes back and nothing is reported`() {
    var reported: Exception? = null

    attempt(onFailure = { reported = it }) { 42 } shouldBeEqualTo 42

    reported.shouldBeNull()
  }

  @Test
  fun `given a block that throws, when attempted, then the failure is reported and null comes back`() {
    var reported: Exception? = null
    val failure = IOException("no network")

    attempt<Int>(onFailure = { reported = it }) { throw failure }.shouldBeNull()

    reported shouldBeEqualTo failure
  }

  @Test
  fun `given a block that is canceled, when attempted, then the cancellation is thrown on and never reported`() {
    // swallowing it would let the work carry on after its scope is gone
    var reported = false

    assertThrows<CancellationException> {
      attempt<Unit>(onFailure = { reported = true }) { throw CancellationException("scope is gone") }
    }

    reported.shouldBeFalse()
  }

  @Test
  fun `given a coroutine suspended inside an attempt, when it is canceled, then nothing after the attempt runs`() =
    runTest {
      var reported = false
      var carriedOn = false
      val job =
        launch(start = CoroutineStart.UNDISPATCHED) {
          attempt<Unit>(onFailure = { reported = true }) { awaitCancellation() }
          carriedOn = true
        }

      job.cancelAndJoin()

      job.isCancelled.shouldBeTrue()
      reported.shouldBeFalse()
      carriedOn.shouldBeFalse()
    }
}