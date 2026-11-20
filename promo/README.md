# Promo video

The Google Play promo video, rendered rather than edited. Its phone screens are redrawn from the
app's own layouts, colors, strings and icons, and its soundtrack is synthesized for it, so a change
to the app reaches the video at its next render.

```bash
cd promo
npm ci --ignore-scripts
node render.mjs                     # out/sizzle.mp4, 1920x1080 at 60 fps, 40.8 s
node render.mjs --jobs 2            # the same with 2 workers (default: one per core)
node render.mjs --still 2.5,12.8    # single frames, as out/still-<t>.png
node render.mjs --screens           # the redrawn phone screens alone, as out/screen-<n>.png
node render.mjs --audio             # the soundtrack alone, and its buses, as out/stem-*.wav
node render.mjs --thumbnail         # the YouTube thumbnail, 1280x720, as out/thumbnail.jpg
```

Nothing under `out/` is committed.

## What it needs

- **Node and Playwright.** Playwright is the one package, pinned in `package-lock.json` with its
  hash. It drives Chromium 141, which it keeps in `~/.cache/ms-playwright`. If that browser isn't
  there, `npx playwright install chromium` downloads it from Playwright's servers over TLS. That
  download isn't checked against a pinned hash.
- **ffmpeg**, built with `libx264`, from `$FFMPEG` or else the `PATH`.
- **Android Studio**, under `/opt/android/studio` or wherever `ANDROID_STUDIO` points. It's the
  source of everything the page draws with:
  - the Pixel 9a frame from its device art, the same frame the store screenshots use
  - Inter for the captions and JetBrains Mono for the links, from its bundled JDK's fonts
  - Roboto and Noto Color Emoji, from its layout preview's copy of Android's fonts. These are the
    fonts the phone itself uses. The Roboto in npm's `@fontsource/roboto` package set text about
    2% wider than the phone did.

`render.mjs` serves the page on 127.0.0.1 at a random port, and answers only for a fixed list of
files. The page loads nothing from the network, and Chromium runs with its sandbox on, which
Playwright leaves off unless asked.

## How it's made

- `sizzle.js` draws every frame as a pure function of time, so any frame renders on its own. That's
  what lets `render.mjs` share the frames among workers and feed one encoder in order.
- The timeline sits on the soundtrack's grid: 100 beats a minute, so a beat is 0.6 s and a bar
  2.4 s. Each section starts on a bar, and every tap, toggle and pop lands on a beat or a sixteenth.
  The section starts are near the top of the timeline in `sizzle.js` (`BRAND_AT`, `YT_AT` and on),
  and the times inside a section are offsets from its start.
- The sections:
  1. The headline arrives looking like every other, then two beats in, "infested" and "trackers"
     go off. Mold spreads through them from a few spots, and leaves them slime green, twitching and
     dripping. Under it, a shared YouTube link types itself out, its two trackers light up, and a
     sweep washes them off.
  2. The name and the logo.
  3. That link shared to the app: `si` and `feature` come up unchecked, and `t` is unchecked and
     checked again.
  4. An Amazon link with four trackers, wiped down to 36 characters from 181.
  5. The app's demo short link, from "I'm Feeling Lucky" through the cloud to the expanded link.
     Its four boxes come up checked, and are unchecked one at a time until the clean link is left.
     The phone eases back while the long link would make the sheet too tall for the frame.
  6. Five features.
  7. The end card.
- **The short-link section departs from the app on purpose.** The app would bring the expanded
  link's trackers up unchecked, as the YouTube and Amazon sections show. The owner chose on
  2026-11-20 to show the boxes checked and unchecked by hand, so the demonstration can be followed.
- `screens.js` redraws the launcher screen and the share sheet. Every position comes from the
  layouts' dp values, at the Pixel 9a's 2.625 pixels to the dp, rounded as Compose rounds them. The
  exceptions are the system's insets and the status bar, which were measured on the screenshots.
- The URL field's bouncy resize uses the spring the app gives `animateContentSize`, and the rows
  grow from their bottom end corner as `expandIn` grows them. The sheet's slide and the
  indeterminate progress bar are close approximations, not values taken from Material's sources.
- The status bar is SystemUI's, in Google Sans, which isn't to hand. So it's drawn in Roboto,
  narrowed to Google Sans's widths.

### Checking the screens

`--screens` writes the five store screenshots' screens as the redraw draws them, at the phone's own
1080×2424. Hold them against the real ones, cropped out of their frames:

```bash
node render.mjs --screens
for n in 1 2 3 4 5; do
  convert ../store/screenshots/en-US/$n.png -crop 1080x2424+69+73 +repage -alpha off out/real-$n.png
  compare -metric AE -fuzz 10% out/real-$n.png out/screen-$n.png out/diff-$n.png; echo " pixels differ in $n"
done
```

On 2026-11-20, below the status bar, 0.7% to 3.1% of each screen's pixels differed by more than
24 levels. Nearly all of those were at text edges:
- Vertically, every text style sat within a pixel of the phone's.
- Horizontally, long lines drifted by up to 6 pixels by their ends, which is about 3 pixels at the
  size the phone appears in the video.
