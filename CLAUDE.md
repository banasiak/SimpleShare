# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build and test

| command | what it does |
| --- | --- |
| `./gradlew check` | ktlint, Android lint and the unit tests -- the gate to run before calling anything done |
| `./gradlew format` | ktlint auto-fix. Must be idempotent: a second run has to produce no diff |
| `./gradlew testDebugUnitTest --tests "*SanitizeViewModelTests*"` | a single test class |
| `./gradlew koverHtmlReportDebug` | unit test coverage, with generated code and composables left out |
| `./gradlew assembleRelease` | R8-minified release APK, output unsigned |

Run `format` and `check` as two commands, `format` first: nothing orders them inside one invocation.

Lint is expected to report **zero** findings; treat a new one as a regression rather than noise. It
also reports every dependency that has a newer release, so a clean run needs the catalog current.

The Gradle version and `distributionSha256Sum` in `gradle/wrapper/gradle-wrapper.properties` are
bumped together, with the sum taken from the `.sha256` file Gradle publishes beside the
distribution. Run `./gradlew wrapper --gradle-version <version>` twice, because the first run
rewrites only the properties, then check `gradle-wrapper.jar` against Gradle's published
`gradle-<version>-wrapper.jar.sha256`. Until 9.9.1 the jar had never been regenerated and was still
the one from the 6.6 to 7.0.2 line.

`assembleRelease` produces `share-release-unsigned.apk`. To run it on a device, sign it with the
debug keystore -- that matches the debug build's signature, so it installs over an existing debug
install with `-r` and keeps the DataStore contents, which is what makes it useful for testing
migrations and R8 behavior:

```
apksigner sign --ks ~/.android/debug.keystore --ks-pass pass:android \
  --ks-key-alias androiddebugkey --key-pass pass:android --out signed.apk aligned.apk
```

## Architecture

There are two entry points:

- `main.MainActivity` -- the launcher screen, a Compose-only screen with no ViewModel, where the user
  pastes text and starts an `ACTION_SEND` intent aimed at the other activity.
- `sanitize.SanitizeActivity` -- the share target. It is transparent and draws a `ModalBottomSheet`
  over whichever app invoked the share sheet, which is why it handles its own window insets rather
  than letting the framework fit system windows.

`sanitize/SanitizeState.kt` holds the whole contract for that screen: `SanitizeState` (parcelable,
the single source of truth), `SanitizeAction` (input) and `SanitizeEffect` (one-shot output).
Reading that file first explains most of the rest.

`SanitizeViewModel` is the only place state changes:

- The private `state` property is restored from `SavedStateHandle` at construction, and its setter
  is the only place state is published. The setter also saves to `SavedStateHandle`, because nothing
  tells a ViewModel that its process is about to be killed.
- `stateFlow` is seeded from `state`. The initializer assigns the restored value without running the
  setter, so a flow created with a default of its own would leave the UI empty after process death.
- `loading` is `@IgnoredOnParcel`. A lookup does not survive the process, and a restored `true`
  would leave the progress bar running with the button disabled.
- `effectFlow` is backed by a **`Channel`, not a `SharedFlow`**. Effects are one-shot and the
  Activity subscribes asynchronously; a shared flow silently drops anything emitted before the
  collector attaches.
- The user's choices are written out when a box is toggled, and at no other time. A link whose boxes
  are never touched writes nothing.
- The saved preferences and the rules are looked up concurrently. Each lookup returns on the main
  thread, which is busy drawing the sheet's first frame, so one after the other costs a second wait.

`data/Repository` owns everything durable: per-host preferences in Preferences DataStore, the launch
counter, and short-URL resolution over OkHttp. `fetchRedirectUrl` returns a `RedirectResult` rather
than a nullable URL so callers can tell "this host has no redirect" from "we never reached it" --
the UI keeps the retry action available only for the second.

