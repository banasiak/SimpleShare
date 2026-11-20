// The promo video's soundtrack: a groove in E dorian at 100 bpm, arranged around the sections
// sizzle.js marks, with a sound under every cue it schedules. The voices are in synth.mjs.
//
// The harmony vamps between Em9 and A13 through the demos, steps up through Cmaj9 and D6/9 under the
// features, and lands on Em9 for the end card.

import { RATE, drop, echo, glass, hz, keys, kick, midi, pad, pop, room, shaker, snap, squeak, squelch, sub, swell, touch, wav } from './synth.mjs';

const CHORDS = {
  Em9: { root: 'E1', notes: ['G3', 'B3', 'D4', 'F#4'] },
  A13: { root: 'A1', notes: ['G3', 'C#4', 'F#4', 'B4'] },
  Cmaj9: { root: 'C2', notes: ['E3', 'G3', 'B3', 'D4'] },
  D69: { root: 'D2', notes: ['F#3', 'A3', 'B3', 'E4'] },
};

// E dorian, for the glass melody and the little runs over the cues
const SCALE = ['E', 'F#', 'G', 'A', 'B', 'C#', 'D'];
const degree = (d, octave = 5) => midi(`${SCALE[((d % 7) + 7) % 7]}${octave + Math.floor(d / 7)}`);

/**
 * The finished soundtrack, as a 48 kHz stereo float WAV. `marks` are the section starts the picture
 * is cut to (MARKS in sizzle.js). With `stems`, the buses before the mix too, keyed by name.
 */
