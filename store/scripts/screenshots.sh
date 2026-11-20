#!/usr/bin/env bash
# Retakes the Play Store screenshots on a phone connected over adb, five per language, framed the way
# Android Studio frames them, into store/screenshots/<language>/1.png to 5.png. store/README.md says
# what it needs and what it changes on the phone while it runs. Everything it changes is put back
# when it exits, including when it fails.
#
#   store/scripts/screenshots.sh                         English, the one language with shots
#   LANGS="de-DE fr-FR" store/scripts/screenshots.sh     these languages instead
#   OUT=/tmp/shots store/scripts/screenshots.sh          somewhere other than store/screenshots
#   FORCE=1 store/scripts/screenshots.sh                 keep a language even if its status bars differ
#
# ANDROID_SERIAL picks the phone when more than one is connected. ANDROID_STUDIO is the Android Studio
# install the frame is taken from (default /opt/android/studio), and DEVICE_FRAME is the device art
# inside it (default pixel_9a), whose screen size the phone has to match.
set -euo pipefail

here=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$here/../.." && pwd)
out=${OUT:-$root/store/screenshots}
studio=${ANDROID_STUDIO:-/opt/android/studio}
frame=${DEVICE_FRAME:-pixel_9a}
pkg=com.banasiak.android.simpleshare.screenshots
main=$pkg/com.banasiak.android.simpleshare.main.MainActivity
sheet=$pkg/com.banasiak.android.simpleshare.sanitize.SanitizeActivity
statusbar=150

# the links the shots show. The YouTube and Amazon ones are made up, down to the share tag, so the
# shots carry no real person's tracking ID. The short link is one of the app's own demo links
# (Constants.DEMO_URL_LIST), which Feeling Lucky picks from
youtube='https://www.youtube.com/watch?v=Xk3vT9pLm2A&t=42&si=Q7xZ2kLp9VbN4mRt&feature=shared'
youtube_clean='^https://www\.youtube\.com/watch\?v=Xk3vT9pLm2A&t=42$'
tag=cm_sw_r_apan_dp_7KQ2M9XWD4RT5HJB3N8C
amazon="https://www.amazon.com/dp/B01DCG0GPC?ref=$tag&ref_=$tag&social_share=$tag&starsLeft=1"
amazon_clean='^https://www\.amazon\.com/dp/B01DCG0GPC$'
short='a\.co/d/0ZV7xf0'

die() { echo "screenshots.sh: $*" >&2; exit 1; }
say() { echo "== $*"; }
ui() { python3 -I "$here/ui.py" "$@"; }
sh_adb() { adb shell "$@" | tr -d '\r'; }

# --- what this needs --------------------------------------------------------------------------

command -v adb >/dev/null || die "adb is not on the PATH"
command -v python3 >/dev/null || die "python3 is not on the PATH"
if command -v magick >/dev/null; then im=(magick); cmp=(magick compare); idf=(magick identify)
elif command -v convert >/dev/null; then im=(convert); cmp=(compare); idf=(identify)
else die "ImageMagick is not installed"; fi
"${im[@]}" -list format 2>/dev/null | grep -qE '^ *WEBP' || die "this ImageMagick cannot read WebP"
art=$(find "$studio" -type d -path "*/device-art-resources/$frame" -print -quit 2>/dev/null)
[ -n "$art" ] || die "no $frame device art under $studio: set ANDROID_STUDIO or DEVICE_FRAME"
adb get-state >/dev/null 2>&1 || die "no phone connected, or more than one: set ANDROID_SERIAL"

langs=${LANGS:-en-US}
for lang in $langs; do python3 -I "$here/labels.py" "$lang" >/dev/null || die "no labels for $lang"; done

# the frame: where the screen sits on it, how big the screen is, and the two images that make it
read -r sx sy < <(awk '$1 == "part2" {p = 1} p && $1 == "x" {x = $2} p && $1 == "y" {print x, $2; exit}' "$art/layout") || true
read -r sw sh < <(awk '$1 == "display" {d = 1} d && $1 == "width" {w = $2} d && $1 == "height" {print w, $2; exit}' "$art/layout") || true
back=$art/$(awk '$1 == "image" {print $2; exit}' "$art/layout")
mask=$art/$(awk '$1 == "mask" {print $2; exit}' "$art/layout")
[ -f "$back" ] && [ -f "$mask" ] && [ -n "${sx:-}" ] && [ -n "${sw:-}" ] || die "could not read $art/layout"

