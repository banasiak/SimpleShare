// Simple URL Sanitizer -- Google Play promo video, drawn one frame at a time.
//
// render.mjs loads this page, calls setup() once and renderFrame(t) for every frame. Nothing here
// animates on its own: every frame is a pure function of t, so a frame can be rendered alone.
'use strict';

const W = 1920;
const H = 1080;
const FPS = 60;
// 100 bpm: every section starts on a bar of the soundtrack, and every tap lands on its grid
const BEAT = 0.6;
const BAR = 4 * BEAT;

const stage = document.getElementById('stage');
const ctx = stage.getContext('2d');

const INK = '#F4F2FF';
const SUB = '#D4D8DE';
const BRAND = '#6667AB';
const NAME = 'Simple URL Sanitizer';
const TAGLINE = 'Scrub tracking links clean before sharing';

// ---------------------------------------------------------------------------------------------
// the links. The YouTube and Amazon ones are made up, share tags included, as in the store
// screenshots. The short link is the app's own demo link: it expands to the Amazon one's product
// and parameters, with a real share tag in place of the made-up one

const YOUTUBE = 'https://www.youtube.com/watch?v=Xk3vT9pLm2A&t=42&si=Q7xZ2kLp9VbN4mRt&feature=shared';
const TAG = 'cm_sw_r_apan_dp_7KQ2M9XWD4RT5HJB3N8C';
const AMAZON = `https://www.amazon.com/dp/B01DCG0GPC?ref=${TAG}&ref_=${TAG}&social_share=${TAG}&starsLeft=1`;
const SHORT = 'https://a.co/d/0ZV7xf0';

// what the bundled ClearURLs rules flag on each, which store/scripts/screenshots.sh checks on the
// phone: the rows start checked or not by these
const YT_ROWS = [
  { name: 'v', value: 'Xk3vT9pLm2A', enabled: true },
  { name: 't', value: '42', enabled: true },
  { name: 'si', value: 'Q7xZ2kLp9VbN4mRt', enabled: false },
  { name: 'feature', value: 'shared', enabled: false },
];
const AMAZON_ROWS = [
  { name: 'ref', value: TAG, enabled: false },
  { name: 'ref_', value: TAG, enabled: false },
  { name: 'social_share', value: TAG, enabled: false },
  { name: 'starsLeft', value: '1', enabled: false },
];

// the sanitized link, as SanitizeViewModel writes it: the kept parameters, in their order
function sanitized(link, rows) {
  const base = link.split('?')[0];
  const kept = rows.filter((r) => (r.on === undefined ? r.enabled : r.on >= 0.5)).map((r) => `${r.name}=${r.value}`);
  return kept.length ? `${base}?${kept.join('&')}` : base;
}

// ---------------------------------------------------------------------------------------------
// math

const clamp = (x, a = 0, b = 1) => Math.min(b, Math.max(a, x));
const seg = (t, a, b) => clamp((t - a) / (b - a));
const lerp = (a, b, p) => a + (b - a) * p;
const easeOut = (p) => 1 - (1 - p) ** 3;
const easeIn = (p) => p ** 3;
const easeInOut = (p) => (p < 0.5 ? 4 * p ** 3 : 1 - (-2 * p + 2) ** 3 / 2);
const overshoot = (p) => 1 + 2.4 * (p - 1) ** 3 + 1.4 * (p - 1) ** 2;

function random(seed) {
  let s = seed >>> 0 || 1;
  return () => {
    s ^= s << 13;
    s >>>= 0;
    s ^= s >>> 17;
    s ^= s << 5;
    s >>>= 0;
    return s / 4294967296;
  };
}

// ---------------------------------------------------------------------------------------------
// assets

let APP = null;
let C = null;
const IMG = {};
let LOGO = null;
const screenCanvas = canvas(SCREEN_W, SCREEN_H);
const sctx = screenCanvas.getContext('2d');

function canvas(w, h) {
  const c = document.createElement('canvas');
  c.width = Math.ceil(w);
  c.height = Math.ceil(h);
  return c;
}

function loadImage(src) {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () => resolve(img);
    img.onerror = () => reject(new Error(`failed to load ${src}`));
    img.src = src;
  });
}

async function setup() {
  APP = await (await fetch('/app.json')).json();
  C = APP.colors;
  SCREENS.init(APP);
  LOGO = APP.icons.sanitize.map((p) => ({ path: new Path2D(p.d), alpha: p.alpha }));
  IMG.back = await loadImage('/art/back.webp');
  IMG.mask = await loadImage('/art/mask.webp');
  for (const f of ['400 40px Roboto', '500 40px Roboto', '400 40px Inter', '600 40px Inter', '500 40px "JetBrains Mono"']) {
    if (!(await document.fonts.load(f, 'Ag')).length) throw new Error(`${f} did not load`);
  }
  // resolves empty rather than failing when nothing declares the family, and the hint's finger
  // would then draw as an empty box
  if (!(await document.fonts.load('40px "Noto Color Emoji"', '☝')).length) throw new Error('Noto Color Emoji did not load');
  IMG.grid = makeGrid();
  IMG.glow = makeGlow();
  return { fps: FPS, duration: DURATION, cues: CUES, marks: MARKS, screens: Object.keys(SHOTS) };
}

function urlLines(url) {
  return SCREENS.wrap(sctx, url, SCREENS.TYPE.bodyLarge, SCREEN_W - 2 * (px(8) + px(4)) - px(16) - px(48)).length;
}

// ---------------------------------------------------------------------------------------------
// the backdrop: the feature graphic's, near-black under a Very Peri glow and a faint grid

function makeGrid() {
  const c = canvas(W + 128, H + 128);
  const g = c.getContext('2d');
  g.strokeStyle = 'rgba(255, 255, 255, 0.05)';
  g.lineWidth = 2;
  g.beginPath();
  for (let x = 0; x <= c.width; x += 64) {
    g.moveTo(x, 0);
    g.lineTo(x, c.height);
  }
  for (let y = 0; y <= c.height; y += 64) {
    g.moveTo(0, y);
    g.lineTo(c.width, y);
  }
  g.stroke();
  g.globalCompositeOperation = 'destination-in';
  const fade = g.createRadialGradient(c.width * 0.3, c.height * 0.5, 0, c.width * 0.3, c.height * 0.5, c.width * 0.62);
  fade.addColorStop(0.2, '#FFFFFF');
  fade.addColorStop(1, 'rgba(255, 255, 255, 0)');
  g.fillStyle = fade;
  g.fillRect(0, 0, c.width, c.height);
  return c;
}

function glow(x, y, r, color, alpha) {
  const g = ctx.createRadialGradient(x, y, 0, x, y, r);
  g.addColorStop(0, color);
  g.addColorStop(1, 'rgba(0, 0, 0, 0)');
  ctx.save();
  ctx.globalAlpha *= alpha;
  ctx.fillStyle = g;
  ctx.fillRect(x - r, y - r, 2 * r, 2 * r);
  ctx.restore();
}

function backdrop(t) {
  const base = ctx.createLinearGradient(0, 0, W * 0.35, H);
  base.addColorStop(0, '#17183A');
  base.addColorStop(0.55, '#0E0E1C');
  base.addColorStop(1, '#0A0A12');
  ctx.fillStyle = base;
  ctx.fillRect(0, 0, W, H);
  glow(W * 0.84 + 50 * Math.sin(t * 0.21), -90 + 40 * Math.cos(t * 0.17), 1150, BRAND, 0.62);
  glow(-80 + 40 * Math.cos(t * 0.19), H * 1.15, 950, '#FFB68B', 0.15);
  ctx.drawImage(IMG.grid, -64 + ((t * 7) % 64), -64 + ((t * 4) % 64));
}

// ---------------------------------------------------------------------------------------------
// type

