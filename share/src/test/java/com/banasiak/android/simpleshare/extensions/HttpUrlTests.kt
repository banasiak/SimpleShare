package com.banasiak.android.simpleshare.extensions

import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeNull
import org.junit.jupiter.api.Test

class HttpUrlTests {
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