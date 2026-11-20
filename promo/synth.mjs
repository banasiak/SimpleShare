// The voices and effects the promo soundtrack is built from, synthesized a sample at a time at
// 48 kHz. Nothing is sampled. Every voice returns a mono Float32Array; audio.mjs places and pans
// them.

export const RATE = 48000;
const TAU = Math.PI * 2;

export const samples = (sec) => new Float32Array(Math.max(1, Math.round(sec * RATE)));

// xorshift, from -1 to 1, seeded so that every render is the same
export function noise(seed) {
  let s = (seed * 2654435761) >>> 0 || 1;
  return () => {
    s ^= s << 13;
    s >>>= 0;
    s ^= s >>> 17;
    s ^= s << 5;
    s >>>= 0;
    return s / 2147483648 - 1;
  };
}

const STEPS = { C: 0, D: 2, E: 4, F: 5, G: 7, A: 9, B: 11 };

// 'F#4' as a MIDI note number
export function midi(name) {
  const m = /^([A-G])(#|b)?(-?\d)$/.exec(name);
  if (!m) throw new Error(`not a note: ${name}`);
  return 12 * (Number(m[3]) + 1) + STEPS[m[1]] + (m[2] === '#' ? 1 : m[2] === 'b' ? -1 : 0);
}

export const hz = (note) => 440 * 2 ** (((typeof note === 'string' ? midi(note) : note) - 69) / 12);

// a topology-preserving state-variable filter, which stays stable however fast its cutoff moves
export function filter() {
  let s1 = 0;
  let s2 = 0;
  return (x, cutoff, q, mode = 'lp') => {
    const g = Math.tan((Math.PI * Math.min(cutoff, RATE * 0.45)) / RATE);
    const k = 1 / q;
    const a1 = 1 / (1 + g * (g + k));
    const a2 = g * a1;
    const a3 = g * a2;
    const v3 = x - s2;
    const v1 = a1 * s1 + a2 * v3;
    const v2 = s2 + a2 * s1 + a3 * v3;
    s1 = 2 * v1 - s1;
    s2 = 2 * v2 - s2;
    if (mode === 'lp') return v2;
    if (mode === 'bp') return v1;
    return x - k * v1 - v2;
  };
}

// the correction that takes the alias out of a sawtooth's reset
function blep(p, dt) {
  if (p < dt) {
    const x = p / dt;
    return x + x - x * x - 1;
  }
  if (p > 1 - dt) {
    const x = (p - 1) / dt;
    return x * x + x + x + 1;
  }
  return 0;
}

function saw(phase, dt) {
  return 2 * phase - 1 - blep(phase, dt);
}

// 1 until `hold`, then a straight fall to 0 over `fall` seconds
const gate = (t, hold, fall) => (t < hold ? 1 : Math.max(0, 1 - (t - hold) / fall));
const attack = (t, a) => (a > 0 ? Math.min(1, t / a) : 1);

// ---------------------------------------------------------------------------------------------
// pitched voices

// an electric piano: a 1:1 FM pair whose brightness falls away after the strike, with a short
// high tine on top
export function keys(f, dur, vel = 1) {
  const out = samples(dur + 1.4);
  let body = 0;
  let mod = 0;
  let tine = 0;
  for (let i = 0; i < out.length; i++) {
    const t = i / RATE;
    const index = vel * (1.3 * Math.exp(-t / 0.3) + 0.2);
    const m = Math.sin(TAU * mod) * index;
    const v = Math.sin(TAU * body + m) + 0.16 * vel * Math.sin(TAU * tine) * Math.exp(-t / 0.04);
    out[i] = v * attack(t, 0.003) * Math.exp(-t / 1.5) * gate(t, dur, 0.3) * (0.55 + 0.45 * vel);
    body = (body + f / RATE) % 1;
    mod = (mod + f / RATE) % 1;
    tine = (tine + (12.7 * f) / RATE) % 1;
  }
  return out;
}

// a struck glass: FM at an inharmonic ratio, bright for a moment, then a pure ring
export function glass(f, dur = 0.6, bright = 1) {
  const out = samples(dur + 1.6);
  let c = 0;
  let m = 0;
  for (let i = 0; i < out.length; i++) {
    const t = i / RATE;
    const index = bright * (2.4 * Math.exp(-t / 0.09) + 0.25);
    out[i] = Math.sin(TAU * c + index * Math.sin(TAU * m)) * attack(t, 0.0015) * Math.exp(-t / 0.75) * gate(t, dur + 0.6, 1);
    c = (c + f / RATE) % 1;
    m = (m + (2.756 * f) / RATE) % 1;
  }
  return out;
}

// a sub bass: a sine bent a little toward a triangle, so a phone's speaker has a harmonic to play.
// `decay` lets a long note die away rather than hold
export function sub(f, dur, decay = Infinity) {
  const out = samples(dur + 0.08);
  let p = 0;
  for (let i = 0; i < out.length; i++) {
    const t = i / RATE;
    const s = Math.sin(TAU * p);
    out[i] = (s + 0.22 * s * s * s - 0.1 * Math.sin(2 * TAU * p)) * attack(t, 0.006) * gate(t, dur, 0.06) * Math.exp(-t / decay);
    p = (p + f / RATE) % 1;
  }
  return out;
}

// a slow pad: two detuned sawtooths a note, through a lowpass that breathes
export function pad(notes, dur, open = 1) {
  const out = samples(dur + 1.2);
  const lp = filter();
  const voices = notes.flatMap((n) => [hz(n) * 2 ** (-7 / 1200), hz(n) * 2 ** (6 / 1200)]);
  const phase = voices.map((_, i) => (i * 0.37) % 1);
  for (let i = 0; i < out.length; i++) {
    const t = i / RATE;
    let v = 0;
    voices.forEach((f, k) => {
      v += saw(phase[k], f / RATE);
      phase[k] = (phase[k] + f / RATE) % 1;
    });
    const cutoff = open * (520 + 260 * Math.sin(TAU * 0.11 * t + 1.3));
    out[i] = (lp(v / voices.length, cutoff, 0.8) * attack(t, 0.7) * gate(t, dur, 1.1));
  }
  return out;
}

// ---------------------------------------------------------------------------------------------
// drums

export function kick() {
  const out = samples(0.5);
  const n = noise(3);
  let p = 0;
  for (let i = 0; i < out.length; i++) {
    const t = i / RATE;
    const f = 44 + 120 * Math.exp(-t / 0.03);
    out[i] = Math.sin(TAU * p) * Math.exp(-t / 0.26) + 0.35 * n() * Math.exp(-t / 0.0012);
    p = (p + f / RATE) % 1;
  }
  return out;
}

// a finger snap: a sharp band of noise with a little pitch in it
export function snap(seed) {
  const out = samples(0.25);
  const n = noise(seed);
  const bp = filter();
  for (let i = 0; i < out.length; i++) {
    const t = i / RATE;
    const burst = Math.exp(-t / 0.022) + 0.4 * (t > 0.007 ? Math.exp(-(t - 0.007) / 0.012) : 0);
    out[i] = bp(n(), 2300, 1.4, 'bp') * 2.2 * burst + 0.25 * Math.sin(TAU * 1750 * t) * Math.exp(-t / 0.008);
  }
  return out;
}

export function shaker(seed, accent = 1) {
  const out = samples(0.12);
  const n = noise(seed);
  const hp = filter();
  for (let i = 0; i < out.length; i++) {
    const t = i / RATE;
    out[i] = hp(n(), 7200, 0.7, 'hp') * Math.min(1, t / 0.012) * Math.exp(-Math.max(0, t - 0.012) / (0.026 + 0.014 * accent));
  }
  return out;
}

// ---------------------------------------------------------------------------------------------
// effects: air and water, to go with the soap

// noise through a band that travels from f0 to f1, swelling and falling away: risers and whooshes
export function swell(dur, f0, f1, seed, shape = 1) {
  const out = samples(dur);
  const n = noise(seed);
  const bp = filter();
  for (let i = 0; i < out.length; i++) {
    const p = i / out.length;
    const env = shape >= 1 ? p ** (2 * shape) * (1 - Math.max(0, p - 0.92) / 0.08) : Math.sin(Math.PI * p) ** 1.5;
    out[i] = bp(n(), f0 * (f1 / f0) ** p, 2.2, 'bp') * env * 1.6;
  }
  return out;
}

// a drop falling into water: a sine whose pitch leaps upward as it dies
export function drop(f0, seed) {
  const out = samples(0.18);
  const n = noise(seed);
  let p = 0;
  for (let i = 0; i < out.length; i++) {
    const t = i / RATE;
    const f = f0 * (1 + 1.9 * (1 - Math.exp(-t / 0.014)));
    out[i] = Math.sin(TAU * p) * attack(t, 0.001) * Math.exp(-t / 0.045) + 0.12 * n() * Math.exp(-t / 0.0015);
    p = (p + f / RATE) % 1;
  }
  return out;
}

// something wet giving way: noise through a resonant lowpass that sinks as it wobbles, over a low
// tone falling away
export function squelch(seed) {
  const out = samples(0.7);
  const n = noise(seed);
  const lp = filter();
  let p = 0;
  for (let i = 0; i < out.length; i++) {
    const t = i / RATE;
    const cutoff = 180 + 1400 * Math.exp(-t / 0.12) * (1 + 0.45 * Math.sin(TAU * 17 * t));
    const wet = lp(n(), cutoff, 6) * attack(t, 0.01) * Math.exp(-t / 0.16);
    out[i] = 0.9 * wet + 0.6 * Math.sin(TAU * p) * Math.exp(-t / 0.22);
    p = (p + (70 + 90 * Math.exp(-t / 0.07)) / RATE) % 1;
  }
  return out;
}

// a bubble bursting: a click, and a chirp falling
export function pop(seed) {
  const out = samples(0.08);
  const n = noise(seed);
  const bp = filter();
  const f0 = 1900 + 700 * n();
  let p = 0;
  for (let i = 0; i < out.length; i++) {
    const t = i / RATE;
    out[i] = 0.7 * Math.sin(TAU * p) * Math.exp(-t / 0.014) + bp(n(), 3200, 2, 'bp') * 1.5 * Math.exp(-t / 0.002);
    p = (p + (f0 * Math.exp(-t / 0.03)) / RATE) % 1;
  }
  return out;
}

// a fingertip on glass
export function touch(seed) {
  const out = samples(0.06);
  const n = noise(seed);
  const bp = filter();
  for (let i = 0; i < out.length; i++) {
    const t = i / RATE;
    out[i] = bp(n(), 4200, 2.5, 'bp') * 1.8 * Math.exp(-t / 0.0035) + 0.35 * Math.sin(TAU * 190 * t) * Math.exp(-t / 0.018);
  }
  return out;
}

// a rubber blade on glass: a squeak that wobbles as it drags
export function squeak(dur, seed) {
  const out = samples(dur);
  const n = noise(seed);
  const bp = filter();
  let p = 0;
  for (let i = 0; i < out.length; i++) {
    const t = i / RATE;
    const q = t / dur;
    const f = 760 + 380 * q + 90 * Math.sin(TAU * 27 * t) + 30 * n();
    const env = Math.min(1, t / 0.03) * Math.min(1, (dur - t) / 0.06);
    out[i] = bp(saw(p, f / RATE), 1900 + 600 * q, 3, 'bp') * env * 1.4;
    p = (p + f / RATE) % 1;
  }
  return out;
}

// ---------------------------------------------------------------------------------------------
// the room

// a feedback delay network: eight delays of unrelated lengths, mixed into one another through a
// Hadamard matrix, each losing its highs and a little level on every pass
export function room(inL, inR, { decay = 2.4, damp = 0.4, pre = 0.02 } = {}) {
  const lengths = [1171, 1433, 1601, 1867, 2069, 2341, 2687, 3001];
  const lines = lengths.map((n) => new Float32Array(n));
  const at = lengths.map(() => 0);
  const lows = lengths.map(() => 0);
  const gains = lengths.map((n) => 10 ** ((-3 * n) / (decay * RATE)));
  const delay = Math.round(pre * RATE);
  const N = inL.length;
  const L = new Float32Array(N);
  const R = new Float32Array(N);
  const o = new Float64Array(8);
  for (let i = 0; i < N; i++) {
    const xl = i >= delay ? inL[i - delay] : 0;
    const xr = i >= delay ? inR[i - delay] : 0;
    for (let k = 0; k < 8; k++) o[k] = lines[k][at[k]];
    L[i] = (o[0] + o[2] + o[4] + o[6]) * 0.5;
    R[i] = (o[1] + o[3] + o[5] + o[7]) * 0.5;
    // the Hadamard butterfly, in place
    for (let h = 1; h < 8; h *= 2) {
      for (let a = 0; a < 8; a += 2 * h) {
        for (let b = a; b < a + h; b++) {
          const x = o[b];
          const y = o[b + h];
          o[b] = x + y;
          o[b + h] = x - y;
        }
      }
    }
    for (let k = 0; k < 8; k++) {
      const v = o[k] / Math.SQRT2 / 2;
      lows[k] += (1 - damp) * (v - lows[k]);
      lines[k][at[k]] = (k % 2 ? xr : xl) * 0.5 + gains[k] * lows[k];
      at[k] = (at[k] + 1) % lengths[k];
    }
  }
  return [L, R];
}

// an echo that crosses from side to side
export function echo(inL, inR, time, feedback) {
  const d = Math.round(time * RATE);
  const N = inL.length;
  const L = new Float32Array(N);
  const R = new Float32Array(N);
  for (let i = 0; i < N; i++) {
    const fromR = i >= d ? R[i - d] : 0;
    const fromL = i >= d ? L[i - d] : 0;
    L[i] = (i >= d ? inR[i - d] : 0) + feedback * fromR;
    R[i] = (i >= d ? inL[i - d] : 0) + feedback * fromL;
  }
  return [L, R];
}

// 32-bit float stereo, so nothing is rounded before ffmpeg sets the loudness
export function wav(L, R) {
  const n = L.length;
  const buf = Buffer.alloc(44 + n * 8);
  buf.write('RIFF', 0);
  buf.writeUInt32LE(36 + n * 8, 4);
  buf.write('WAVE', 8);
  buf.write('fmt ', 12);
  buf.writeUInt32LE(16, 16);
  buf.writeUInt16LE(3, 20);
  buf.writeUInt16LE(2, 22);
  buf.writeUInt32LE(RATE, 24);
  buf.writeUInt32LE(RATE * 8, 28);
  buf.writeUInt16LE(8, 32);
  buf.writeUInt16LE(32, 34);
  buf.write('data', 36);
  buf.writeUInt32LE(n * 8, 40);
  for (let i = 0; i < n; i++) {
    buf.writeFloatLE(L[i], 44 + i * 8);
    buf.writeFloatLE(R[i], 48 + i * 8);
  }
  return buf;
}
