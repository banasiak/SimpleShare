package com.banasiak.android.simpleshare.data

import com.banasiak.android.simpleshare.DefaultDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl
import timber.log.Timber
import java.io.IOException
import java.io.InputStream
import java.util.regex.PatternSyntaxException
import javax.inject.Inject
import javax.inject.Singleton

fun interface RuleSource {
  fun open(): InputStream
}

@Singleton
class TrackerRules @Inject constructor(
  private val source: RuleSource,
  @DefaultDispatcher private val dispatcher: CoroutineDispatcher
) {
  private val providers: List<Provider> by lazy { load() }

  /**
   * The query parameter names on [url] that the ClearURLs catalog marks as tracking, spelled as the
   * URL spells them. Empty when the catalog could not be read.
   */
  suspend fun flaggedNames(url: HttpUrl): Set<String> =
    withContext(dispatcher) {
      val names = (0 until url.querySize).map { url.queryParameterName(it) }.toSet()
      if (names.isEmpty()) return@withContext emptySet()

      val address = url.toString()
      val applicable = providers.filter { it.appliesTo(address) }
      names.filterTo(mutableSetOf()) { name -> applicable.any { it.flags(name) } }
    }

  private fun load(): List<Provider> {
    val catalog =
      try {
        source.open().use { Json.parseToJsonElement(it.reader().readText()) }
      } catch (e: IOException) {
        Timber.e(e, "Unable to read the rules catalog")
        return emptyList()
      } catch (e: SerializationException) {
        Timber.e(e, "Unable to parse the rules catalog")
        return emptyList()
      }

    val entries = ((catalog as? JsonObject)?.get("providers") as? JsonObject).orEmpty()
    return entries.mapNotNull { (name, entry) -> (entry as? JsonObject)?.toProvider(name) }
  }

  private fun JsonObject.toProvider(name: String): Provider? {
    val urlPattern = this["urlPattern"].stringOrNull()?.let { compile(it) }
    val exceptions = strings("exceptions").map { compile(it) }
    // an exception that will not compile cannot be honored, so the provider goes rather than flag
    // parameters on links the catalog meant to leave alone
    if (urlPattern == null || exceptions.any { it == null }) {
      Timber.w("Skipping rules provider: $name")
      return null
    }
    return Provider(urlPattern, exceptions.filterNotNull(), strings("rules") + strings("referralMarketing"))
  }

  private fun JsonObject.strings(key: String): List<String> = (this[key] as? JsonArray).orEmpty().mapNotNull { it.stringOrNull() }

  private fun JsonElement?.stringOrNull(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content
}

// matched the way the ClearURLs add-on matches: case-insensitively, the URL patterns anywhere in the
// link and each rule against a whole parameter name
private class Provider(
  private val urlPattern: Regex,
  private val exceptions: List<Regex>,
  rules: List<String>
) {
  private val rules: List<Regex> by lazy { rules.mapNotNull { compile("^$it$") } }

  fun appliesTo(address: String): Boolean = urlPattern.containsMatchIn(address) && exceptions.none { it.containsMatchIn(address) }

  fun flags(name: String): Boolean = rules.any { it.containsMatchIn(name) }
}

// the catalog is written for JavaScript, so a pattern this platform rejects is skipped, not fatal
private fun compile(pattern: String): Regex? =
  try {
    Regex(pattern, RegexOption.IGNORE_CASE)
  } catch (e: PatternSyntaxException) {
    Timber.w(e, "Skipping rules pattern: $pattern")
    null
  }