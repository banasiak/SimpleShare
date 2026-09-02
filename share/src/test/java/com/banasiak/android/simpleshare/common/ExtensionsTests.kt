package com.banasiak.android.simpleshare.common

import androidx.lifecycle.SavedStateHandle
import com.banasiak.android.simpleshare.sanitize.SanitizeState
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeNull
import org.junit.jupiter.api.Test

class ExtensionsTests {
  private val handle: SavedStateHandle = mockk(relaxed = true)

  @Test
  fun `state is written under the key every screen shares`() {
    // the literal is spelled out here on purpose: changing it silently orphans state saved by an
    // installed build across a process death
    val state = SanitizeState(sanitizedUrl = "https://www.banasiak.com/")

    handle.save(state)

    verify(exactly = 1) { handle.set("state", state) }
  }

  @Test
  fun `state is read back from the same key`() {
    val state = SanitizeState(sanitizedUrl = "https://www.banasiak.com/")
    every { handle.get<SanitizeState>("state") } returns state

    handle.restore<SanitizeState>() shouldBeEqualTo state
  }

  @Test
  fun `an empty handle restores nothing`() {
    every { handle.get<SanitizeState>("state") } returns null

    handle.restore<SanitizeState>().shouldBeNull()
  }

  @Test
  fun `an http url is upgraded to https`() {
    // the whole point of the extension: a sanitized link should never downgrade the sender's transport
    "http://www.banasiak.com/p?a=1".toHttpsUrlOrNull().toString() shouldBeEqualTo "https://www.banasiak.com/p?a=1"
  }

  @Test
  fun `an https url is left alone`() {
    "https://www.banasiak.com/p".toHttpsUrlOrNull().toString() shouldBeEqualTo "https://www.banasiak.com/p"
  }

  @Test
  fun `a non-default port survives the upgrade`() {
    "http://www.banasiak.com:8080/p".toHttpsUrlOrNull().toString() shouldBeEqualTo "https://www.banasiak.com:8080/p"
  }

  @Test
  fun `something that is not a url at all yields nothing`() {
    "there is no link here".toHttpsUrlOrNull().shouldBeNull()
  }
}