function setFont(size, weight, family = 'Inter') {
  ctx.font = `${weight} ${size}px ${family === 'mono' ? '"JetBrains Mono"' : family}`;
}

function measure(s, size, weight, family = 'Inter', ls = 0) {
  ctx.save();
  setFont(size, weight, family);
  ctx.letterSpacing = `${ls}px`;
  const w = ctx.measureText(s).width;
  ctx.restore();
  return w;
}

function say(s, x, y, o = {}) {
  const { size = 40, weight = 400, family = 'Inter', color = INK, align = 'left', alpha = 1, ls = 0 } = o;
  if (alpha <= 0) return;
  ctx.save();
  ctx.globalAlpha *= alpha;
  setFont(size, weight, family);
  ctx.letterSpacing = `${ls}px`;
  ctx.textAlign = align;
  ctx.textBaseline = 'alphabetic';
  if (o.gradient) {
    const w = ctx.measureText(s).width;
    const x0 = align === 'center' ? x - w / 2 : align === 'right' ? x - w : x;
    const g = ctx.createLinearGradient(x0, 0, x0 + w, 0);
    o.gradient.forEach((c, i) => g.addColorStop(i / (o.gradient.length - 1), c));
    ctx.fillStyle = g;
  } else {
    ctx.fillStyle = color;
  }
  ctx.fillText(s, x, y);
  ctx.restore();
}

function wrapWords(s, size, weight, maxW) {
  const lines = [];
  let line = '';
  for (const word of s.split(' ')) {
    const next = line ? `${line} ${word}` : word;
    if (line && measure(next, size, weight) > maxW) {
      lines.push(line);
      line = word;
    } else {
      line = next;
    }
  }
  if (line) lines.push(line);
  return lines;
}

// the feature graphic's white-to-lavender
const SHEEN = ['#FFFFFF', '#E1DFFF', '#C1C1FF', '#9C9DD8'];

// one line arriving: up from a little below as it fades in, and on up as it fades out
function arrive(t, tin, tout = Infinity) {
  const p = easeOut(seg(t, tin, tin + 0.5));
  const q = Number.isFinite(tout) ? easeIn(seg(t, tout, tout + 0.3)) : 0;
  return { alpha: p * (1 - q), dy: (1 - p) * 30 - q * 24 };
}

// a headline over a line or two of copy, at the left, its top at y
const HEAD = { size: 74, weight: 600, lh: 86 };
const COPY = { size: 36, weight: 400, lh: 50 };
const COLUMN = 120;
const COLUMN_W = 840;

function caption(t, tin, tout, head, sub, y) {
  if (t < tin || t > tout + 0.4) return;
  const heads = head.split('\n');
  const subs = sub ? wrapWords(sub, COPY.size, COPY.weight, COLUMN_W) : [];
  heads.forEach((l, i) => {
    const a = arrive(t, tin + i * 0.07, tout);
    say(l, COLUMN, y + HEAD.size * 0.8 + i * HEAD.lh + a.dy, { size: HEAD.size, weight: HEAD.weight, gradient: SHEEN, alpha: a.alpha, ls: -1.5 });
  });
  const top = y + heads.length * HEAD.lh + 26;
  subs.forEach((l, i) => {
    const a = arrive(t, tin + 0.2 + i * 0.06, tout);
    say(l, COLUMN, top + COPY.size * 0.8 + i * COPY.lh + a.dy, { size: COPY.size, weight: COPY.weight, color: SUB, alpha: a.alpha });
  });
}

function captionHeight(head, sub) {
  return head.split('\n').length * HEAD.lh + 26 + wrapWords(sub, COPY.size, COPY.weight, COLUMN_W).length * COPY.lh;
}

// ---------------------------------------------------------------------------------------------
// shapes

function logo(cx, cy, size, alpha = 1, color = C.primary) {
  ctx.save();
  ctx.translate(cx - size / 2, cy - size / 2);
  ctx.scale(size / 24, size / 24);
  ctx.fillStyle = color;
  const base = ctx.globalAlpha;
  for (const p of LOGO) {
    ctx.globalAlpha = base * alpha * p.alpha;
    ctx.fill(p.path);
  }
  ctx.restore();
}

// the logo's own four-pointed sparkle
function sparkle(x, y, r, alpha, color = '#FFFFFF') {
  if (alpha <= 0 || r <= 0) return;
  ctx.save();
  ctx.globalAlpha *= alpha;
  ctx.fillStyle = color;
  ctx.beginPath();
  for (let i = 0; i < 8; i++) {
    const a = (i * Math.PI) / 4 - Math.PI / 2;
    const rr = i % 2 ? r * 0.28 : r;
    ctx.lineTo(x + rr * Math.cos(a), y + rr * Math.sin(a));
  }
  ctx.closePath();
  ctx.fill();
  ctx.restore();
}

function bubble(x, y, r, alpha) {
  if (alpha <= 0 || r <= 0) return;
  ctx.save();
  ctx.globalAlpha *= alpha;
  const fill = ctx.createRadialGradient(x - r * 0.3, y - r * 0.3, r * 0.1, x, y, r);
  fill.addColorStop(0, 'rgba(255, 255, 255, 0.02)');
  fill.addColorStop(0.75, 'rgba(193, 193, 255, 0.10)');
  fill.addColorStop(1, 'rgba(255, 182, 139, 0.32)');
  ctx.fillStyle = fill;
  ctx.beginPath();
  ctx.arc(x, y, r, 0, Math.PI * 2);
  ctx.fill();
  ctx.strokeStyle = 'rgba(255, 255, 255, 0.5)';
  ctx.lineWidth = Math.max(1, r * 0.08);
  ctx.stroke();
  ctx.fillStyle = 'rgba(255, 255, 255, 0.75)';
  ctx.beginPath();
  ctx.ellipse(x - r * 0.38, y - r * 0.42, r * 0.22, r * 0.12, -0.7, 0, Math.PI * 2);
  ctx.fill();
  ctx.restore();
}

function burst(x, y, r, p) {
  if (p <= 0 || p >= 1) return;
  ctx.save();
  ctx.globalAlpha *= 0.8 * (1 - p);
  ctx.fillStyle = '#FFFFFF';
  for (let i = 0; i < 7; i++) {
    const a = (i / 7) * Math.PI * 2 + r;
    const d = r * (0.9 + 1.4 * easeOut(p));
    ctx.beginPath();
    ctx.arc(x + d * Math.cos(a), y + d * Math.sin(a), Math.max(0.8, r * 0.12 * (1 - p)), 0, Math.PI * 2);
    ctx.fill();
  }
  ctx.restore();
}

// ---------------------------------------------------------------------------------------------
// the phone: Android Studio's Pixel 9a frame around the redrawn screen

const FRAME_W = 1224;
const FRAME_H = 2570;
const SCREEN_X = 69;
const SCREEN_Y = 73;
const PHONE_S = 0.58;
// where the phone's top sits to show the sheet, its bottom edge just in frame, and to show the
// launcher's field and buttons
const VIEW_SHEET = 1064 - FRAME_H * PHONE_S;
const VIEW_MAIN = H / 2 - ((SCREEN_Y + 1125) * PHONE_S);
const GLOW_PAD = 180;

function makeGlow() {
  const q = 0.25;
  const c = canvas((FRAME_W + 2 * GLOW_PAD) * q, (FRAME_H + 2 * GLOW_PAD) * q);
  const g = c.getContext('2d');
  g.filter = `blur(${70 * q}px)`;
  g.fillStyle = BRAND;
  g.beginPath();
  g.roundRect(GLOW_PAD * q, GLOW_PAD * q, FRAME_W * q, FRAME_H * q, 160 * q);
  g.fill();
  return c;
}

