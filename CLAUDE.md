# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build and test

| command | what it does |
| --- | --- |
| `./gradlew check` | ktlint, Android lint and the unit tests -- the gate to run before calling anything done |
| `./gradlew format` | ktlint auto-fix. Must be idempotent: a second run has to produce no diff |
| `./gradlew testDebugUnitTest --tests "*SanitizeViewModelTests*"` | a single test class |
| `./gradlew assembleRelease` | R8-minified release APK, output unsigned |

Lint is expected to report **zero** findings; treat a new one as a regression rather than noise.

`assembleRelease` produces `share-release-unsigned.apk`. To run it on a device, sign it with the
debug keystore -- that matches the debug build's signature, so it installs over an existing debug
install with `-r` and keeps the DataStore contents, which is what makes it useful for testing
migrations and R8 behaviour:

```
apksigner sign --ks ~/.android/debug.keystore --ks-pass pass:android \
  --ks-key-alias androiddebugkey --key-pass pass:android --out signed.apk aligned.apk
```

## Architecture

Two entry points share one ViewModel-driven loop:

- `main.MainActivity` -- the launcher screen, a Compose-only screen with no ViewModel, where the user
  pastes text and starts an `ACTION_SEND` intent aimed at the other activity.
- `sanitize.SanitizeActivity` -- the share target. It is transparent and draws a `ModalBottomSheet`
  over whichever app invoked the share sheet, which is why it handles its own window insets rather
  than letting the framework fit system windows.

`sanitize/Sanitize.kt` holds the whole contract for that screen: `SanitizeState` (parcelable, the
single source of truth), `SanitizeAction` (input) and `SanitizeEffect` (one-shot output). Reading
that file first explains most of the rest.

`SanitizeViewModel` is the only place state changes:

- `stateFlow` is a `StateFlow` for rendering. The private `state` property reads and writes through
  it, and is seeded from `SavedStateHandle` at construction -- a property initialiser would bypass
  the setter and leave the UI on a default after process death.
- `effectFlow` is backed by a **`Channel`, not a `SharedFlow`**. Effects are one-shot and the
  Activity subscribes asynchronously; a shared flow silently drops anything emitted before the
  collector attaches.
- It implements `LifecycleEventObserver`. On `ON_PAUSE` it writes to `SavedStateHandle`
  synchronously and persists enabled parameters in the background.

`data/Repository` owns everything durable: per-host preferences in Preferences DataStore, the launch
counter, and short-URL resolution over OkHttp. `fetchRedirectUrl` returns a `RedirectResult` rather
than a nullable URL so callers can tell "this host has no redirect" from "we never reached it" --
the UI keeps the retry action available only for the second.

## Decisions worth not re-litigating

- **Query parameters are a `List<QueryParam>`, not a map.** Repeated names are legitimate;
  `application/x-www-form-urlencoded` produces them for multi-valued form controls. A map cannot
  represent `?a=1&a=1` at all. Read the query positionally (`querySize` / `queryParameterName(i)`),
  because `queryParameterNames` is a `Set` and `queryParameter(name)` returns only the first match.
- **Preferences are stored per parameter *name*, deliberately.** Values change from link to link
  while names recur, so a value-keyed preference would rarely match again. The consequence -- keeping
  one of two same-named parameters re-enables both next time -- errs toward keeping more. Saving
  merges rather than replaces: a link updates only the names it carries, so one that lacks a
  parameter (or has no query at all) cannot erase a choice made on an earlier link.
- **The DataStore schema is load-bearing for upgrades.** Preferences written by older installs must
  keep reading; changing key types or entry meaning orphans real user data.
- **Framework seams exist so the ViewModel is JVM-testable.** `ClipboardHelper` wraps `ClipData` and
  `PersistableBundle`, which are android.jar stubs that throw off-device. Inlining it back would force
  `unitTests.isReturnDefaultValues` on again.
- **`toHttpsUrlOrNull` upgrades every URL to https** before anything else touches it.
- Five ktlint rules are disabled in `.editorconfig` because promoting them would reformat otherwise
  untouched files. Do not re-enable them to "tidy up".
- `.editorconfig` sets `insert_final_newline = false`, and ktlint holds Kotlin sources to it, so
  they end without a trailing newline. The markdown files in the repo do end with one.
- R8 is on for release. Every rule in `share/proguard-rules.pro` notes the failure it prevents,
  because R8 breakage is silent -- verify reflection-driven paths (Hilt, `@Parcelize` through process
  death, DataStore) on a device rather than trusting a clean build.

## Tests

JUnit 5 with mockk, kluent and Turbine, mirroring the sibling `CoinFlip2` repo. `MainDispatcherRule`
is a JUnit 5 extension, not a JUnit 4 rule. Test names are `given / when / then` sentences in
backticks and class names end in `Tests`.

The ViewModel is constructed directly rather than through Hilt, `Repository` is mocked, and
`RepositoryTests` drives real HTTP against MockWebServer. `HttpUrlParceler` is untested because a
real `Parcel` needs Robolectric or instrumentation.

When a test guards against a specific bug, prove it fails against the bug before trusting it. Restore
mutated files from a copy, never `git checkout HEAD --`, which silently discards uncommitted work.
