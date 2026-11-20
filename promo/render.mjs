// Renders sizzle.html to out/sizzle.mp4, one capture per frame, with the soundtrack audio.mjs
// synthesizes from the cues the page schedules.
//
//   node render.mjs                  the whole video, drawn by one worker per core
//   node render.mjs --jobs 2         ...or by this many
//   node render.mjs --still 2.5,12.8 single frames, as out/still-<t>.png
//   node render.mjs --screens        the redrawn phone screens alone, at the phone's 1080x2424, as
//                                    out/screen-<name>.png, to hold against the store screenshots
//   node render.mjs --audio          the soundtrack alone, and its buses, as out/stem-*.wav
//   node render.mjs --thumbnail      the YouTube thumbnail, 1280x720, as out/thumbnail.jpg
//
// ffmpeg is taken from $FFMPEG, else from the PATH, and needs libx264. Android Studio, for the
// Pixel 9a frame and the Inter and JetBrains Mono fonts, is looked for under $ANDROID_STUDIO, else
// /opt/android/studio.

import fs from 'node:fs';
import http from 'node:http';
import os from 'node:os';
import path from 'node:path';
import { spawn, spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright';
import { synthesize } from './audio.mjs';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '..');
const res = path.join(root, 'share/src/main/res');
const out = path.join(here, 'out');
const studio = process.env.ANDROID_STUDIO ?? '/opt/android/studio';

function fail(message) {
  console.error(`render.mjs: ${message}`);
  process.exit(1);
}

// ---------------------------------------------------------------------------------------------
// what the page draws from the app itself: its dark color scheme, its English strings, the two
// vector icons and the demo link, so a change to any of them reaches the video at its next render

function colors() {
  const src = fs.readFileSync(path.join(root, 'share/src/main/java/com/banasiak/android/simpleshare/ui/theme/Color.kt'), 'utf8');
  const scheme = {};
  for (const [, name, argb] of src.matchAll(/val md_theme_dark_(\w+) = Color\(0xFF([0-9A-Fa-f]{6})\)/g)) scheme[name] = `#${argb.toUpperCase()}`;
  for (const name of ['primary', 'onPrimary', 'primaryContainer', 'secondary', 'tertiary', 'onSurface', 'onSurfaceVariant', 'surfaceVariant']) {
    if (!scheme[name]) fail(`no md_theme_dark_${name} in Color.kt`);
  }
  return scheme;
}