function phone(x, y, s, state) {
  const layout = SCREENS.drawScreen(sctx, state);
  ctx.save();
  ctx.globalAlpha *= 0.55;
  ctx.drawImage(IMG.glow, x - GLOW_PAD * s, y - GLOW_PAD * s, (FRAME_W + 2 * GLOW_PAD) * s, (FRAME_H + 2 * GLOW_PAD) * s);
  ctx.restore();
  ctx.drawImage(IMG.back, x, y, FRAME_W * s, FRAME_H * s);
  ctx.drawImage(screenCanvas, x + SCREEN_X * s, y + SCREEN_Y * s, SCREEN_W * s, SCREEN_H * s);
  ctx.drawImage(IMG.mask, x + SCREEN_X * s, y + SCREEN_Y * s, SCREEN_W * s, SCREEN_H * s);
  return layout;
}

// a fingertip, the way Android's "Show taps" draws one: it settles, presses at `at` and lifts
function finger(x, y, t, at) {
  const p = seg(t, at - 0.22, at - 0.04);
  const q = seg(t, at + 0.12, at + 0.32);
  if (p <= 0 || q >= 1) return;
  const press = t >= at - 0.04 && t < at + 0.12 ? 0.86 : 1;
  ctx.save();
  ctx.globalAlpha *= p * (1 - q);
  ctx.fillStyle = 'rgba(255, 255, 255, 0.34)';
  ctx.strokeStyle = 'rgba(255, 255, 255, 0.7)';
  ctx.lineWidth = 2.5;
  ctx.beginPath();
  ctx.arc(x + (1 - p) * 26, y + (1 - p) * 34, 25 * press, 0, Math.PI * 2);
  ctx.fill();
  ctx.stroke();
  ctx.restore();
}

// ---------------------------------------------------------------------------------------------
// the store screenshots' five screens, for holding the redraw against them (render.mjs --screens)

const SHOTS = {
  1: () => ({ sheet: { rows: YT_ROWS, url: sanitized(YOUTUBE, YT_ROWS), urlLines: urlLines(sanitized(YOUTUBE, YT_ROWS)) } }),
  2: () => ({ sheet: { rows: AMAZON_ROWS, url: sanitized(AMAZON, AMAZON_ROWS), urlLines: urlLines(sanitized(AMAZON, AMAZON_ROWS)) } }),
  3: () => ({ main: { text: APP.demo }, sheet: { rows: [], url: SHORT, urlLines: urlLines(SHORT) } }),
  4: () => ({ main: { text: APP.demo } }),
  5: () => ({ main: {} }),
};

function renderScreen(name) {
  stage.width = SCREEN_W;
  stage.height = SCREEN_H;
  SCREENS.drawScreen(ctx, SHOTS[name]());
  ctx.drawImage(IMG.mask, 0, 0);
}

// ---------------------------------------------------------------------------------------------
// the timeline. Sections start on bars; the times inside each are offsets from its start

const CUES = [];
const cue = (t, type, extra = {}) => CUES.push({ t, type, ...extra });

const BRAND_AT = 3 * BAR;
const YT_AT = 4 * BAR;
const AMAZON_AT = 7 * BAR;
const SHORT_AT = 9 * BAR;
const FEATURES_AT = 13 * BAR;
const END_AT = 15 * BAR;
const DURATION = 17 * BAR;

// 1. the hook: a shared link types itself out, its trackers light up, and a sweep washes them off

const HOOK_LINE_1 = YOUTUBE.slice(0, YOUTUBE.indexOf('&si'));
const HOOK_LINE_2 = YOUTUBE.slice(YOUTUBE.indexOf('&si'));
const HOOK_SPANS = [[0, HOOK_LINE_2.indexOf('&feature')], [HOOK_LINE_2.indexOf('&feature'), HOOK_LINE_2.length]];
const HOOK_HEAD = [0.3, 2 * BAR + 0.05];
const HOOK_ROT = 2 * BEAT;
const HOOK_TYPE = [3 * BEAT, 5 * BEAT];
const HOOK_FLAG = 6 * BEAT;
const HOOK_SCRUB = [2 * BAR, 2 * BAR + 0.45];
const HOOK_SETTLE = [2 * BAR + 0.8, 2 * BAR + 1.35];
const HOOK_MONO = 52;

// the bubbles the sweep leaves behind it, one every couple of characters
const HOOK_BUBBLES = (() => {
  const r = random(7);
  const list = [];
  const n = 24;
  for (let k = 0; k < n; k++) {
    const ci = ((k + 0.5) / n) * HOOK_LINE_2.length;
    const born = lerp(HOOK_SCRUB[0], HOOK_SCRUB[1], ci / HOOK_LINE_2.length);
    list.push({ ci, born, size: 7 + 17 * r(), life: 0.45 + 0.75 * r(), vx: (r() - 0.5) * 70, vy: -(70 + 110 * r()), phase: r() * 6 });
  }
  return list;
})();

for (let k = 0; k < 8; k++) cue(lerp(HOOK_TYPE[0], HOOK_TYPE[1], k / 8), 'key');
cue(HOOK_ROT, 'rot');
cue(HOOK_FLAG, 'flag');
cue(HOOK_FLAG + 0.15, 'flag', { second: true });
cue(HOOK_SCRUB[0], 'scrub', { until: HOOK_SCRUB[1] });
HOOK_BUBBLES.forEach((b, i) => {
  if (i % 3 === 0) cue(b.born + b.life, 'pop', { k: i });
});
cue(HOOK_SETTLE[1] - 0.15, 'sparkle');
cue(BRAND_AT - 1.2, 'rise', { until: BRAND_AT });

// the opening headline, whose "infested" and "trackers" go off: bile green and blotched with mold,
// shivering and dripping slime
const SLIME = ['#E4F58C', '#A9CD3D', '#5E7E1B'];
const SLIME_DRIP = '#86AB2A';
const SLIME_PAD = 24;

// a repeatable number from 0 to 1 for a pair of integers
function hash(a, b) {
  const v = Math.sin(a * 127.1 + b * 311.7) * 43758.5453;
  return v - Math.floor(v);
}

// a word gone off, with mold, drips and a canvas of its own, so that no two rot in step. Each drip
// names the letter it hangs from and where along that letter's foot
function rotten(word, seed, drips) {
  const r = random(seed);
  return {
    word,
    seed,
    canvas: null,
    spots: Array.from({ length: 5 }, () => ({ fx: r(), fy: r(), delay: 0.3 * r() })),
    mold: Array.from({ length: 18 }, () => ({ fx: r(), fy: 0.1 + 0.65 * r(), size: 0.05 + 0.1 * r(), dark: r() < 0.65 })),
    drips: drips.map(([letter, along], i) => ({
      letter, along, start: 0.75 + i * 0.17 + (seed % 3) * 0.06, period: 1.2 + 0.37 * ((i * 5 + seed) % 4), max: 44 + 15 * ((i * 3 + seed) % 4),
    })),
  };
}

const HOOK_LINE = [
  'Share links are ',
  rotten('infested', 19, [[0, 0.5], [1, 0.82], [2, 0.3], [4, 0.45], [5, 0.6], [7, 0.84]]),
  ' with ',
  rotten('trackers', 61, [[0, 0.62], [2, 0.82], [4, 0.25], [6, 0.3], [7, 0.5]]),
];

