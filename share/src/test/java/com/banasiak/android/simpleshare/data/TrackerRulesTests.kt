package com.banasiak.android.simpleshare.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.amshove.kluent.shouldBeEmpty
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeGreaterThan
import org.junit.jupiter.api.Test
import java.io.File
import java.io.IOException
import java.util.regex.PatternSyntaxException

@OptIn(ExperimentalCoroutinesApi::class)
class TrackerRulesTests {
  private fun TestScope.rules(source: RuleSource) = TrackerRules(source, UnconfinedTestDispatcher(testScheduler))

  private fun TestScope.rules(catalog: String) = rules { catalog.byteInputStream() }

  // unit tests run from the module directory, so this is the same file the app packages
  private val bundled = File("src/main/assets/clearurls/data.min.json")

  private suspend fun TrackerRules.flagged(url: String) = flaggedNames(url.toHttpUrl())

  // region matching

  @Test
  fun `given a provider for the link's host, when a rule names a parameter, then only that parameter is flagged`() =
    runTest {
      val rules = rules("""{"providers":{"video":{"urlPattern":"^https?:\\/\\/(?:[a-z0-9-]+\\.)*?video\\.example","rules":["si"]}}}""")

      rules.flagged("https://www.video.example/watch?v=1&si=2") shouldBeEqualTo setOf("si")
    }

  @Test
  fun `given a provider for another host, when the link carries its parameter, then nothing is flagged`() =
    runTest {
      // si is a tracker on one site and can be the whole point of a link on another
      val rules = rules("""{"providers":{"video":{"urlPattern":"^https?:\\/\\/(?:[a-z0-9-]+\\.)*?video\\.example","rules":["si"]}}}""")

      rules.flagged("https://www.banasiak.com/watch?v=1&si=2").shouldBeEmpty()
    }

  @Test
  fun `given a rule, when a longer name merely contains it, then the longer name is left alone`() =
    runTest {
      // the add-on anchors every rule to the whole name; unanchored, ref would also take prefer and referrer
      val rules = rules("""{"providers":{"all":{"urlPattern":".*","rules":["ref"]}}}""")

      rules.flagged("https://www.banasiak.com/p?ref=a&prefer=b&referrer=c") shouldBeEqualTo setOf("ref")
    }

  @Test
  fun `given a rule written as a pattern, when names match it, then each of them is flagged`() =
    runTest {
      val rules = rules("""{"providers":{"all":{"urlPattern":".*","rules":["utm(?:_[a-z_]*)?"]}}}""")

      rules.flagged("https://www.banasiak.com/p?utm=a&utm_source=b&utm_medium=c&utmost=d") shouldBeEqualTo
        setOf("utm", "utm_source", "utm_medium")
    }

  @Test
  fun `given a name in another case, when it matches a rule, then it is flagged as the link spells it`() =
    runTest {
      // preferences and the checkbox list are keyed on the link's own spelling
      val rules = rules("""{"providers":{"all":{"urlPattern":".*","rules":["utm_source"]}}}""")

      rules.flagged("https://www.banasiak.com/p?UTM_Source=a") shouldBeEqualTo setOf("UTM_Source")
    }

  @Test
  fun `given a host in another case, when it matches a provider, then the provider still applies`() =
    runTest {
      val rules = rules("""{"providers":{"video":{"urlPattern":"^https?:\\/\\/VIDEO\\.example","rules":["si"]}}}""")

      rules.flagged("https://video.example/watch?si=2") shouldBeEqualTo setOf("si")
    }

  @Test
  fun `given a referral marketing rule, when the link carries that parameter, then it is flagged too`() =
    runTest {
      val rules = rules("""{"providers":{"shop":{"urlPattern":".*","rules":["qid"],"referralMarketing":["tag"]}}}""")

      rules.flagged("https://www.banasiak.com/p?qid=1&tag=me-20&k=soap") shouldBeEqualTo setOf("qid", "tag")
    }

  @Test
  fun `given a link one of the provider's exceptions matches, when it is checked, then that provider flags nothing`() =
    runTest {
      // exceptions mark pages a rule would break, such as a sign-in flow that needs every parameter
      val catalog =
        """{"providers":{"video":{"urlPattern":"^https?:\\/\\/video\\.example","rules":["si"],"exceptions":["^https?:\\/\\/video\\.example\\/signin"]}}}"""
      val rules = rules(catalog)

      rules.flagged("https://video.example/signin?si=2").shouldBeEmpty()
      rules.flagged("https://video.example/watch?si=2") shouldBeEqualTo setOf("si")
    }

  @Test
  fun `given several providers match, when one is excepted, then the others still apply`() =
    runTest {
      val catalog =
        """{"providers":{"all":{"urlPattern":".*","rules":["utm_source"],"exceptions":["video\\.example"]},"video":{"urlPattern":"video\\.example","rules":["si"]}}}"""

      rules(catalog).flagged("https://video.example/watch?si=2&utm_source=a") shouldBeEqualTo setOf("si")
    }

