# Play Store assets

What the Google Play listing for this app is made of, kept beside the code it describes. Play
Console is still where it is published: nothing here uploads anything, and anything changed here
reaches the store only when it is pasted or uploaded there.

| File | What it is | Where it goes in Play Console |
|---|---|---|
| `store_listing.xml` | App name, short description and full description | The main store listing |
| `release_notes.xml` | What's new in the release being prepared, in every language | The release's notes, all languages in one paste |
| `screenshots/en-US/1.png`–`5.png` | Five framed phone screenshots, in English | The main store listing's phone screenshots |
| `feature_graphic.png` | The 1024×500 banner, rendered from `feature_graphic.svg` | The main store listing's feature graphic |
| `ic_launcher-playstore.png` | The 512×512 app icon | The main store listing's app icon |

## The listing text

Both XML files hold one block per language, tagged with **Play Console's language code**, not
Android's resource qualifier. Most codes are language and region, but Thai is plain `th`, and Latin
American Spanish is `es-419`. A block Play does not recognize is dropped without a warning:
CoinFlip2's version 77 tagged its Thai release notes `th-TH`, and Thai users were shown the English
notes instead.

Both files cover every language the app is translated into.

Long text sits flush against the left margin between indented tags, so that copying it gives
exactly what Play should show. Keep `&`, `<` and `>` out of the text: XML would need them escaped,
and the escape is what would get pasted.

Before pasting anything, check both files:

```bash
python3 store/scripts/check.py
```

It confirms they parse, that they cover the same languages, that every block is tagged with a code
Play uses, that both have English, and that every field is inside Play's limit: 30 characters for
the app name, 80 for the short description, 4000 for the full description and 500 for each
language's release notes. It prints every field's length against its limit, and exits non-zero on
any problem.

- **The app name here is the store's**, "URL Cleaner: Sanitize Links" in English and translated in
  the other languages. The launcher label under the icon is the app's `app_name` resource, "Sanitize
  URL", and the share sheet's title is "Simple URL Sanitizer".
- **Release notes are pasted as one block.** Copy the language blocks, without the `<?xml …?>` line
  or the `<release_notes>` tags around them, into the release's notes field. Play sorts the text
  into languages by the tags.
- **Write English first, then the translations.** Use the app's own words for anything that is on
  screen, from its `strings.xml` in each `values-*` directory, including the app's name. Chinese
  and Japanese use full-width punctuation.
- **Parameter names stay in English, in single quotes,** in every language: `'utm_source'`, `'si'`.
  Each language's description quotes the same ones as the English.
- **Most translations are tighter than the English, not word for word.** The English full
  description is close to Play's 4000-character limit, and a literal German, French, Spanish,
  Italian or Turkish translation runs over it. Those drop repeated words, not claims.

## Screenshots

```bash
store/scripts/screenshots.sh
```

This retakes all five screenshots in English and writes them to `screenshots/en-US/`, framed the way
Android Studio frames a Pixel 9a. A run takes under two minutes. Leave the phone alone while it
runs.

The listing has screenshots in English only, though its text is translated. That was a choice made
in November 2026, and the script can take the other languages when wanted.

| Shot | Screen |
|---|---|
| 1 | A YouTube link shared in: `v` and `t` kept, and `si` and `feature` unchecked by the rules |
| 2 | An Amazon link shared in, with all four of its tracking parameters unchecked by the rules |
| 3 | The demo short link in the sheet, before the cloud icon expands it |
| 4 | The launcher screen with that demo link pasted in |
| 5 | The launcher screen, empty |

The YouTube and Amazon links are made up, share tags included, so the shots carry no real person's
tracking ID. The short link is one of the app's own demo links.

### What it needs

- A phone connected over adb whose screen is 1080×2424, the Pixel 9a's, at the default font size.
  Set `ANDROID_SERIAL` when more than one device is connected.
- The phone unlocked, or locked only by the lock screen of a phone that was unlocked recently. The
  script dismisses that, and stops if the phone is actually locked.
- Android Studio, for its Pixel 9a device frame. It is looked for under `/opt/android/studio`, or
  wherever `ANDROID_STUDIO` points.
- ImageMagick built with WebP support, and Python 3.

`LANGS="de-DE fr-FR"` takes those languages instead, finding its way around by the app's own
translations. `OUT=some/dir` writes somewhere other than `screenshots/`.

### What it does to the phone

It never touches an installed copy of the app. It builds its own,
`com.banasiak.android.simpleshare.screenshots`, into `build/screenshots`, installs it beside the
real one, and uninstalls it at the end. It installs the copy once a run, and clears its data
between languages. That copy draws the app's own colors: dynamic color is off in it, so the shots
do not take their colors from the phone's wallpaper.