function infestedHeadline(t, tin, tout, rotAt, y) {
  if (t < tin || t > tout + 0.4) return;
  const a = arrive(t, tin, tout);
  const parts = HOOK_LINE.map((p) => (typeof p === 'string' ? p : p.word));
  const { size, ls } = fitted(parts.join(''), 84, 600, -2);
  const widths = parts.map((s) => measure(s, size, 600, 'Inter', ls));
  const total = widths.reduce((sum, w) => sum + w, 0);
  const x0 = W / 2 - total / 2;
  const base = y + a.dy;
  ctx.save();
  ctx.globalAlpha *= a.alpha;
  // the clean words, under one sheen across the whole line
  setFont(size, 600);
  ctx.letterSpacing = `${ls}px`;
  ctx.textAlign = 'left';
  ctx.textBaseline = 'alphabetic';
  const sheen = ctx.createLinearGradient(x0, 0, x0 + total, 0);
  SHEEN.forEach((c, i) => sheen.addColorStop(i / (SHEEN.length - 1), c));
  ctx.fillStyle = sheen;
  let x = x0;
  HOOK_LINE.forEach((p, i) => {
    if (typeof p === 'string') ctx.fillText(p, x, base);
    else if (t < rotAt + 1.1) {
      ctx.save();
      ctx.globalAlpha *= 1 - seg(t, rotAt + 0.6, rotAt + 1.1);
      ctx.fillText(p.word, x, base);
      ctx.restore();
    }
    x += widths[i];
  });
  // then the rotten ones, so that their glow and their drips spill over the rest
  x = x0;
  HOOK_LINE.forEach((p, i) => {
    if (typeof p !== 'string' && t >= rotAt) slime(p, t, rotAt, x, base, size, ls, widths[i]);
    x += widths[i];
  });
  ctx.restore();
}

// zoom scales the shiver, the rim and the glow with the type, and dk the drips, for the thumbnail's
// larger words; the video draws at 1 and 1
function slime(rot, t, rotAt, x, base, size, ls, width, zoom = 1, dk = 1) {
  const pad = SLIME_PAD * zoom;
  const word = rot.word;
  const w = Math.ceil(width + 2 * pad);
  const h = Math.ceil(size + 2 * pad + 110 * dk);
  if (!rot.canvas || rot.canvas.width !== w || rot.canvas.height !== h) rot.canvas = canvas(w, h);
  const g = rot.canvas.getContext('2d');
  g.globalCompositeOperation = 'source-over';
  g.clearRect(0, 0, w, h);
  const by = pad + size;
  g.font = `600 ${size}px Inter`;
  g.letterSpacing = `${ls}px`;
  g.textAlign = 'left';
  g.textBaseline = 'alphabetic';
  const fill = g.createLinearGradient(0, by - size * 0.75, 0, by);
  SLIME.forEach((c, i) => fill.addColorStop(i / (SLIME.length - 1), c));
  g.fillStyle = fill;

  // every letter shivers fifteen times a second, and now and then the word twitches hard
  const beat = Math.floor(t * 15) + rot.seed;
  const rotting = seg(t, rotAt, rotAt + 1);
  const twitch = rotting >= 1 && beat % 9 < 2 && hash(Math.floor(beat / 9), 7 + rot.seed) > 0.45 ? 3.4 : rotting;
  const letters = [];
  for (let k = 0; k < word.length; k++) {
    const left = pad + g.measureText(word.slice(0, k)).width;
    const glyph = g.measureText(word[k]).width - ls;
    const jx = (hash(k, beat) - 0.5) * 2.4 * zoom * twitch;
    const jy = (hash(k + 17, beat) - 0.5) * 2.4 * zoom * twitch;
    letters.push({ left, glyph, jx, jy });
    g.save();
    g.translate(left + jx, by + jy);
    g.rotate((hash(k + 31, beat) - 0.5) * 0.05 * twitch);
    g.strokeStyle = '#26330A';
    g.lineWidth = 5 * zoom;
    g.lineJoin = 'round';
    g.strokeText(word[k], 0, 0);
    g.fillText(word[k], 0, 0);
    g.restore();
  }

  // mold, only where there are letters to grow on
  g.globalCompositeOperation = 'source-atop';
  for (const m of rot.mold) {
    g.fillStyle = m.dark ? 'rgba(30, 40, 4, 0.72)' : 'rgba(250, 255, 196, 0.6)';
    g.beginPath();
    g.arc(pad + m.fx * width, by - m.fy * size * 0.75, m.size * size * (m.dark ? 1 : 0.45), 0, Math.PI * 2);
    g.fill();
  }
  g.globalCompositeOperation = 'source-over';

  // drips, swelling from the letters' feet until a drop breaks off
  const falling = [];
  for (const d of rot.drips) {
    const u = t - (rotAt + d.start - 0.2);
    if (u < 0) continue;
    const cycle = Math.floor(u / d.period);
    const p = (u - cycle * d.period) / d.period;
    const max = d.max * (0.75 + 0.5 * hash(cycle, d.letter + rot.seed));
    const l = letters[d.letter];
    const dx = l.left + l.jx + d.along * l.glyph;
    const top = by + l.jy - 4 * zoom;
    if (p < 0.8) {
      const q = p / 0.8;
      drip(g, dx, top, dk * lerp(2, max, q * q), dk * lerp(3, 8.5, q), 3 * dk);
    } else {
      const q = (p - 0.8) / 0.2;
      drip(g, dx, top, dk * lerp(max * 0.35, 3, q), dk * lerp(4.5, 3, q), 3 * dk);
      const fall = (p - 0.8) * d.period;
      falling.push({ x: dx, y: top + dk * (max + 0.5 * 2600 * fall * fall), alpha: 1 - q });
    }
  }

  const ox = x - pad;
  const oy = base - by;
  ctx.save();
  if (t < rotAt + 1.2) {
    ctx.beginPath();
    for (const sp of rot.spots) {
      const r = width * seg(t, rotAt + sp.delay, rotAt + sp.delay + 0.8) ** 1.5;
      const cx = x + sp.fx * width;
      const cy = base - size * (0.1 + 0.6 * sp.fy);
      ctx.moveTo(cx + r, cy);
      ctx.arc(cx, cy, r, 0, Math.PI * 2);
    }
    ctx.clip();
  }
  if (twitch > 1) {
    ctx.save();
    ctx.globalAlpha *= 0.35;
    ctx.drawImage(rot.canvas, ox + 9 * zoom * (hash(beat, 3) - 0.5) * 2, oy + 5 * zoom * (hash(beat, 5) - 0.5) * 2);
    ctx.restore();
  }
  ctx.save();
  ctx.shadowColor = `rgba(150, 215, 60, ${rotting * (0.6 + 0.25 * Math.sin(t * 6 + rot.seed))})`;
  ctx.shadowBlur = 30 * zoom;
  ctx.drawImage(rot.canvas, ox, oy);
  ctx.restore();
  ctx.restore();
  for (const f of falling) {
    ctx.save();
    ctx.globalAlpha *= f.alpha;
    drip(ctx, ox + f.x, oy + f.y - 8 * dk, 0, 8 * dk, 3 * dk);
    ctx.restore();
  }
}

// a hanging drip of slime: a neck from the letter, and a drop swelling at its end
function drip(g, x, top, len, r, neck = 3) {
  g.fillStyle = SLIME_DRIP;
  if (len > 0) {
    g.beginPath();
    g.moveTo(x - r - neck, top);
    g.quadraticCurveTo(x - r * 0.45, top + len * 0.55, x - r * 0.8, top + len);
    g.lineTo(x + r * 0.8, top + len);
    g.quadraticCurveTo(x + r * 0.45, top + len * 0.55, x + r + neck, top);
    g.closePath();
    g.fill();
  }
  g.beginPath();
  g.arc(x, top + len, r, 0, Math.PI * 2);
  g.fill();
  g.fillStyle = 'rgba(255, 255, 220, 0.45)';
  g.beginPath();
  g.ellipse(x - r * 0.35, top + len - r * 0.3, r * 0.25, r * 0.4, 0, 0, Math.PI * 2);
  g.fill();
}

