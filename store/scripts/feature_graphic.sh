#!/usr/bin/env bash
# Renders store/feature_graphic.svg to store/feature_graphic.png: 1024x500 and with no alpha channel,
# as Play asks. The text is set in Inter, from the fonts Android Studio bundles, which fontconfig is
# pointed at for the one run. librsvg cannot use a woff2 web font, even one fontconfig lists, and
# falls back to another font without a word.
#
#   store/scripts/feature_graphic.sh
#
# ANDROID_STUDIO is the Android Studio install the font is taken from (default /opt/android/studio).
set -euo pipefail

here=$(cd "$(dirname "$0")" && pwd)
store=$(cd "$here/.." && pwd)
fonts=${ANDROID_STUDIO:-/opt/android/studio}/jbr/lib/fonts

die() { echo "feature_graphic.sh: $*" >&2; exit 1; }

command -v rsvg-convert >/dev/null || die "rsvg-convert is not installed"
command -v convert >/dev/null || die "ImageMagick is not installed"
for style in Regular SemiBold; do
  [ -f "$fonts/Inter-$style.otf" ] || die "no Inter-$style.otf in $fonts: set ANDROID_STUDIO"
done
# the phone shows the first screenshot, which a clone without git-lfs has only as a pointer file
[ "$(head -c 8 "$store/screenshots/en-US/1.png" | od -An -c | tr -d ' ')" = '211PNG\r\n032\n' ] ||
  die "store/screenshots/en-US/1.png is not a PNG: is git-lfs installed?"

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
cat > "$work/fonts.conf" <<XML
<?xml version="1.0"?>
<!DOCTYPE fontconfig SYSTEM "urn:fontconfig:fonts.dtd">
<fontconfig>
  <include ignore_missing="yes">/etc/fonts/fonts.conf</include>
  <dir>$fonts</dir>
  <cachedir>$work/cache</cachedir>
</fontconfig>
XML

# a weight fontconfig cannot find is drawn in another font without a word, so check first. The SVG's
# font-weight 400 and 600 are fontconfig's regular and semibold, which Inter names Regular and
# Semi Bold
for weight in regular:Regular "semibold:Semi Bold"; do
  FONTCONFIG_FILE=$work/fonts.conf fc-match "Inter:weight=${weight%%:*}" style | grep -q "${weight#*:}" ||
    die "fontconfig cannot find Inter ${weight#*:}"
done

FONTCONFIG_FILE=$work/fonts.conf rsvg-convert -w 1024 -h 500 -o "$work/graphic.png" "$store/feature_graphic.svg"
convert "$work/graphic.png" -background '#0E0E1C' -alpha remove -alpha off -strip "PNG24:$store/feature_graphic.png"
echo "wrote $store/feature_graphic.png"