  @Test
  fun `given a link with no query, when it is checked, then nothing is flagged`() =
    runTest {
      rules("""{"providers":{"all":{"urlPattern":".*","rules":["si"]}}}""").flagged("https://www.banasiak.com/p").shouldBeEmpty()
    }

  // endregion

  // region a catalog that is not what was expected

  @Test
  fun `given a rule that will not compile, when the catalog loads, then the provider's other rules still apply`() =
    runTest {
      // the catalog is written for JavaScript's engine, so one pattern this platform rejects must not
      // take the rest down with it
      val rules = rules("""{"providers":{"all":{"urlPattern":".*","rules":["(","si"]}}}""")

      rules.flagged("https://www.banasiak.com/p?si=2&v=1") shouldBeEqualTo setOf("si")
    }

  @Test
  fun `given an exception that will not compile, when the catalog loads, then that provider is dropped`() =
    runTest {
      // skipping only the exception would flag parameters on exactly the links it was there to protect
      val catalog =
        """{"providers":{"video":{"urlPattern":".*","rules":["si"],"exceptions":["("]},"all":{"urlPattern":".*","rules":["utm_source"]}}}"""

      rules(catalog).flagged("https://www.banasiak.com/p?si=2&utm_source=a") shouldBeEqualTo setOf("utm_source")
    }

  @Test
  fun `given a url pattern that will not compile, when the catalog loads, then that provider is dropped`() =
    runTest {
      val catalog = """{"providers":{"video":{"urlPattern":"(","rules":["si"]},"all":{"urlPattern":".*","rules":["utm_source"]}}}"""

      rules(catalog).flagged("https://www.banasiak.com/p?si=2&utm_source=a") shouldBeEqualTo setOf("utm_source")
    }

  @Test
  fun `given entries of the wrong shape, when the catalog loads, then the well-formed ones still apply`() =
    runTest {
      val catalog =
        """{"providers":{"none":{"rules":["v"]},"text":"oops","nulls":{"urlPattern":null,"rules":["v"]},""" +
          """"all":{"urlPattern":".*","rules":[7,null,["v"],"si"],"exceptions":{"a":1},"completeProvider":false}}}"""

      // the link says "null" and "7" itself, so a null or a number read as text would match something
      rules(catalog).flagged("https://www.banasiak.com/null?si=2&v=1&null=3&7=4") shouldBeEqualTo setOf("si")
    }

  @Test
  fun `given a catalog that is not json, when a link is checked, then nothing is flagged`() =
    runTest {
      rules("<html>not the catalog</html>").flagged("https://www.banasiak.com/p?utm_source=a").shouldBeEmpty()
    }

  @Test
  fun `given a catalog with no providers, when a link is checked, then nothing is flagged`() =
    runTest {
      rules("""["providers"]""").flagged("https://www.banasiak.com/p?utm_source=a").shouldBeEmpty()
    }

  @Test
  fun `given a catalog that cannot be opened, when a link is checked, then nothing is flagged`() =
    runTest {
      val rules = rules(RuleSource { throw IOException("no such asset") })

      rules.flagged("https://www.banasiak.com/p?utm_source=a").shouldBeEmpty()
    }

  // endregion

  // region the bundled catalog

  @Test
  fun `given the bundled catalog, when every pattern the app uses is compiled, then none is rejected`() {
    // TrackerRules skips a pattern it cannot compile, so a refresh that brought one in would otherwise
    // pass silently. This runs on the JVM's engine; Android's is a different one, so a device still
    // has the last word.
    val providers = Json.parseToJsonElement(bundled.readText()).jsonObject.getValue("providers").jsonObject
    providers.size shouldBeGreaterThan 100

    val rejected =
      providers.flatMap { (name, entry) ->
        val provider = entry.jsonObject
        val patterns =
          listOf(provider.getValue("urlPattern").jsonPrimitive.content) +
            provider.strings("exceptions") +
            (provider.strings("rules") + provider.strings("referralMarketing")).map { "^$it$" }
        patterns.filter { !compiles(it) }.map { "$name: $it" }
      }

    rejected.shouldBeEmpty()
  }

  @Test
  fun `given the bundled catalog, when a video link is checked, then its share token is flagged and its video is not`() =
    runTest {
      rules { bundled.inputStream() }.flagged("https://www.youtube.com/watch?v=dQw4w9WgXcQ&si=AbCdEf&t=42") shouldBeEqualTo setOf("si")
    }

  @Test
  fun `given the bundled catalog, when a link to a host it has never heard of is checked, then the common trackers are flagged`() =
    runTest {
      rules { bundled.inputStream() }.flagged("https://www.banasiak.com/p?utm_source=a&id=1&fbclid=b&page=2") shouldBeEqualTo
        setOf("utm_source", "fbclid")
    }

  // endregion

  private fun JsonObject.strings(key: String) = (this[key] as? JsonArray).orEmpty().map { it.jsonPrimitive.content }

  private fun compiles(pattern: String) =
    try {
      Regex(pattern, RegexOption.IGNORE_CASE)
      true
    } catch (e: PatternSyntaxException) {
      false
    }
}