- The links wrapped at the same places as on the phone.

## What reaches into it from the app

Nothing fails when any of these drift. The video quietly changes, or stops matching the app, so
check them after a change to the app:

- `render.mjs` reads the dark scheme's `md_theme_dark_*` colors from `ui/theme/Color.kt` with a
  regular expression, and stops if one it needs is missing.
- It reads ten strings from `values/strings.xml` by key, the vector paths of `drawable/sanitize.xml`
  and `drawable/cloud_download.xml`, and the Feeling Lucky entry holding `a.co/d/0ZV7xf0` from
  `Constants.DEMO_URL_LIST`. It stops if any of those is missing.
- It counts the languages, for "14 languages", as the `values` directories holding a
  `strings.xml`.
- `screens.js` copies the layouts' dp values from `MainScreen.kt` and `SanitizeScreen.kt`. Its two
  surface colors are Material's baseline dark values, which show through because the app's scheme
  doesn't set them. A change to either layout means changing `screens.js`, then running the check
  above.
- `sizzle.js` lists which parameters the ClearURLs rules flag on each link. That's what the bundled
  catalog flagged on 2026-11-20, and what `store/scripts/screenshots.sh` waits for on the phone.

## The links

The YouTube and Amazon links are made up, share tags included, as in the store screenshots. The
short link is the app's own demo link. On 2026-11-20 it redirected to the same Amazon product and
four parameters as the made-up Amazon link, but with a real share tag. The video expands it into
the made-up link, so that tag never appears.

## The copy

Every line the video writes is in sentence case with no closing period. That covers the headlines,
the lines under them, the feature labels and the end card. Each line stands alone on screen, so a
period adds nothing.
- Names keep their own capitals: Simple URL Sanitizer, ClearURLs, YouTube.
- Where a line lists several things, middle dots separate them, as in "Free · No ads · No tracking ·
  Open source".
- The phone's screens are the exception. Their Title Case ("Query Parameters", "Remove Tracking
  Parameters") is the app's own text, which the redraw copies.

## The claims

The captions, the features and the end card ("Free · No ads · No tracking · Open source") say what
the store listing says, and were checked against the build on 2026-11-20:
- No dependency is an ad or analytics SDK.
- The merged release manifest asks for `INTERNET` and nothing else of the system. Its one other
  entry is androidx's app-private `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. The request behind
  the cloud icon is the only one the app makes.
- The Play in-app review prompt goes through the Play Store app, not through the app's own internet
  access.

Check them again when the app's dependencies or permissions change.

## The soundtrack

`audio.mjs` arranges it and `synth.mjs` holds the voices. It was written for this video: nothing is
sampled, and nothing is taken from another project's soundtrack.
- **The groove** is in E dorian, vamping between Em9 and A13 under the demos, stepping up through
  Cmaj9 and D6/9 under the features, and landing on Em9 for the end card.
- **The voices** are an FM electric piano, glass bells at an inharmonic FM ratio, a sub bass, a
  kick, finger snaps and shakers.
- **The sound effects** are water and soap, for an app whose icon is a soap dispenser: drops, bubble
  pops and a squeegee. The rotting headline gets the opposite: a wet squelch, a cluster of notes a
  semitone apart, and a heartbeat until the sweep.
- **The room** is a feedback delay network of eight lines mixed through a Hadamard matrix. There's
  also a ping-pong echo.

`render.mjs` sets the loudness to -14 LUFS, the level YouTube is widely reported to turn louder
uploads down to. It takes two passes of ffmpeg's `loudnorm`: the first measures the track and the
second applies a single gain, where a single pass would ride the level.

## Checking a render

```bash
ffprobe out/sizzle.mp4                                              # 1920x1080, 60 fps, yuv420p bt709, AAC 48 kHz stereo
ffmpeg -i out/sizzle.mp4 -map 0:a -af ebur128=peak=true -f null -   # I = -14 LUFS, true peak under -1.5 dBFS
mkdir -p out/review && ffmpeg -i out/sizzle.mp4 -vf "fps=4,scale=480:270,tile=6x6" out/review/sheet-%02d.png
```

The contact sheets are the quickest way to review a cut. Every caption should settle before the
next arrives, and every tap should land on what it presses. If a render looks wrong, render it with
`--jobs 1` to tell a parallelism bug from a drawing one.

## Publishing

Google Play takes the video as a YouTube link, not a file. Upload `out/sizzle.mp4` to YouTube as a
public or unlisted video, with ads off and no age restriction, and `out/thumbnail.jpg` as its
thumbnail. Then paste its link into the Video field of the app's main store listing in Play
Console.

The thumbnail is the opening headline at a moment of the rot, set large beside the share sheet with
the YouTube link's trackers outlined. It's a JPEG because YouTube takes a thumbnail of up to 2 MB,
and a PNG of these gradients can pass that.