export function synthesize(cues, duration, marks, { stems = false } = {}) {
  const N = Math.ceil(duration * RATE);
  const beat = marks.beat;
  const bar = 4 * beat;
  const step = beat / 4;
  const bus = () => [new Float32Array(N), new Float32Array(N)];
  const drums = bus();
  const music = bus(); // ducked under the kick
  const fx = bus();
  const roomSend = bus();
  const echoSend = bus();

  // places a voice at t, panned from -1 to 1, with sends to the room and the echo
  const place = (target, t, sig, gain = 1, pan = 0, wet = 0, echoes = 0) => {
    const start = Math.round(t * RATE);
    const l = Math.cos(((pan + 1) * Math.PI) / 4) * gain;
    const r = Math.sin(((pan + 1) * Math.PI) / 4) * gain;
    for (let i = 0; i < sig.length; i++) {
      const j = start + i;
      if (j < 0 || j >= N) continue;
      const v = sig[i];
      target[0][j] += v * l;
      target[1][j] += v * r;
      if (wet) {
        roomSend[0][j] += v * l * wet;
        roomSend[1][j] += v * r * wet;
      }
      if (echoes) {
        echoSend[0][j] += v * l * echoes;
        echoSend[1][j] += v * r * echoes;
      }
    }
  };

  const chordAt = (t) => {
    const b = Math.floor(t / bar + 1e-6);
    const brandBar = Math.round(marks.brand / bar);
    const featuresBar = Math.round(marks.features / bar);
    const endBar = Math.round(marks.end / bar);
    if (b === brandBar) return 'Cmaj9';
    if (b >= endBar) return 'Em9';
    if (b === featuresBar) return 'Cmaj9';
    if (b === featuresBar + 1) return 'D69';
    return b % 2 ? 'A13' : 'Em9';
  };

  // where the band plays: the full groove through the demos and features, but not in the hook, nor
  // in the first bar and a half of the short link, which builds back to the resolve
  const groove = (t) => t >= marks.yt - 1e-6 && t < marks.end - 1e-6 && !(t >= marks.short - 1e-6 && t < marks.resolve - 1e-6);
  const kicks = [];

  // ---- drums
  const kickSig = kick();
  for (let t = marks.yt; t < marks.end - 1e-6; t += step) {
    if (!groove(t)) continue;
    const s = Math.round((t - marks.yt) / step) % 16;
    if ([0, 7, 10].includes(s)) {
      place(drums, t, kickSig, s === 0 ? 1 : 0.8);
      kicks.push(t);
    }
    if (s === 4 || s === 12) place(drums, t, snap(Math.round(t * 100)), 0.55, 0.05, 0.3);
  }
  // a heartbeat under the rotting headline, lub and dub, every other beat until the sweep
  for (let t = marks.rot; t < marks.scrub - 1e-6; t += 2 * beat) {
    place(drums, t, kickSig, 0.55);
    place(drums, t + 0.19, kickSig, 0.35);
  }
  for (const t of [marks.brand, marks.end]) {
    place(drums, t, kickSig, 1.1);
    kicks.push(t);
  }
  // shakers: from the hook's second bar, quietly, then through everything until the end card
  for (let t = bar; t < marks.end - 1e-6; t += step) {
    const s = Math.round(t / step) % 4;
    const swing = s % 2 ? 0.022 : 0;
    const quiet = t < marks.yt ? 0.45 : t >= marks.short && t < marks.resolve ? 0.7 : 1;
    place(drums, t + swing, shaker(Math.round(t * 1000), s === 2 ? 1 : 0.4), (s === 2 ? 0.22 : 0.12) * quiet, s % 2 ? 0.35 : -0.25);
  }
  // a snap roll up to the resolve
  for (let t = marks.resolve - beat * 2; t < marks.resolve - 1e-6; t += step / 2) {
    const p = (t - (marks.resolve - beat * 2)) / (beat * 2);
    place(drums, t, snap(Math.round(t * 1000)), 0.08 + 0.32 * p, 0, 0.25);
  }

  // ---- bass: a syncopated line on the chord's root, its fifth and its octave
  const BASS = [[0, 0, 0.32], [3, 0, 0.1], [6, 7, 0.22], [10, 0, 0.28], [14, 12, 0.1]];
  for (let b = marks.yt; b < marks.end - 1e-6; b += bar) {
    for (const [s, up, len] of BASS) {
      const t = b + s * step;
      if (!groove(t)) continue;
      place(music, t, sub(hz(midi(CHORDS[chordAt(t)].root) + 12 + up), len), 0.42);
    }
  }
  // a long low note under the brand and the end card
  place(music, marks.brand, sub(hz(midi(CHORDS.Cmaj9.root) + 12), bar - 0.1, 0.7), 0.4);
  place(music, marks.end, sub(hz(midi(CHORDS.Em9.root) + 12), bar * 1.5, 0.9), 0.4);
  for (let t = marks.short; t < marks.resolve - 1e-6; t += bar) place(music, t, sub(hz(midi(CHORDS[chordAt(t)].root) + 12), bar - 0.1, 1.1), 0.32);

  // ---- electric piano: stabs through the groove, held chords where it stops
  const STABS = [[0, 0.5, 1], [7, 0.18, 0.7], [10, 0.4, 0.85]];
  for (let b = marks.yt; b < marks.end - 1e-6; b += bar) {
    for (const [s, len, vel] of STABS) {
      const t = b + s * step;
      if (!groove(t)) continue;
      CHORDS[chordAt(t)].notes.forEach((n, i) => place(music, t + i * 0.004, keys(hz(n), len, vel), 0.075, (i - 1.5) * 0.25, 0.25));
    }
  }
  for (const t of [marks.brand, marks.short, marks.short + bar, marks.end]) {
    const held = t === marks.end ? bar * 1.8 : bar - 0.1;
    CHORDS[chordAt(t)].notes.forEach((n, i) => place(music, t + i * 0.012, keys(hz(n), held, 0.9), 0.065, (i - 1.5) * 0.3, 0.4));
  }

  // ---- pad, all the way through, a chord a bar: a bed, under everything else
  for (let t = 0; t < duration - 1e-6; t += bar) {
    const len = t >= marks.end ? duration - t : bar;
    const hook = t < marks.brand;
    place(music, t, pad(CHORDS[chordAt(t)].notes.map((n) => midi(n) + 12), len, hook ? 0.85 : 1), hook ? 0.09 : 0.045, 0, 0.5);
  }

  // ---- the glass melody: a two-bar phrase over the demos, answered on the echo
  const PHRASE = [[0, 4], [3, 6], [6, 7], [10, 8], [12, 7], [14, 6], [16, 4], [19, 3], [22, 4], [26, 6], [28, 4]];
  for (let b = marks.yt + bar; b < marks.end - 1e-6; b += 2 * bar) {
    for (const [s, d] of PHRASE) {
      const t = b + s * step;
      if (!groove(t) || t >= marks.features) continue;
      place(music, t, glass(hz(degree(d, 4)), 0.3, 0.8), 0.06, ((s % 5) - 2) * 0.2, 0.35, 0.35);
    }
  }
  // the headline arriving, clean, on a soft chord
  CHORDS.Em9.notes.forEach((n, i) => place(music, 0.3 + i * 0.015, keys(hz(n), 1.6, 0.6), 0.06, (i - 1.5) * 0.3, 0.45));
  // the hook's first notes, before anything else plays
  for (const [t, d] of [[0.3, 4], [2.1, 6], [3.0, 7], [3.9, 8], [5.7, 7], [6.6, 6]]) place(music, t, glass(hz(degree(d, 4)), 0.5, 0.7), 0.08, 0, 0.5, 0.4);

  // ---- the cues
  const run = (t, degrees, gap, gain, octave = 5, wet = 0.45) =>
    degrees.forEach((d, i) => place(fx, t + i * gap, glass(hz(degree(d, octave)), 0.25), gain, (i / Math.max(1, degrees.length - 1)) * 0.8 - 0.4, wet, 0.25));
  for (const c of cues) {
    const seed = Math.round(c.t * 1000);
    switch (c.type) {
      case 'key':
        place(fx, c.t, touch(seed), 0.35, 0.3 * Math.sin(seed));
        break;
      case 'rot':
        // the headline going off: a squelch, and a cluster a semitone wide swelling under it
        place(fx, c.t, squelch(seed), 0.45, 0, 0.3);
        place(fx, c.t, pad(['F3', 'F#3', 'C4', 'C#4'], 2.2, 0.9), 0.09, 0, 0.5);
        break;
      case 'flag':
        // a wrong note, then its neighbor: something to look at
        place(fx, c.t, glass(hz(c.second ? 'F5' : 'F#5'), 0.2, 1.3), 0.09, c.second ? 0.3 : -0.3, 0.3);
        break;
      case 'scrub':
        place(fx, c.t, squeak(c.until - c.t + 0.05, seed), 0.3, 0, 0.25);
        place(fx, c.t, swell(c.until - c.t + 0.15, 800, 5000, seed, 0.5), 0.25, 0, 0.2);
        break;
      case 'pop':
        place(fx, c.t, pop(seed), 0.4, ((c.k % 5) - 2) * 0.3, 0.2);
        break;
      case 'sparkle':
        run(c.t, [7, 9, 11, 14], 0.05, 0.05, 5);
        break;
      case 'rise':
        place(fx, c.t, swell(c.until - c.t, 300, 7000, seed, 1), 0.3, 0, 0.3);
        break;
      case 'brand':
        place(fx, c.t, swell(1.2, 6000, 400, seed, 0.5), 0.2, 0, 0.4);
        run(c.t, [0, 4, 7, 9, 11], 0.06, 0.05, 5, 0.6);
        break;
      case 'twinkle':
        run(c.t, [14, 16], 0.07, 0.04, 5, 0.6);
        break;
      case 'whoosh':
        place(fx, c.t - 0.25, swell(0.5, c.soft ? 400 : 600, c.soft ? 1800 : 3500, seed, 0.5), c.soft ? 0.14 : 0.2, 0, 0.2);
        break;
      case 'sheet':
        place(fx, c.t, swell(0.32, 500, 2600, seed, 0.5), 0.14, 0.2, 0.15);
        break;
      case 'sheetDown':
        place(fx, c.t, swell(0.3, 2400, 500, seed, 0.5), 0.12, 0.2, 0.15);
        break;
      case 'expand':
        run(c.t + 0.04, [0, 2, 4], 0.045, 0.035, 5, 0.3);
        break;
      case 'tap':
        place(fx, c.t, touch(seed), 0.42, 0.25, 0.1);
        break;
      case 'check':
        place(fx, c.t, glass(hz(degree(4, 5)), 0.08), 0.06, 0.25, 0.2);
        place(fx, c.t + 0.06, glass(hz(degree(7, 5)), 0.1), 0.06, 0.25, 0.2);
        break;
      case 'uncheck':
        place(fx, c.t, glass(hz(degree(7, 5)), 0.08), 0.06, 0.25, 0.2);
        place(fx, c.t + 0.06, glass(hz(degree(4, 5)), 0.1), 0.06, 0.25, 0.2);
        break;
      case 'drop':
        place(fx, c.t, drop(hz(degree([4, 6, 7, 9][c.k], 5)) * 0.5, seed), 0.3, (c.k - 1.5) * 0.3, 0.35);
        break;
      case 'count':
        run(c.t, [4, 11], 0.09, 0.05, 5, 0.5);
        break;
      case 'lucky':
        run(c.t, [0, 2, 4, 7, 9], 0.04, 0.04, 6, 0.5);
        break;
      case 'load':
        // a quiet tick a sixteenth, rising, while the indicator runs
        for (let t = c.t; t < c.until - 1e-6; t += step) {
          const p = (t - c.t) / (c.until - c.t);
          place(fx, t, glass(hz(degree(Math.round(p * 6) + 7, 5)), 0.05, 0.6), 0.022 + 0.02 * p, Math.sin(t * 9) * 0.5, 0.3);
        }
        place(fx, c.t, swell(c.until - c.t, 300, 5000, seed, 1), 0.22, 0, 0.3);
        break;
      case 'resolve':
        run(c.t, [4, 7, 9, 11, 14], 0.05, 0.05, 5, 0.6);
        break;
      case 'chip':
        place(fx, c.t, glass(hz(degree([0, 2, 4, 6, 7][c.k] + 7, 4)), 0.3), 0.06, (c.k - 2) * 0.3, 0.45, 0.2);
        place(fx, c.t, pop(seed), 0.12, (c.k - 2) * 0.3);
        break;
      case 'final':
        place(fx, c.t - 1.2, swell(1.2, 300, 6000, seed, 1), 0.26, 0, 0.3);
        run(c.t, [0, 4, 7, 9, 11, 14], 0.07, 0.055, 5, 0.7);
        break;
      default:
        break;
    }
  }

  // the music bus dips under every kick and comes back within a beat
  const duck = new Float32Array(N).fill(1);
  for (const t of kicks) {
    const s = Math.round(t * RATE);
    for (let i = 0; i < 0.35 * RATE && s + i < N; i++) duck[s + i] = Math.min(duck[s + i], 1 - 0.45 * Math.exp(-i / RATE / 0.09));
  }

  const [rl, rr] = room(roomSend[0], roomSend[1], { decay: 2.6, damp: 0.45 });
  const [el, er] = echo(echoSend[0], echoSend[1], 3 * step, 0.38);
  const L = new Float32Array(N);
  const R = new Float32Array(N);
  let peak = 0;
  for (let i = 0; i < N; i++) {
    const t = i / RATE;
    const fade = 1 - Math.max(0, Math.min(1, (t - (duration - 0.9)) / 0.85));
    L[i] = (0.85 * drums[0][i] + music[0][i] * duck[i] + fx[0][i] + 0.6 * rl[i] + 0.5 * el[i]) * fade;
    R[i] = (0.85 * drums[1][i] + music[1][i] * duck[i] + fx[1][i] + 0.6 * rr[i] + 0.5 * er[i]) * fade;
    peak = Math.max(peak, Math.abs(L[i]), Math.abs(R[i]));
  }
  // a soft knee in place of a limiter, then scaled to just under full; ffmpeg sets the loudness
  const drive = 1.6 / peak;
  const ceiling = 1.6 / Math.sqrt(1 + 1.6 * 1.6);
  for (let i = 0; i < N; i++) {
    const l = L[i] * drive;
    const r = R[i] * drive;
    L[i] = (0.95 * l) / Math.sqrt(1 + l * l) / ceiling;
    R[i] = (0.95 * r) / Math.sqrt(1 + r * r) / ceiling;
  }
  if (!stems) return wav(L, R);
  return {
    mix: wav(L, R),
    drums: wav(...drums),
    music: wav(...music.map((ch) => ch.map((v, i) => v * duck[i]))),
    fx: wav(...fx),
    room: wav(rl, rr),
    echo: wav(el, er),
  };
}