While it runs, it:

- switches the phone to dark mode
- turns on demo mode, which fixes the clock at 5:00 and draws full signal and a full battery
- hides the notification icons, since demo mode leaves "Wireless debugging connected" showing on
  Android 17
- keeps the screen on while the phone has power
- sets the copy's language through Android's per-app language setting, and finds its way around the
  app by labels it reads from the app's own translations

When it exits, including on a failure, all of these go back to what they were.

### When a run goes wrong

- **The status bar changed part-way through.** Every shot's status bar has to match a reference
  set: one already in `screenshots/` that the run is not retaking, English first, or else the
  first language the run takes. The run stops at the first language that does not match, keeping
  the languages already finished. A notification is the usual cause. Clear it and rerun the
  remaining languages with `LANGS`, which are then checked against the ones already done. `FORCE=1`
  keeps a language regardless.
- **A dump of the screen was killed.** Android allows one UI automation connection at a time, so
  running `uiautomator`, or anything that uses it, during a run kills the run's own dump. The run
  retries a few times, but leave the phone to it.
- **The battery is drawn green and charging, with a shield.** On a Pixel 9a running Android 17 on
  its charger, the phone drew its charging state over demo mode's battery at 80% charge, though not
  at 60%. Every status bar then failed the comparison. Unplug the charger and run over wireless
  debugging. Either way is fine, as long as a whole set is taken the same way.
- **The install stalls, and the run stops after two minutes.** Play Protect can hold an install of
  the copy until someone answers a prompt on the phone. Answer it there, and run the script again.
- **The status bar still shows a full battery afterwards.** SystemUI keeps the demo battery until
  the next real battery change. Unplugging or plugging the cable in refreshes it.

### Play's rules on screenshots

Play Console Help's page on preview assets (answer 9866151, read on 2026-11-20) sets these rules
for phone screenshots:

- **Required:** JPEG or 24-bit PNG with no alpha channel, each side between 320 and 3840 pixels,
  and the long side no more than 2.3 times the short side. Two to eight per language.
- **Needed for promotion:** an app is eligible for Play's recommendations and other promoted
  placements only with at least four screenshots in 9:16 portrait, at 1080×1920 or larger.
- **Recommended:** no device imagery, since it dates quickly. A clean status bar, with no
  notifications and full battery and signal, which demo mode gives.

These framed screenshots are 1224×2570, a ratio of 2.1, on a transparent background. So they meet
the size and ratio rules, depart from the no-alpha rule, and are neither 9:16 nor free of device
imagery. Play has accepted CoinFlip2's, framed the same way, and the frames were kept deliberately
in November 2026. If an upload is ever rejected, or promotion becomes the goal, flatten the frames
onto a solid background and pad them to 9:16, or drop the frame and pad the bare screen.

The same page recommends separate screenshots for each language when they contain text, as these
do. The listing keeps English ones only, as the Screenshots section above says.

The screenshots are stored in Git LFS (see `.gitattributes`), so a clone needs `git-lfs` installed
to get the images rather than pointer files.

## The feature graphic

`feature_graphic.svg` is the source, and `feature_graphic.png` is what gets uploaded. Edit the SVG,
then render it:

```bash
store/scripts/feature_graphic.sh
```

It needs `rsvg-convert` and ImageMagick, and Android Studio for the font it sets the text in, Inter.
It stops if fontconfig cannot find Inter's regular or semibold weight, rather than drawing the text
in a fallback font. librsvg can't use a `woff2` web font, even one fontconfig lists, so Roboto from
the cloud repository's web fonts drew as a fallback. The PNG it writes is 1024×500 with no alpha
channel, as Play requires.

The phone in the graphic is `screenshots/en-US/1.png`, framed as it is, so render the graphic again
after retaking the screenshots. A clone without `git-lfs` has only a pointer file there, and the
script stops rather than draw an empty phone.

The look follows zero-nextgen's feature graphic: near-black with a Very Peri glow, which is
`#6667AB`, the launcher icon's background, a faint grid, and the name in a white-to-lavender
gradient over a line of copy. The phone on the right shows the share sheet's checklist, cropped so
it fills the height. Play Console Help's page on preview assets recommends against two things the
graphic does on purpose:

- **Text that isn't translated.** The name and copy are English in every language.
- **Device imagery,** which the page says dates quickly. The phone is the Pixel 9a frame the
  screenshots use.

It follows the page's other advice: it doesn't repeat the icon, its main content stays clear of
the edges, and its background isn't plain black, white or gray, which blend into Play's own.

## The icon

`ic_launcher-playstore.png` comes from Android Studio's Image Asset wizard. That wizard also writes
a new copy into `share/src/main/` every time it regenerates the launcher icons. Move that copy here,
replacing this one.
