# Simple URL Sanitizer

Sometimes you just want to share a link with a friend without being canceled by association. Freedom of assembly, or something... 

Anyway, this app gives you a simple way to remove query parameters from any URL. Either launch the app and paste a URL into the text box or share a URL from another app via the Android Sharesheet. 

Uncheck the boxes to remove any tracking parameters from the URL. Have to figure out which ones those are by yourself, though. This app is *simple*, remember? (But at least it will save your selections for next time.)

Got a shortened link that hides where it actually goes? Tap the cloud icon and the app will follow the redirect first, then sanitize wherever it lands.

Requires Android 7.0 or later.

It is available via the [Google Play Store](https://play.google.com/store/apps/details?id=com.banasiak.android.simpleshare).

## License

This repository holds two works under two licenses.

- **The ClearURLs rules catalog is not covered by the Unlicense.** [`share/src/main/assets/clearurls/data.min.json`](share/src/main/assets/clearurls/data.min.json) is the work of the [ClearURLs](https://github.com/ClearURLs/Rules) project, copied unmodified, and is licensed under the [GNU Lesser General Public License, version 3](share/src/main/assets/clearurls/LICENSE). The [NOTICE](share/src/main/assets/clearurls/NOTICE) beside it names the upstream commit it came from, and all three files ship inside the APK.
- **Everything else** is released under the [Unlicense](LICENSE).