function sceneHook(t) {
  const out = 1 - seg(t, BRAND_AT - 0.25, BRAND_AT + 0.05);
  ctx.save();
  ctx.globalAlpha *= out;
  infestedHeadline(t, HOOK_HEAD[0], HOOK_HEAD[1], HOOK_ROT, 450);
  caption2(t, HOOK_SCRUB[0] + 0.35, Infinity, 'Scrub them clean before you share', 450);

  ctx.save();
  setFont(HOOK_MONO, 500, 'mono');
  const cw = ctx.measureText('M').width;
  ctx.restore();
  const x0 = W / 2 - (HOOK_LINE_1.length * cw) / 2;
  const settle = easeInOut(seg(t, HOOK_SETTLE[0], HOOK_SETTLE[1]));
  const y1 = lerp(610, 645, settle);
  const y2 = 682;
  const typed = Math.floor(seg(t, HOOK_TYPE[0], HOOK_TYPE[1]) * YOUTUBE.length);
  const sweep = lerp(x0 - 60, x0 + HOOK_LINE_2.length * cw + 60, seg(t, HOOK_SCRUB[0], HOOK_SCRUB[1]));
  const scrubbing = t >= HOOK_SCRUB[0];

  // line one, which the sweep spares, and which comes clean
  const clean = seg(t, HOOK_SETTLE[0], HOOK_SETTLE[1]);
  ctx.save();
  if (clean > 0) {
    ctx.shadowColor = `rgba(193, 193, 255, ${0.6 * clean})`;
    ctx.shadowBlur = 24 * clean;
  }
  say(HOOK_LINE_1.slice(0, typed), x0, y1, { size: HOOK_MONO, weight: 500, family: 'mono', color: clean > 0.5 ? '#FFFFFF' : '#D9D8EE' });
  ctx.restore();

  // line two: typed, flagged, then washed off a character at a time
  const flag = HOOK_SPANS.map((_, i) => easeOut(seg(t, HOOK_FLAG + i * 0.15, HOOK_FLAG + i * 0.15 + 0.3)));
  if (!scrubbing || sweep < x0 + HOOK_LINE_2.length * cw) {
    HOOK_SPANS.forEach(([a, b], i) => {
      const left = Math.max(x0 + a * cw, scrubbing ? sweep : -Infinity);
      const right = x0 + a * cw + (b - a) * cw * flag[i];
      if (flag[i] > 0 && right > left) {
        ctx.save();
        ctx.fillStyle = 'rgba(255, 178, 188, 0.16)';
        ctx.beginPath();
        ctx.roundRect(left - 6, y2 - HOOK_MONO * 0.9, right - left + 12, HOOK_MONO * 1.22, 12);
        ctx.fill();
        ctx.restore();
      }
    });
    for (let i = 0; i < HOOK_LINE_2.length; i++) {
      if (HOOK_LINE_1.length + i >= typed) break;
      const cx = x0 + (i + 0.5) * cw;
      if (scrubbing && cx < sweep) continue;
      const span = i < HOOK_SPANS[1][0] ? 0 : 1;
      const color = flag[span] > 0.5 ? C.secondary : '#D9D8EE';
      say(HOOK_LINE_2[i], x0 + i * cw, y2, { size: HOOK_MONO, weight: 500, family: 'mono', color });
    }
  }
  if (typed < YOUTUBE.length || t < HOOK_FLAG) {
    const caret = typed < HOOK_LINE_1.length ? [x0 + typed * cw, y1] : [x0 + (typed - HOOK_LINE_1.length) * cw, y2];
    if (Math.floor(t * 2.5) % 2 === 0 || typed < YOUTUBE.length) {
      ctx.fillStyle = C.primary;
      ctx.fillRect(caret[0] + 2, caret[1] - HOOK_MONO * 0.78, 4, HOOK_MONO * 0.95);
    }
  }

  // the sweep itself: a soapy sheen
  const sp = seg(t, HOOK_SCRUB[0] - 0.05, HOOK_SCRUB[1] + 0.1);
  if (sp > 0 && sp < 1) {
    ctx.save();
    ctx.globalAlpha *= Math.sin(sp * Math.PI);
    const g = ctx.createLinearGradient(sweep - 70, 0, sweep + 30, 0);
    g.addColorStop(0, 'rgba(193, 193, 255, 0)');
    g.addColorStop(0.6, 'rgba(255, 182, 139, 0.35)');
    g.addColorStop(0.85, 'rgba(255, 255, 255, 0.85)');
    g.addColorStop(1, 'rgba(255, 255, 255, 0)');
    ctx.fillStyle = g;
    ctx.beginPath();
    ctx.roundRect(sweep - 70, y2 - 62, 100, 86, 30);
    ctx.fill();
    ctx.restore();
  }

  for (const b of HOOK_BUBBLES) {
    const age = t - b.born;
    if (age < 0) continue;
    const bx = x0 + b.ci * cw + b.vx * age + 6 * Math.sin(age * 7 + b.phase);
    const by = y2 - 18 + b.vy * age;
    if (age < b.life) bubble(bx, by, b.size * easeOut(seg(age, 0, 0.15)), 1);
    else burst(bx, by, b.size, (age - b.life) / 0.2);
  }

  const shine = seg(t, HOOK_SETTLE[1] - 0.15, HOOK_SETTLE[1] + 0.35);
  if (shine > 0 && shine < 1) {
    sparkle(x0 + HOOK_LINE_1.length * cw + 34, y1 - 40, 26 * Math.sin(shine * Math.PI), 1);
    sparkle(x0 - 34, y1 - 6, 15 * Math.sin(seg(shine, 0.2, 1) * Math.PI), 0.8);
  }
  ctx.restore();
}

// the size and spacing that keep a centered line inside the frame's margins
const MAX_LINE = W - 2 * COLUMN;
function fitted(s, size, weight, ls) {
  const k = Math.min(1, MAX_LINE / measure(s, size, weight, 'Inter', ls));
  return { size: size * k, ls: ls * k };
}

// a centered headline, arriving and leaving like the captions
function caption2(t, tin, tout, head, y) {
  if (t < tin || t > tout + 0.4) return;
  const a = arrive(t, tin, tout);
  say(head, W / 2, y + a.dy, { ...fitted(head, 84, 600, -2), weight: 600, gradient: SHEEN, align: 'center', alpha: a.alpha });
}

// 2. the brand

cue(BRAND_AT, 'brand');
cue(BRAND_AT + 0.6, 'twinkle');
cue(BRAND_AT + 1.8, 'twinkle');

function brandMark(t, t0, cy, size, nameY, tagY, extra) {
  const pop = overshoot(seg(t, t0, t0 + 0.5));
  glow(W / 2, cy, size * 1.6, BRAND, 0.5 * seg(t, t0, t0 + 0.4));
  if (pop > 0) {
    ctx.save();
    ctx.translate(W / 2, cy);
    ctx.scale(pop, pop);
    ctx.translate(-W / 2, -cy);
    logo(W / 2, cy, size, clamp(pop));
    ctx.restore();
  }
  // the logo's sparkle sits at (17, 7) of its 24
  for (const at of [t0 + 0.6, t0 + 1.8]) {
    const s = seg(t, at, at + 0.45);
    if (s > 0 && s < 1) sparkle(W / 2 + ((17 - 12) / 24) * size, cy + ((7 - 12) / 24) * size, size * 0.12 * Math.sin(s * Math.PI), 0.9);
  }
  const n = arrive(t, t0 + 0.25, Infinity);
  say(NAME, W / 2, nameY + n.dy, { ...fitted(NAME, 150, 600, -4), weight: 600, gradient: SHEEN, align: 'center', alpha: n.alpha });
  const g = arrive(t, t0 + 0.45, Infinity);
  say(TAGLINE, W / 2, tagY + g.dy, { size: 46, weight: 400, color: SUB, align: 'center', alpha: g.alpha });
  if (extra) {
    const e = arrive(t, t0 + 0.75, Infinity);
    say(extra, W / 2, tagY + 104 + e.dy, { size: 40, weight: 600, color: C.primary, align: 'center', alpha: e.alpha });
  }
}

