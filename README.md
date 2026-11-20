# Simple URL Sanitizer

Sometimes you just want to share a link with a friend without being canceled by association. Freedom of assembly, or something... 

Anyway, this app gives you a simple way to remove query parameters from any URL. Either launch the app and paste a URL into the text box or share a URL from another app via the Android Sharesheet. 

The tracking parameters it recognizes start out unchecked, courtesy of the rules from the [ClearURLs](https://github.com/ClearURLs/Rules) project. Everything else stays checked, so the link still goes where it was going. If it guesses wrong, check or uncheck the box yourself, and it will remember your choice for that site next time.

Got a shortened link that hides where it actually goes? Tap the cloud icon and the app will follow the redirect first, then sanitize wherever it lands. That tap is the only time the app makes a network request of its own: the rules ship inside it and are never downloaded.

Requires Android 7.0 or later.

It is available via the [Google Play Store](https://play.google.com/store/apps/details?id=com.banasiak.android.simpleshare). The listing itself lives in [`store/`](store/README.md): its description, the release notes in every language, the feature graphic, and the screenshots, which a script retakes from a phone.

## Building

It is a single Gradle module, `share`, written in Kotlin with Jetpack Compose, Hilt and DataStore. The versions of everything it is built with are in [`gradle/libs.versions.toml`](gradle/libs.versions.toml). You need a JDK and the Android SDK; the Gradle wrapper fetches the rest, and checks the Gradle download against a pinned checksum.

```
./gradlew assembleDebug    # a debug APK, in share/build/outputs/apk/debug
./gradlew check            # ktlint, Android lint and the unit tests
```

## The promo video

[`promo/`](promo/) renders the Google Play promo video, outside the Gradle build, with Node, Playwright and ffmpeg. Its phone screens are redrawn from the app's own layouts, colors and strings, and its soundtrack is synthesized for it. [`promo/README.md`](promo/README.md) says how to render it and what it needs.

## License

This repository holds two works under two licenses.

- **The ClearURLs rules catalog is not covered by the Unlicense.** [`share/src/main/assets/clearurls/data.min.json`](share/src/main/assets/clearurls/data.min.json) is the work of the [ClearURLs](https://github.com/ClearURLs/Rules) project, copied unmodified, and is licensed under the [GNU Lesser General Public License, version 3](share/src/main/assets/clearurls/LICENSE). The [NOTICE](share/src/main/assets/clearurls/NOTICE) beside it names the upstream commit it came from, and all three files ship inside the APK.
- **Everything else** is released under the [Unlicense](LICENSE).