`data/TrackerRules` answers one question: which query parameter names on a link does the ClearURLs
catalog mark as tracking. The catalog is upstream's `data.min.json`, bundled unmodified in
`share/src/main/assets/clearurls/` and read on first use. It is matched the way the ClearURLs
add-on matches it (`removeFieldsFormURL` in the add-on's `clearurls.js`), all case-insensitively:

- A provider applies when its `urlPattern` is found anywhere in the link and none of its
  `exceptions` is.
- Each entry in `rules` and `referralMarketing` is wrapped in `^...$` and tested against a decoded
  parameter name.
- Nothing reads `rawRules`, `redirections`, `completeProvider` or `forceRedirection`.

## Decisions worth not re-litigating

- **Query parameters are a `List<QueryParam>`, not a map.** Repeated names are legitimate;
  `application/x-www-form-urlencoded` produces them for multi-valued form controls. A map cannot
  represent `?a=1&a=1` at all. Read the query positionally (`querySize` / `queryParameterName(i)`),
  because `queryParameterNames` is a `Set` and `queryParameter(name)` returns only the first match.
- **A box starts checked unless something says otherwise.** `buildParameterList` asks, in order:
  did the user keep this name on this host, did they remove it, do the rules flag it. Boxes started
  unchecked through release EIGHT, which stripped the `v` from a video link until the user checked
  it.
- **Only a choice the user made is stored, and it is stored per parameter *name*.** Values change
  from link to link while names recur, so a value-keyed preference would rarely match again.
  - A box left as the rules had it stores nothing, so a later catalog that starts or stops flagging
    a name still reaches that host.
  - A box left against the rules is remembered until the user reverses it.
  - Two same-named parameters collapse into one answer, kept if either is, which errs toward keeping
    more.
  - Saving merges rather than replaces: a link updates only the names it carries, so one that lacks
    a parameter (or has no query at all) cannot erase a choice made on an earlier link.
- **The DataStore schema is load-bearing for upgrades.** Preferences written by older installs must
  keep reading; changing key types or entry meaning orphans real user data.
  - A host's kept names are under the bare host, the only key that releases through EIGHT wrote.
  - Its removed names are under `<host>#removed`. A host cannot contain `#`, so the two never
    collide.
  - A host with nothing to remember has no entry. The file is backed up, and it should not become a
    list of every host a link was ever shared from.
  - Releases through EIGHT did write an entry for every such host, an empty set. `EmptySetMigration`
    drops those on the first start after an upgrade. An empty set reads the same as no entry, so it
    loses no choice.
- **The rules are bundled, never fetched.** The only network request the app makes is the one behind
  the cloud icon. The catalog is also unsigned, and its published hash comes from the same server,
  so the hash catches corruption but not tampering. A bundled copy changes only through a diff
  someone read. To refresh it:
  1. Pick a commit of `github.com/ClearURLs/Rules`.
  2. Download `data.min.json` and `LICENSE` from
     `raw.githubusercontent.com/ClearURLs/Rules/<commit>/` into `share/src/main/assets/clearurls/`.
  3. Put the commit in the `NOTICE` beside them, and read the diff.
  4. On a device, look for "Skipping rules" in logcat (see the next entry).
- **A pattern the platform will not compile is skipped, and a provider that loses its `urlPattern`
  or an exception is dropped whole.** The catalog is written for JavaScript's regex engine.
  `TrackerRulesTests` compiles every bundled pattern, but on the JVM's engine, and Android's is a
  different one (ICU). Dropping only the exception would flag parameters on the links it protects.
  The catalog at commit `326be26` was run on a Pixel 9a with Android 17 on 2026-11-18: every pattern
  compiled, and 24 sample links got the same verdicts as on the JVM.
- **A link's length is not capped before matching.** On that phone, in a test harness, every URL
  pattern and exception against a hostile link of a million characters took 0.6 to 3.2 seconds and
  threw nothing, and time grew in step with length. The app does that work off the main thread. The
  JVM's engine does overflow its stack on a host of thousands of labels, which is a reason not to
  read the unit tests as a measure of this.
- **The catalog is LGPL-3.0 and the app is under the Unlicense**, which is why the catalog ships
  unmodified, with its license and a notice beside it in the APK. A converted or trimmed copy would
  be a modified work with more to state. The README's License section tells readers that the root
  `LICENSE` does not cover the catalog, and it stays for as long as the catalog is bundled.
- Rule patterns compile on first use, one provider at a time. The share sheet usually starts in a
  new process, and a link matches a provider or two out of a couple of hundred.
- **The rules were measured not to slow the sheet.** On the same phone and day, as R8 release
  builds, the time from the intent to the first complete state was a median of 258 ms with the rules
  against 262 ms without when interpreted, and 132 ms against 115 ms when compiled ahead of time,
  with the runs of each pair overlapping. Reading and parsing the catalog and compiling the URL
  patterns took 7 to 13 ms of that. Before the two lookups ran concurrently the compiled figure was
  178 ms against 133 ms.
- **Framework seams exist so the ViewModel is JVM-testable.** `ClipboardHelper` wraps `ClipData` and
  `PersistableBundle`, which are android.jar stubs that throw off-device. Inlining it back would force
  `unitTests.isReturnDefaultValues` on again. `RuleSource` is the same kind of seam over the APK's
  assets.
- **Dispatchers are injected, never named where they are used.** `AppModule` provides
  `@DefaultDispatcher` and `@IoDispatcher`, and a test passes
  `UnconfinedTestDispatcher(testScheduler)` for either.
- **The sanitized query is written decoded, so it reads as text.** `sanitizeUrl` joins the kept
  parameters by hand, because `HttpUrl.Builder` would escape every value again. What stays escaped,
  and why, is at `readableQueryComponent`; the round-trip test in `SanitizeViewModelTests` fails if
  the output ever parses back to different parameters.
- **`toHttpsUrlOrNull` upgrades every URL to https** before anything else touches it.
- `.editorconfig` is the house style's copy, unchanged, from the `android-house-style` skill. It
  disables the ktlint rules that reflow working code rather than catch a defect, so do not re-enable
  them to "tidy up". It starts with `root = true`, because a Claude Code worktree sits inside the
  main checkout, under `.claude/worktrees/`, and without that line ktlint there also reads the main
  checkout's `.editorconfig`, whatever version of it that checkout is on. Until it went in, an older
  copy there kept `function-expression-body` and `when-entry-bracing` off in every worktree, and the
  16 block bodies and unbraced `when` branches they report in a checkout outside the main one went
  unseen.
- `no-unused-imports` is enabled by name in `.editorconfig`, which is the only way it runs: ktlint
  1.7.0 stopped running it by default. It is deprecated and goes in ktlint 2.0.0.
- `.editorconfig` sets `insert_final_newline = false`, and ktlint holds Kotlin sources to it, so
  they end without a trailing newline. The markdown files in the repo do end with one.
- Prose is U.S. English everywhere: comments, test names, these notes, the README and commit
  messages. So "behavior", "initializer" and "canceled". An API's own name, such as `isCancelled`,
  keeps the spelling the API gives it.
- R8 is on for release. Every rule in `share/proguard-rules.pro` notes the failure it prevents,
  because R8 breakage is silent -- verify reflection-driven paths (Hilt, `@Parcelize` through process
  death, DataStore) on a device rather than trusting a clean build.

## Where this app departs from the house style

Each of these was looked at on 2026-11-18 and left as it is, for the reason given.

- **The launcher screen has no ViewModel or State/Action/Effect triad.** Its one piece of state is
  the text field. Hoisted through a `StateFlow`, a text field's value arrives a frame after the
  keystroke, which is the documented way to get dropped characters and a jumping cursor. Converting
  it needs a state-based text field and a typing test on a device. Until then `rememberSaveable`
  holds the text, and the demo link is picked with an uninjected `Random`.
- **`effectFlow` is a `Channel`.** The house style's `MutableSharedFlow` drops an effect raised
  before the Activity's collector attaches, and a test here guards against exactly that.
- **The app still depends on Material Components, for the XML theme's parent.** The platform theme
  the house style uses instead needs API 29, and `minSdk` is 24.
- **Effects are collected by the Activity, not the `*Screen`.** Every effect here finishes the
  Activity or starts another one, so the Activity is what owns navigation.

## Tests

JUnit 5 with mockk, kluent and Turbine, mirroring the sibling `CoinFlip2` repo. `MainDispatcherRule`
is a JUnit 5 extension, not a JUnit 4 rule. Test names are `given / when / then` sentences in
backticks and class names end in `Tests`.

The ViewModel is constructed directly rather than through Hilt, `Repository` is mocked, and
`RepositoryTests` drives real HTTP against MockWebServer. `HttpUrlParceler` has no unit test because
a real `Parcel` needs Robolectric or instrumentation, and for the same reason neither does `loading`
being left out of the parcel.

Both were checked by hand instead, on a Pixel 9a running Android 17 on 2026-11-19, in a debug and
an R8 release build. Killed six seconds into a lookup, the sheet came back with its link and boxes
and no progress bar, and the cloud button started a new lookup. A build without `@IgnoredOnParcel`
came back with the bar running and a button that did nothing. To repeat it, share a link to
`192.0.2.1`, which never answers, tap the cloud icon, press Home, `am kill` the package, and return
with `am task focus <task id>`.

`TrackerRules` is mocked in the ViewModel tests, where it flags `utm_source` and nothing else.
`TrackerRulesTests` mostly feeds it small catalogs written inline, and reads the bundled one
straight from `src/main/assets`, which works because unit tests run from the module directory.

When a test guards against a specific bug, prove it fails against the bug before trusting it. Restore
mutated files from a copy, never `git checkout HEAD --`, which silently discards uncommitted work.