function sceneBrand(t) {
  // gone before the phone rises into where the name was
  const out = easeIn(seg(t, YT_AT - 0.5, YT_AT - 0.1));
  ctx.save();
  ctx.globalAlpha *= 1 - out;
  ctx.translate(0, -60 * out);
  brandMark(t, BRAND_AT, 380, 300, 700, 796);
  ctx.restore();
}

// 3 to 5. the phone: a YouTube link shared in, then an Amazon one, then the demo short link

const TAPS = [];
const tapAt = (t, target, extra = {}) => {
  TAPS.push({ t, target, ...extra });
  cue(t, 'tap');
};

// YouTube
const YT = {
  sheet: YT_AT + BEAT,
  expand: YT_AT + 1.5 * BEAT,
  flag: YT_AT + 3 * BEAT,
  off: YT_AT + 6 * BEAT,
  on: YT_AT + 9 * BEAT,
  copy: YT_AT + 10 * BEAT,
};
YT.hide = YT.copy + 0.3;
cue(YT_AT, 'whoosh');
cue(YT.sheet, 'sheet');
cue(YT.expand, 'expand');
cue(YT.flag, 'flag');
cue(YT.flag + 0.15, 'flag', { second: true });
tapAt(YT.off, { row: 1 });
cue(YT.off + 0.05, 'uncheck');
tapAt(YT.on, { row: 1 });
cue(YT.on + 0.05, 'check');
tapAt(YT.copy, { button: 1 });
cue(YT.hide, 'sheetDown');

// Amazon
const AZ = {
  sheet: AMAZON_AT,
  expand: AMAZON_AT + BEAT / 2,
  wipe: AMAZON_AT + 3 * BEAT,
  count: AMAZON_AT + 4.5 * BEAT,
  share: AMAZON_AT + 6 * BEAT,
};
AZ.hide = AZ.share + 0.3;
cue(AZ.sheet, 'sheet');
cue(AZ.expand, 'expand');
for (let i = 0; i < 4; i++) cue(AZ.wipe + i * BEAT / 4, 'drop', { k: i });
cue(AZ.count, 'count');
tapAt(AZ.share, { button: 0 });
cue(AZ.hide, 'sheetDown');

// the short link. The pan to the launcher starts a beat early, as the Amazon sheet leaves
const SL = {
  pan: SHORT_AT - BEAT,
  lucky: SHORT_AT + 2 * BEAT,
  remove: SHORT_AT + 4 * BEAT,
  cloud: SHORT_AT + 6 * BEAT,
  resolve: SHORT_AT + 8 * BEAT,
};
SL.sheet = SL.remove + 0.15;
// unlike the app, which starts the rules' trackers unchecked, the expanded link comes up with every
// box checked and they're unchecked one at a time, a beat apart: the owner's choice, to show it
SL.offs = [1, 2, 3, 4].map((k) => SL.resolve + k * BEAT);
cue(SL.pan, 'whoosh', { soft: true });
tapAt(SL.lucky, { main: 'lucky' });
cue(SL.lucky + 0.05, 'lucky');
tapAt(SL.remove, { main: 'remove' });
cue(SL.sheet, 'sheet');
tapAt(SL.cloud, { cloud: true });
cue(SL.cloud + 0.05, 'load', { until: SL.resolve });
cue(SL.resolve, 'resolve');
cue(SL.resolve, 'expand');
SL.offs.forEach((at, i) => {
  tapAt(at, { row: i });
  cue(at + 0.05, 'drop', { k: i });
});

// the URL field's height in lines, springing to each new length as animateContentSize does, from
// wherever the last spring had got to
function springLines(t, steps) {
  let from = steps[0].lines;
  let to = from;
  let start = steps[0].t;
  for (const s of steps.slice(1)) {
    if (t < s.t) break;
    from += (to - from) * SCREENS.bouncy(s.t - start);
    to = s.lines;
    start = s.t;
  }
  return from + (to - from) * SCREENS.bouncy(t - start);
}

// the expanded link's rows at t, with every box checked until its tap
function shortRows(t) {
  return AMAZON_ROWS.map((r, i) => ({ ...r, enabled: true, on: 1 - seg(t, SL.offs[i], SL.offs[i] + 0.12) }));
}

const PHONE_OUT = [FEATURES_AT, FEATURES_AT + 0.5];

function phoneTop(t) {
  if (t < YT_AT + 0.7) return lerp(VIEW_SHEET + 1300, VIEW_SHEET, easeOut(seg(t, YT_AT, YT_AT + 0.7)));
  if (t >= PHONE_OUT[0]) return lerp(VIEW_SHEET, VIEW_SHEET + 1300, easeIn(seg(t, PHONE_OUT[0], PHONE_OUT[1])));
  if (t >= SL.pan) {
    const down = easeInOut(seg(t, SL.pan, SL.pan + 0.6));
    const up = easeInOut(seg(t, SL.sheet, SL.sheet + 0.6));
    return lerp(lerp(VIEW_SHEET, VIEW_MAIN, down), VIEW_SHEET, up);
  }
  return VIEW_SHEET;
}

// how far into its show animation the sheet is
const lifted = (t, up, down = Infinity) => SCREENS.fastOutSlowIn(seg(t, up, up + 0.3)) * (1 - SCREENS.fastOutSlowIn(seg(t, down, down + 0.25)));

function pressOf(t, at) {
  return t >= at - 0.04 && t < at + 0.7 ? t - at + 0.04 : -1;
}

function phoneState(t) {
  if (t < AMAZON_AT) {
    const tRow = YT_ROWS.map((r, i) => {
      if (i !== 1) return { ...r };
      const off = seg(t, YT.off, YT.off + 0.12);
      const on = seg(t, YT.on, YT.on + 0.12);
      return { ...r, on: t < YT.on ? 1 - off : on };
    });
    const flagged = (i) => (i >= 2 ? YT.flag + (i - 2) * 0.15 : null);
    tRow.forEach((r, i) => {
      if (flagged(i) !== null) r.highlight = highlighter(t, flagged(i));
    });
    return {
      main: {},
      sheet: t >= YT.sheet ? {
        rows: tRow,
        rowsShown: SCREENS.expandIn(t - YT.expand),
        url: sanitized(YOUTUBE, tRow),
        urlLines: 2,
        lift: lifted(t, YT.sheet, YT.hide),
        press: { copy: null },
      } : null,
    };
  }
  if (t < SHORT_AT) {
    const rows = AMAZON_ROWS.map((r) => ({ ...r }));
    return {
      main: {},
      sheet: {
        rows,
        rowsShown: SCREENS.expandIn(t - AZ.expand),
        url: sanitized(AMAZON, rows),
        urlLines: 2,
        lift: lifted(t, AZ.sheet, AZ.hide),
        press: {},
      },
    };
  }
  const resolved = t >= SL.resolve;
  const rows = resolved ? shortRows(t) : [];
  // the link changes as each box crosses halfway, and the field springs to its new height
  const steps = [{ t: 0, lines: urlLines(SHORT) }, { t: SL.resolve, lines: urlLines(sanitized(AMAZON, shortRows(SL.resolve))) }];
  SL.offs.forEach((at) => steps.push({ t: at + 0.06, lines: urlLines(sanitized(AMAZON, shortRows(at + 0.06))) }));
  return {
    main: { text: t >= SL.lucky + 0.05 ? APP.demo : '', press: {} },
    sheet: t >= SL.sheet ? {
      rows,
      rowsShown: resolved ? SCREENS.expandIn(t - SL.resolve) : 1,
      url: resolved ? sanitized(AMAZON, rows) : SHORT,
      urlLines: springLines(t, steps),
      lift: lifted(t, SL.sheet),
      loading: t >= SL.cloud + 0.1 && !resolved ? t - SL.cloud - 0.1 : null,
      press: {},
    } : null,
  };
}