size=$(sh_adb wm size | awk '/Override size/ {o = $3} /Physical size/ {p = $3} END {print (o ? o : p)}')
[ "$size" = "${sw}x${sh}" ] || die "the phone's screen is $size, and the $frame frame needs ${sw}x${sh}"
case $(sh_adb settings get system font_scale) in
  1.0 | null) ;;
  *) echo "warning: the phone's font scale is not 1.0, so the text will not match earlier shots" >&2 ;;
esac

# the lock screen can be dismissed from the shell only while the phone is unlocked behind it, as it
# is for a while after the user last unlocked it. A phone that was dozing ignored a dismissal sent
# as it woke, so it is sent until the lock screen is gone
unlock() {
  adb shell input keyevent KEYCODE_WAKEUP
  for _ in 1 2 3 4 5; do
    sh_adb dumpsys window | grep -q 'isKeyguardShowing=true' || return 0
    sh_adb dumpsys trust | grep '(current)' | grep -q 'deviceLocked=0' || die "the phone is locked: unlock it and run this again"
    adb shell wm dismiss-keyguard
    sleep 1
  done
  die "the lock screen would not go away"
}
unlock

# --- put the phone back on the way out, however this ends --------------------------------------

work=$(mktemp -d)
night=$(sh_adb cmd uimode night | awk '{print $3}')
demo_allowed=$(sh_adb settings get global sysui_demo_allowed)
demo_on=$(sh_adb settings get global sysui_tuner_demo_on)
stay_on=$(sh_adb settings get global stay_on_while_plugged_in)

restore_setting() { # name, then the value read before: "null" means it was never set
  if [ "$2" = "null" ]; then adb shell settings delete global "$1" >/dev/null
  else adb shell settings put global "$1" "$2"; fi
}

restore() {
  set +e
  say "putting the phone back"
  adb shell am broadcast -a com.android.systemui.demo -e command exit >/dev/null 2>&1
  adb shell cmd statusbar send-disable-flag none >/dev/null 2>&1
  restore_setting sysui_tuner_demo_on "$demo_on"
  restore_setting sysui_demo_allowed "$demo_allowed"
  restore_setting stay_on_while_plugged_in "$stay_on"
  case $night in yes | no | auto) adb shell cmd uimode night "$night" >/dev/null ;; esac
  adb uninstall "$pkg" >/dev/null 2>&1
  rm -rf "$work"
}
trap restore EXIT

# --- build the side copy ------------------------------------------------------------------------

say "building the side copy"
(cd "$root" && ./gradlew -q -I "$here/side-copy.init.gradle" :share:assembleDebug)
apk=$root/build/screenshots/share/outputs/apk/debug/share-debug.apk

# --- set the phone up ---------------------------------------------------------------------------

say "setting the phone up"
# the screen stays on while the phone has power of any kind, so it cannot lock part-way through
adb shell svc power stayon true
adb shell cmd uimode night yes >/dev/null
# demo mode, as Developer options' "System UI demo mode" sets it: a fixed clock, full signal and a
# full battery, with the notification icons hidden
adb shell settings put global sysui_demo_allowed 1
adb shell settings put global sysui_tuner_demo_on 1
demo() { adb shell am broadcast -a com.android.systemui.demo -e command "$@" >/dev/null; }
# sent again before every shot, in case a real battery update replaces the demo battery
demo_state() {
  demo clock -e hhmm 0500
  demo network -e wifi show -e level 4 -e fully true
  demo network -e mobile show -e datatype lte -e level 4 -e fully true
  demo battery -e level 100 -e plugged false
  demo notifications -e visible false
}
demo enter
demo_state
# demo mode left the "Wireless debugging connected" icon showing on a Pixel 9a running Android 17,
# and the status bar's own flag for hiding notification icons removes it
adb shell cmd statusbar send-disable-flag notification-icons
adb uninstall "$pkg" >/dev/null 2>&1 || true

# --- the screens --------------------------------------------------------------------------------

# Play Protect can hold an install of the copy until the user answers a prompt on the phone, so the
# copy is installed once a run, and a stalled install says where to look
install() {
  timeout -k 5 120 adb install -r "$1" >/dev/null 2>&1 ||
    die "could not install $1. If the phone shows a Play Protect prompt, answer it and run this again"
}
install "$apk"

# the launcher screen, started afresh with its text field empty
launch() {
  adb shell am force-stop "$pkg"
  adb shell am start -W -n "$main" >/dev/null
}