function decodeString(s) {
  return s
    .replace(/\\'/g, "'")
    .replace(/\\"/g, '"')
    .replace(/&#x([0-9A-Fa-f]+);/g, (_, h) => String.fromCodePoint(parseInt(h, 16)))
    .replace(/&#(\d+);/g, (_, d) => String.fromCodePoint(Number(d)))
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&apos;/g, "'")
    .replace(/&amp;/g, '&');
}

function strings() {
  const src = fs.readFileSync(path.join(res, 'values/strings.xml'), 'utf8');
  const all = {};
  for (const [, name, value] of src.matchAll(/<string name="(\w+)">([^<]*)<\/string>/g)) all[name] = decodeString(value);
  const wanted = [
    'title_activity_sanitize', 'query_parameters', 'sanitized_url', 'hint_decode_short_url', 'hint_paste_text_here',
    'remove_tracking', 'feeling_lucky', 'button_share', 'button_copy', 'button_open',
  ];
  const picked = {};
  for (const name of wanted) {
    if (all[name] === undefined) fail(`no string ${name} in values/strings.xml`);
    picked[name] = all[name];
  }
  return picked;
}

// the paths of a vector drawable, each with the alpha it is filled at
function vector(name) {
  const src = fs.readFileSync(path.join(res, `drawable/${name}.xml`), 'utf8');
  const paths = [...src.matchAll(/<path([^>]*)\/>/g)].map(([, attrs]) => ({
    d: attrs.match(/android:pathData="([^"]+)"/)?.[1],
    alpha: Number(attrs.match(/android:fillAlpha="([^"]+)"/)?.[1] ?? 1),
  }));
  if (!paths.length || paths.some((p) => !p.d)) fail(`could not read the paths of drawable/${name}.xml`);
  return paths;
}

// the Feeling Lucky entry whose short link the video expands
function demoText() {
  const src = fs.readFileSync(path.join(root, 'share/src/main/java/com/banasiak/android/simpleshare/common/Constants.kt'), 'utf8');
  const entry = [...src.matchAll(/"([^"]*https:\/\/a\.co\/d\/0ZV7xf0)"/g)][0];
  if (!entry) fail('the demo link a.co/d/0ZV7xf0 is no longer in Constants.DEMO_URL_LIST');
  return entry[1].replace(/\\n/g, '\n');
}

// every values directory with a strings.xml is a language the app speaks
function languages() {
  return fs.readdirSync(res).filter((d) => /^values(-|$)/.test(d) && fs.existsSync(path.join(res, d, 'strings.xml'))).length;
}

function appData() {
  return { colors: colors(), strings: strings(), icons: { sanitize: vector('sanitize'), cloud: vector('cloud_download') }, demo: demoText(), languages: languages() };
}

// ---------------------------------------------------------------------------------------------
// the page's files, and nothing else: the server answers only these paths

function deviceArt() {
  const find = spawnSync('find', [studio, '-type', 'd', '-path', '*/device-art-resources/pixel_9a', '-print', '-quit'], { encoding: 'utf8' });
  const dir = find.stdout.trim();
  if (!dir) fail(`no pixel_9a device art under ${studio}: set ANDROID_STUDIO`);
  return dir;
}

function routes() {
  const fonts = path.join(studio, 'jbr/lib/fonts');
  // the fonts Android Studio's previews draw with, which are Android's own
  const android = path.join(studio, 'plugins/design-tools/resources/layoutlib/data/fonts');
  const art = deviceArt();
  const table = {
    '/sizzle.html': path.join(here, 'sizzle.html'),
    '/sizzle.js': path.join(here, 'sizzle.js'),
    '/screens.js': path.join(here, 'screens.js'),
    '/fonts/roboto.ttf': path.join(android, 'Roboto-Regular.ttf'),
    '/fonts/emoji.ttf': path.join(android, 'NotoColorEmoji.ttf'),
    '/fonts/inter-400.otf': path.join(fonts, 'Inter-Regular.otf'),
    '/fonts/inter-600.otf': path.join(fonts, 'Inter-SemiBold.otf'),
    '/fonts/mono-500.ttf': path.join(fonts, 'JetBrainsMono-Medium.ttf'),
    '/art/back.webp': path.join(art, 'back.webp'),
    '/art/mask.webp': path.join(art, 'mask.webp'),
  };
  for (const [url, file] of Object.entries(table)) if (!fs.existsSync(file)) fail(`${file} is missing, for ${url}: run npm ci, or set ANDROID_STUDIO`);
  return table;
}

const TYPES = {
  '.html': 'text/html',
  '.js': 'text/javascript',
  '.woff2': 'font/woff2',
  '.otf': 'font/otf',
  '.ttf': 'font/ttf',
  '.webp': 'image/webp',
};

function serve() {
  const table = routes();
  const app = JSON.stringify(appData());
  const server = http.createServer((req, response) => {
    const url = new URL(req.url, 'http://localhost').pathname;
    if (url === '/app.json') {
      response.writeHead(200, { 'content-type': 'application/json' });
      response.end(app);
      return;
    }
    const file = table[url];
    if (!file) {
      response.writeHead(404);
      response.end();
      return;
    }
    response.writeHead(200, { 'content-type': TYPES[path.extname(file)] ?? 'application/octet-stream' });
    fs.createReadStream(file).pipe(response);
  });
  return new Promise((resolve) => server.listen(0, '127.0.0.1', () => resolve(server)));
}

// ---------------------------------------------------------------------------------------------
// encoding

const FFMPEG = process.env.FFMPEG ?? 'ffmpeg';

// YouTube plays everything back at -14 LUFS, so the soundtrack is set there, in two passes: the
// first measures it and the second applies one gain, where a single pass would ride the level
function loudness(wav) {
  const probe = spawnSync(FFMPEG, ['-hide_banner', '-i', wav, '-af', 'loudnorm=I=-14:TP=-1.5:LRA=11:print_format=json', '-f', 'null', '-'], { encoding: 'utf8' });
  const json = probe.stderr.slice(probe.stderr.lastIndexOf('{'));
  const m = JSON.parse(json);
  return `loudnorm=I=-14:TP=-1.5:LRA=11:linear=true:measured_I=${m.input_i}:measured_TP=${m.input_tp}:measured_LRA=${m.input_lra}:measured_thresh=${m.input_thresh}:offset=${m.target_offset}`;
}

function encoder(file, fps, audio) {
  const args = [
    '-y', '-loglevel', 'error',
    '-f', 'image2pipe', '-framerate', String(fps), '-i', '-',
    '-i', audio,
    // the captures are sRGB, and without an explicit matrix the conversion is BT.601, which HD players do not assume
    '-vf', 'scale=out_color_matrix=bt709:out_range=tv',
    '-c:v', 'libx264', '-preset', 'slow', '-crf', '16', '-profile:v', 'high', '-pix_fmt', 'yuv420p',
    '-colorspace', 'bt709', '-color_primaries', 'bt709', '-color_trc', 'bt709', '-color_range', 'tv',
    '-af', loudness(audio), '-ar', '48000',
    '-c:a', 'aac', '-b:a', '256k',
    '-movflags', '+faststart', '-shortest',
    file,
  ];
  return spawn(FFMPEG, args, { stdio: ['pipe', 'inherit', 'inherit'] });
}

// Playwright's own screenshot compresses its PNG as hard as zlib allows, which costs many times
// what drawing the frame does; Chromium's fast PNG is just as lossless
async function capture(cdp, options = { format: 'png', optimizeForSpeed: true }) {
  const { data } = await cdp.send('Page.captureScreenshot', options);
  return Buffer.from(data, 'base64');
}

async function openPage(browser, port, viewport = { width: 1920, height: 1080 }) {
  // a context apiece, so each worker gets a renderer process of its own
  const context = await browser.newContext({ viewport, deviceScaleFactor: 1 });
  const page = await context.newPage();
  const errors = [];
  page.on('pageerror', (e) => errors.push(e));
  page.on('console', (m) => console.log(`[page] ${m.text()}`));
  await page.goto(`http://127.0.0.1:${port}/sizzle.html`);
  const meta = await page.evaluate(() => window.setup());
  if (errors.length) throw errors[0];
  const cdp = await context.newCDPSession(page);
  const frame = async (t) => {
    await page.evaluate((time) => window.renderFrame(time), t);
    if (errors.length) throw errors[0];
    return capture(cdp);
  };
  const screen = async (name) => {
    await page.evaluate((n) => window.renderScreen(n), name);
    if (errors.length) throw errors[0];
    return capture(cdp);
  };
  // YouTube takes a thumbnail of up to 2 MB, which a PNG of the gradients can pass
  const thumbnail = async () => {
    await page.evaluate(() => window.renderThumbnail());
    if (errors.length) throw errors[0];
    return capture(cdp, { format: 'jpeg', quality: 92 });
  };
  return { meta, frame, screen, thumbnail };
}

function option(argv, name) {
  const i = argv.indexOf(name);
  return i >= 0 ? argv[i + 1] : null;
}

async function main() {
  const argv = process.argv.slice(2);
  const stills = option(argv, '--still')?.split(',').map(Number);
  const jobs = Math.max(1, Number(option(argv, '--jobs')) || os.availableParallelism());

  fs.mkdirSync(out, { recursive: true });
  const server = await serve();
  // Playwright turns Chromium's sandbox off unless asked
  const browser = await chromium.launch({ chromiumSandbox: true });
  try {
    const port = server.address().port;

    if (argv.includes('--screens')) {
      const page = await openPage(browser, port, { width: 1080, height: 2424 });
      for (const name of page.meta.screens) fs.writeFileSync(path.join(out, `screen-${name}.png`), await page.screen(name));
      console.log(`wrote ${page.meta.screens.map((n) => `out/screen-${n}.png`).join(', ')}`);
      return;
    }

    if (argv.includes('--thumbnail')) {
      const page = await openPage(browser, port, { width: 1280, height: 720 });
      fs.writeFileSync(path.join(out, 'thumbnail.jpg'), await page.thumbnail());
      console.log('wrote out/thumbnail.jpg');
      return;
    }

    const first = await openPage(browser, port);
    const { meta } = first;

    if (stills) {
      for (const t of stills) fs.writeFileSync(path.join(out, `still-${t.toFixed(2)}.png`), await first.frame(t));
      return;
    }

    if (argv.includes('--audio')) {
      // the soundtrack alone, with its buses beside it, for balancing the mix without re-rendering
      const parts = synthesize(meta.cues, meta.duration, meta.marks, { stems: true });
      for (const [name, data] of Object.entries(parts)) fs.writeFileSync(path.join(out, `stem-${name}.wav`), data);
      return;
    }

    const wav = path.join(out, 'soundtrack.wav');
    fs.writeFileSync(wav, synthesize(meta.cues, meta.duration, meta.marks));

    const frames = Math.round(meta.duration * meta.fps);
    const workers = [first, ...(await Promise.all(Array.from({ length: jobs - 1 }, () => openPage(browser, port))))];
    const enc = encoder(path.join(out, 'sizzle.mp4'), meta.fps, wav);
    const done = new Promise((resolve, reject) => {
      enc.on('close', (code) => (code ? reject(new Error(`ffmpeg exited with ${code}`)) : resolve()));
    });

    // the workers take interleaved frames and the encoder is fed strictly in order from whatever has
    // arrived. A worker more than two rounds ahead of the encoder waits, or a slow encode would
    // buffer the whole video's PNGs in memory; the worker owning the next frame is never the one
    // held back, so this cannot deadlock
    const ready = new Map();
    let next = 0;
    let writing = Promise.resolve();
    const started = Date.now();
    const flush = async () => {
      while (ready.has(next)) {
        const png = ready.get(next);
        ready.delete(next);
        next++;
        if (!enc.stdin.write(png)) await new Promise((r) => enc.stdin.once('drain', r));
        if (next % 60 === 0) process.stdout.write(`\rframe ${next}/${frames}  ${((Date.now() - started) / 1000).toFixed(0)}s`);
      }
    };
    await Promise.all(
      workers.map(async (w, k) => {
        for (let f = k; f < frames; f += jobs) {
          while (f - next >= 2 * jobs) await new Promise((r) => setTimeout(r, 5));
          ready.set(f, await w.frame(f / meta.fps));
          writing = writing.then(flush);
        }
      }),
    );
    await writing;
    enc.stdin.end();
    await done;
    const secs = ((Date.now() - started) / 1000).toFixed(0);
    process.stdout.write(`\rwrote out/sizzle.mp4 (${frames} frames, ${jobs} workers) in ${secs}s\n`);
  } finally {
    await browser.close();
    server.close();
  }
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