// the rules' unchecked rows, outlined for a moment as they are pointed out
function highlighter(t, at) {
  const p = seg(t, at, at + 1.3);
  if (p <= 0 || p >= 1) return null;
  return (g, x, y, w, h) => {
    g.save();
    g.globalAlpha = Math.sin(p * Math.PI);
    g.strokeStyle = C.secondary;
    g.lineWidth = 7;
    g.beginPath();
    g.roundRect(x - 4, y - 4, w + 8 + 168, h + 8, 14);
    g.stroke();
    g.restore();
  };
}

function scenePhone(t) {
  const state = phoneState(t);
  // the presses are placed by the layout they land on
  const L = state.sheet ? SCREENS.sheetLayout(sctx, state.sheet) : null;
  // the expanded link's sheet outgrows the frame while every box is checked, so the phone eases back
  // until the whole sheet fits, its bottom edge staying put. With a sheet that fits, this is the
  // usual framing
  let s = PHONE_S;
  let top = phoneTop(t);
  if (L && t >= SL.sheet && t < PHONE_OUT[0]) {
    s = Math.min(PHONE_S, 1040 / (FRAME_H - SCREEN_Y - L.top + 24));
    top = lerp(VIEW_MAIN, 1064 - FRAME_H * s, easeInOut(seg(t, SL.sheet, SL.sheet + 0.6)));
  }
  const x = 1395 - (FRAME_W * s) / 2;
  const M = SCREENS.mainLayout(sctx);
  const fingers = [];
  for (const tap of TAPS) {
    const p = pressOf(t, tap.t);
    const near = t > tap.t - 0.3 && t < tap.t + 0.4;
    if (!near && p < 0) continue;
    let at = null;
    if (tap.target.row !== undefined && L) at = { x: L.checks[tap.target.row].x, y: L.checks[tap.target.row].y };
    if (tap.target.button !== undefined && L) {
      const b = L.buttons[tap.target.button];
      at = { x: b.x + b.w / 2 + 6, y: b.y + b.h / 2 + 4 };
      if (p >= 0) state.sheet.press[['share', 'copy', 'open'][tap.target.button]] = { t: p, x: at.x, y: at.y };
    }
    if (tap.target.cloud && L) {
      at = { x: L.cloud.x, y: L.cloud.y };
      if (p >= 0) state.sheet.press.cloud = { t: p, x: at.x, y: at.y };
    }
    if (tap.target.main) {
      const b = M[tap.target.main];
      at = { x: b.x + b.w / 2 + 10, y: b.y + b.h / 2 + 4 };
      if (p >= 0) state.main.press[tap.target.main] = { t: p, x: at.x, y: at.y };
    }
    if (tap.target.row !== undefined && p >= 0) state.sheet.rows[tap.target.row].press = { t: p };
    if (at) fingers.push({ at, t: tap.t });
  }
  phone(x, top, s, state);
  for (const f of fingers) finger(x + (SCREEN_X + f.at.x) * s, top + (SCREEN_Y + f.at.y) * s, t, f.t);
}

const YT_COPY_1 = ['Send a link to it', 'Automatically remove known trackers'];
const YT_COPY_2 = ['You decide what stays', 'Tap a box to keep or drop any part, and the clean link follows'];
const AZ_COPY = ['Long links, trimmed', 'Your friends will appreciate the sanitized link'];
const SL_COPY = ['Short links, too', 'Expand short links to see what\'s hidden inside'];
// the store listing's claim, in its terms: the app's internet access is for this alone
const SL_NOTE = "That tap is the app's only use of the internet";

function sceneCopy(t) {
  const mid = (head, sub) => H / 2 - captionHeight(head, sub) / 2;
  caption(t, YT_AT + 0.5, YT.off - 0.35, ...YT_COPY_1, mid(...YT_COPY_1));
  caption(t, YT.off - 0.05, AMAZON_AT - 0.45, ...YT_COPY_2, mid(...YT_COPY_2));
  caption(t, AMAZON_AT + 0.15, SHORT_AT - 0.45, ...AZ_COPY, 190);
  amazonPanel(t);
  caption(t, SHORT_AT, FEATURES_AT - 0.35, ...SL_COPY, mid(...SL_COPY) - 50);
  const n = arrive(t, SL.cloud + 0.15, FEATURES_AT - 0.35);
  if (t > SL.cloud) {
    const y = mid(...SL_COPY) - 50 + captionHeight(...SL_COPY) + 70;
    ctx.save();
    ctx.globalAlpha *= n.alpha;
    ctx.fillStyle = 'rgba(255, 182, 139, 0.12)';
    ctx.strokeStyle = 'rgba(255, 182, 139, 0.45)';
    ctx.lineWidth = 2;
    const w = measure(SL_NOTE, 32, 600) + 64;
    ctx.beginPath();
    ctx.roundRect(COLUMN, y + n.dy - 46, w, 66, 33);
    ctx.fill();
    ctx.stroke();
    ctx.restore();
    say(SL_NOTE, COLUMN + 32, y + n.dy - 2, { size: 32, weight: 600, color: C.tertiary, alpha: n.alpha });
  }
}

// the Amazon link as shared, wiped down to what the sheet keeps, with the count of characters
const PANEL_MONO = 25;

function amazonPanel(t) {
  if (t < AMAZON_AT || t > SHORT_AT) return;
  const a = arrive(t, AMAZON_AT + 0.45, SHORT_AT - 0.45);
  const top = 450 + a.dy;
  ctx.save();
  setFont(PANEL_MONO, 500, 'mono');
  const cw = ctx.measureText('M').width;
  ctx.restore();
  const per = Math.floor((COLUMN_W - 56) / cw);
  const lines = [];
  for (let i = 0; i < AMAZON.length; i += per) lines.push(i);
  const keep = AMAZON.indexOf('?');
  const lh = 38;
  const wipe = seg(t, AZ.wipe, AZ.wipe + 0.6);
  const sweepY = top + 30 + wipe * (lines.length * lh + 10);
  const shrink = easeInOut(seg(t, AZ.wipe + 0.65, AZ.wipe + 1.0));
  const boxH = lerp(lines.length * lh + 44, lh + 44, shrink);

  ctx.save();
  ctx.globalAlpha *= a.alpha;
  ctx.fillStyle = 'rgba(255, 255, 255, 0.05)';
  ctx.strokeStyle = 'rgba(255, 255, 255, 0.14)';
  ctx.lineWidth = 2;
  ctx.beginPath();
  ctx.roundRect(COLUMN, top, COLUMN_W, boxH, 22);
  ctx.fill();
  ctx.stroke();
  ctx.beginPath();
  ctx.rect(COLUMN, top, COLUMN_W, boxH);
  ctx.clip();
  lines.forEach((start, li) => {
    const y = top + 22 + PANEL_MONO + li * lh;
    for (let i = start; i < Math.min(AMAZON.length, start + per); i++) {
      const tracker = i >= keep;
      if (tracker && y - 10 < sweepY && wipe > 0) continue;
      say(AMAZON[i], COLUMN + 28 + (i - start) * cw, y, { size: PANEL_MONO, weight: 500, family: 'mono', color: tracker ? C.secondary : '#FFFFFF' });
    }
  });
  if (wipe > 0 && wipe < 1) {
    const g = ctx.createLinearGradient(0, sweepY - 26, 0, sweepY + 6);
    g.addColorStop(0, 'rgba(255, 182, 139, 0)');
    g.addColorStop(0.7, 'rgba(255, 182, 139, 0.35)');
    g.addColorStop(1, 'rgba(255, 255, 255, 0.9)');
    ctx.fillStyle = g;
    ctx.fillRect(COLUMN, sweepY - 26, COLUMN_W, 32);
  }
  ctx.restore();

  const c = arrive(t, AZ.count, SHORT_AT - 0.45);
  const cy = top + boxH + 92 + c.dy;
  const from = String(AMAZON.length);
  const to = String(sanitized(AMAZON, AMAZON_ROWS).length);
  say(from, COLUMN, cy, { size: 72, weight: 600, color: C.secondary, alpha: c.alpha, ls: -1.5 });
  const fw = measure(from, 72, 600, 'Inter', -1.5);
  say('→', COLUMN + fw + 22, cy - 6, { size: 60, weight: 400, color: SUB, alpha: c.alpha });
  say(to, COLUMN + fw + 104, cy, { size: 72, weight: 600, gradient: SHEEN, alpha: c.alpha, ls: -1.5 });
  const tw = measure(to, 72, 600, 'Inter', -1.5);
  say('characters', COLUMN + fw + 104 + tw + 22, cy, { size: 36, weight: 400, color: SUB, alpha: c.alpha });
}