# a link shared in from another app, the way the share sheet sends it
share() { adb shell "am start -W -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT '$1' -n $sheet" >/dev/null; }

# waits for a screen, with the notification shade closed first: an open shade covers the app, and
# would otherwise end up in the shot
shown() { adb shell cmd statusbar collapse; ui wait "$@"; }

capture() {
  demo_state
  sleep 1 # the sheet is still sliding up when its text is first on screen
  adb exec-out screencap -p > "$work/raw-$1.png"
  [ "$("${idf[@]}" -format %wx%h "$work/raw-$1.png")" = "${sw}x${sh}" ] || die "shot $1 came out the wrong size"
}

# the status bar of a framed shot, which is what gets compared: a notification that arrives part-way
# through puts its icon on some shots and not others
bar() { "${im[@]}" "$1" -crop "${sw}x${statusbar}+${sx}+${sy}" +repage "$work/bar-$2.png"; }
same() { "${cmp[@]}" -metric AE "$work/bar-$1.png" "$work/bar-$2.png" null: 2>/dev/null; }

# Every language is compared with one reference: a set already in $out that this run is not
# retaking, English first, so a rerun of a few languages has to match the rest, or else the first
# language this run takes. The sheet dims the status bar, so shots of it have a reference of their own
ref=""
for candidate in en-US $(ls "$out" 2>/dev/null); do
  case " $langs " in *" $candidate "*) continue ;; esac
  if [ -f "$out/$candidate/1.png" ] && [ -f "$out/$candidate/5.png" ]; then
    ref=$candidate
    bar "$out/$ref/1.png" ref-sheet
    bar "$out/$ref/5.png" ref-screen
    break
  fi
done

done_langs=()
for lang in $langs; do
  say "taking $lang"
  declare -A L=()
  while IFS=$'\t' read -r name pattern; do L[$name]=$pattern; done < <(python3 -I "$here/labels.py" "$lang")

  # the copy's data cleared for every language, so no choice saved by an earlier one changes a box
  adb shell pm clear "$pkg" >/dev/null || die "could not clear the side copy's data"
  adb shell cmd locale set-app-locales "$pkg" --locales "$lang" >/dev/null || die "could not set the side copy's language to $lang"
  unlock

  # 1 and 2: links shared in from another app, over the launcher screen. The sanitized URL is waited
  # for in full, which it is only once the rules have unchecked the trackers
  launch; shown "${L[lucky]}"; share "$youtube"; shown "${L[parameters]}" "$youtube_clean"; capture 1
  launch; shown "${L[lucky]}"; share "$amazon"; shown "${L[parameters]}" "$amazon_clean"; capture 2
  # 5: the launcher screen, empty
  launch; shown "${L[lucky]}"; capture 5
  # 4: Feeling Lucky picks a demo link at random, so it is tapped until it picks the short one
  for _ in $(seq 40); do
    ui tap "${L[lucky]}"
    ui find "$short" | grep -q . && break
  done
  shown "$short"; capture 4
  # 3: that short link in the sheet, before the cloud icon expands it
  ui tap "${L[remove]}"; shown "${L[sanitized]}" "^https://$short\$" "${L[decode_hint]}"; capture 3

  for n in 1 2 3 4 5; do
    "${im[@]}" "$back" "$work/raw-$n.png" -geometry "+$sx+$sy" -composite "$mask" -geometry "+$sx+$sy" -composite \
      -background black -alpha background -strip "$work/framed-$n.png"
  done
  if [ -z "$ref" ]; then
    ref=$lang
    bar "$work/framed-1.png" ref-sheet
    bar "$work/framed-5.png" ref-screen
  fi
  differ=()
  for n in 1 2 3; do bar "$work/framed-$n.png" "$n"; same ref-sheet "$n" || differ+=("$n"); done
  for n in 4 5; do bar "$work/framed-$n.png" "$n"; same ref-screen "$n" || differ+=("$n"); done
  if [ ${#differ[@]} -gt 0 ] && [ -z "${FORCE:-}" ]; then
    die "$lang: shots ${differ[*]} have a different status bar from $ref's, so $lang was not written. Done: ${done_langs[*]:-none}. Clear whatever posted the notification and rerun the rest with LANGS"
  fi

  mkdir -p "$out/$lang"
  for n in 1 2 3 4 5; do cp "$work/framed-$n.png" "$out/$lang/$n.png"; done
  done_langs+=("$lang")
  echo "   wrote $out/$lang"
done