// 6. features

const CHIP_TIMES = [1, 2, 3, 4, 5].map((k) => FEATURES_AT + k * BEAT);
CHIP_TIMES.forEach((at, i) => cue(at, 'chip', { k: i }));
cue(FEATURES_AT, 'whoosh');

function chips() {
  return [
    { s: 'ClearURLs rules built in', dot: C.primary },
    { s: 'Internet only when you ask', dot: C.tertiary },
    { s: 'Remembers each site', dot: C.secondary },
    { s: `${APP.languages} languages`, dot: '#E1DFFF' },
    { s: 'Open source', dot: BRAND },
  ];
}

function sceneFeatures(t) {
  const out = seg(t, END_AT - 0.25, END_AT + 0.05);
  ctx.save();
  ctx.globalAlpha *= 1 - out;
  caption2(t, FEATURES_AT + 0.35, Infinity, 'Private by default', 420);
  const list = chips();
  const rows = [list.slice(0, 3), list.slice(3)];
  let k = 0;
  rows.forEach((row, r) => {
    const widths = row.map((c) => measure(c.s, 40, 600) + 120);
    const total = widths.reduce((a, b) => a + b, 0) + (row.length - 1) * 28;
    let x = W / 2 - total / 2;
    row.forEach((c, i) => {
      chip(c, x, 580 + r * 124, widths[i], t, CHIP_TIMES[k++]);
      x += widths[i] + 28;
    });
  });
  ctx.restore();
}

function chip(c, x, cy, w, t, at) {
  const s = overshoot(seg(t, at, at + 0.4));
  if (s <= 0) return;
  const h = 92;
  ctx.save();
  ctx.translate(x + w / 2, cy);
  ctx.scale(s, s);
  ctx.translate(-(x + w / 2), -cy);
  ctx.globalAlpha *= clamp(s);
  ctx.fillStyle = 'rgba(255, 255, 255, 0.07)';
  ctx.strokeStyle = 'rgba(255, 255, 255, 0.17)';
  ctx.lineWidth = 2;
  ctx.beginPath();
  ctx.roundRect(x, cy - h / 2, w, h, h / 2);
  ctx.fill();
  ctx.stroke();
  ctx.fillStyle = c.dot;
  ctx.beginPath();
  ctx.arc(x + 46, cy, 12, 0, Math.PI * 2);
  ctx.fill();
  ctx.restore();
  ctx.save();
  ctx.translate(x + w / 2, cy);
  ctx.scale(s, s);
  ctx.translate(-(x + w / 2), -cy);
  say(c.s, x + 78, cy + 14, { size: 40, weight: 600, color: INK, alpha: clamp(s) });
  ctx.restore();
}

// 7. the end card

cue(END_AT, 'final');

function sceneEnd(t) {
  brandMark(t, END_AT, 330, 240, 610, 706, 'Free \u00B7 No ads \u00B7 No tracking \u00B7 Open source');
}

// ---------------------------------------------------------------------------------------------

const SCENES = [
  { from: 0, to: BRAND_AT + 0.1, draw: sceneHook },
  { from: BRAND_AT, to: YT_AT + 0.15, draw: sceneBrand },
  { from: YT_AT, to: PHONE_OUT[1], draw: scenePhone },
  { from: YT_AT, to: FEATURES_AT, draw: sceneCopy },
  { from: FEATURES_AT, to: END_AT + 0.1, draw: sceneFeatures },
  { from: END_AT, to: DURATION + 1, draw: sceneEnd },
];

// the section starts the soundtrack is arranged around
const MARKS = { beat: BEAT, rot: HOOK_ROT, scrub: HOOK_SCRUB[0], brand: BRAND_AT, yt: YT_AT, amazon: AMAZON_AT, short: SHORT_AT, resolve: SL.resolve, features: FEATURES_AT, end: END_AT };

function renderFrame(t) {
  if (stage.width !== W || stage.height !== H) {
    stage.width = W;
    stage.height = H;
  }
  ctx.setTransform(1, 0, 0, 1, 0, 0);
  ctx.globalAlpha = 1;
  ctx.imageSmoothingEnabled = true;
  ctx.imageSmoothingQuality = 'high';
  backdrop(t);
  for (const s of SCENES) if (t >= s.from && t < s.to) s.draw(t);
  const fade = seg(t, DURATION - 0.7, DURATION - 0.05);
  if (fade > 0) {
    ctx.fillStyle = `rgba(0, 0, 0, ${fade})`;
    ctx.fillRect(0, 0, W, H);
  }
}

// ---------------------------------------------------------------------------------------------
// the YouTube thumbnail (render.mjs --thumbnail): the opening headline, large and rotten, beside the
// share sheet with the YouTube link's trackers outlined

function renderThumbnail() {
  stage.width = 1280;
  stage.height = 720;
  ctx.setTransform(stage.width / W, 0, 0, stage.height / H, 0, 0);
  ctx.globalAlpha = 1;
  ctx.imageSmoothingEnabled = true;
  ctx.imageSmoothingQuality = 'high';
  // a moment well into the rot, its drips at several lengths and neither word twitching
  const t = 2.9;
  const rotAt = 0.5;
  backdrop(t);
  const rows = YT_ROWS.map((r, i) => ({ ...r, highlight: i >= 2 ? highlighter(0.65, 0) : null }));
  const s = 0.62;
  phone(1500 - (FRAME_W * s) / 2, 1064 - FRAME_H * s, s, { main: {}, sheet: { rows, url: sanitized(YOUTUBE, rows), urlLines: 2 } });
  const rotten = (rot, x, base, size) => {
    const ls = (-2 * size) / 84;
    slime(rot, t, rotAt, x, base, size, ls, measure(rot.word, size, 600, 'Inter', ls), size / 84, Math.min(size / 84, 1.6));
  };
  say('Share links are', 90, 300, { size: 112, weight: 600, gradient: SHEEN, ls: -3 });
  rotten(HOOK_LINE[1], 90, 548, 236);
  say('with', 90, 770, { size: 150, weight: 600, gradient: SHEEN, ls: -4 });
  rotten(HOOK_LINE[3], 90 + measure('with ', 150, 600, 'Inter', -4), 770, 150);
  logo(132, 952, 84);
  say(NAME, 196, 975, { size: 56, weight: 600, gradient: SHEEN, ls: -1 });
}

window.setup = setup;
window.renderThumbnail = renderThumbnail;
window.renderFrame = renderFrame;
window.renderScreen = renderScreen;
