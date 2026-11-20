// The app's two screens, redrawn at the Pixel 9a's own 1080x2424 px and 2.625 px to the dp, from
// main/MainScreen.kt, sanitize/SanitizeScreen.kt and the Material 3 components they use. Each dp is
// rounded to whole pixels, as Compose does. README.md says how they were held against the store
// screenshots.
'use strict';

const SCREEN_W = 1080;
const SCREEN_H = 2424;
const DENSITY = 2.625;
const px = (dp) => Math.round(dp * DENSITY);
const sp = (v) => v * DENSITY;

// the app's color scheme leaves the surface containers unset, so Material's baseline dark values
// show through: measured on the screenshots
const SURFACE_LOW = '#1D1B20';
const SURFACE_HIGHEST = '#36343B';

// Roboto's hhea metrics; Material's type scale centers the glyphs in each line's height
const ROBOTO_ASC = 0.9277;
const ROBOTO_DESC = 0.2441;

const TYPE = {
  titleLarge: { size: sp(22), lh: sp(28), weight: 400, ls: 0 },
  bodyLarge: { size: sp(16), lh: sp(24), weight: 400, ls: sp(0.5) },
  bodySmall: { size: sp(12), lh: sp(16), weight: 400, ls: sp(0.4) },
  labelLarge: { size: sp(14), lh: sp(20), weight: 500, ls: sp(0.1) },
};

const SCREENS = (() => {
  let app = null;
  let C = null;
  const icons = {};

  function init(data) {
    app = data;
    C = data.colors;
    for (const [name, paths] of Object.entries(data.icons)) icons[name] = paths.map((p) => ({ path: new Path2D(p.d), alpha: p.alpha }));
  }

  // ---------------------------------------------------------------------------------------------
  // easing and springs, as Compose runs them

  function bezier(x1, y1, x2, y2) {
    return (x) => {
      if (x <= 0) return 0;
      if (x >= 1) return 1;
      let lo = 0;
      let hi = 1;
      let t = x;
      for (let i = 0; i < 30; i++) {
        t = (lo + hi) / 2;
        const bx = 3 * (1 - t) ** 2 * t * x1 + 3 * (1 - t) * t ** 2 * x2 + t ** 3;
        if (bx < x) lo = t;
        else hi = t;
      }
      return 3 * (1 - t) ** 2 * t * y1 + 3 * (1 - t) * t ** 2 * y2 + t ** 3;
    };
  }
  const fastOutSlowIn = bezier(0.4, 0, 0.2, 1);

  // the position of a spring released from 0 toward 1, `t` seconds in
  function spring(t, stiffness, damping) {
    if (t <= 0) return 0;
    const w = Math.sqrt(stiffness);
    if (damping >= 1) return 1 - (1 + w * t) * Math.exp(-w * t);
    const wd = w * Math.sqrt(1 - damping * damping);
    return 1 - Math.exp(-damping * w * t) * (Math.cos(wd * t) + ((damping * w) / wd) * Math.sin(wd * t));
  }
  // AnimatedVisibility's expandIn(), and animateContentSize's MediumBouncy, StiffnessLow
  const expandIn = (t) => spring(t, 400, 1);
  const bouncy = (t) => spring(t, 200, 0.5);

  // ---------------------------------------------------------------------------------------------
  // type

  function font(g, st) {
    g.font = `${st.weight} ${st.size}px Roboto, "Noto Color Emoji"`;
    g.letterSpacing = `${st.ls}px`;
  }

  function width(g, s, st) {
    g.save();
    font(g, st);
    const w = g.measureText(s).width;
    g.restore();
    return w;
  }

  // Android spaces letters by half the spacing either side of each glyph, the canvas by all of it
  // after, so a line starts half a spacing in
  function text(g, s, x, lineTop, st, color, align = 'left') {
    g.save();
    font(g, st);
    g.fillStyle = color;
    g.textBaseline = 'alphabetic';
    const w = g.measureText(s).width;
    const left = align === 'left' ? x : align === 'right' ? x - w : x - w / 2;
    g.textAlign = 'left';
    g.fillText(s, left + st.ls / 2, lineTop + st.lh / 2 + (st.size * (ROBOTO_ASC - ROBOTO_DESC)) / 2);
    g.restore();
  }

  // where Android's line breaker may break a link: after "//", before other punctuation, and either
  // side of '=' and '&'
  const PUNCT = '/.?#%-_~:,;@+!';
  function urlBreakBefore(s, i) {
    const c = s[i];
    const p = s[i - 1];
    if ('=&'.includes(c) || '=&'.includes(p)) return true;
    if (p === '/' && s[i - 2] === '/') return true;
    return PUNCT.includes(c) && !PUNCT.includes(p);
  }

  // greedy, as the text fields lay out: each line takes as much as fits
  function wrap(g, s, st, maxW) {
    const lines = [];
    for (const para of s.split('\n')) {
      const link = !para.includes(' ');
      const breaks = [];
      for (let i = 1; i < para.length; i++) if (link ? urlBreakBefore(para, i) : para[i - 1] === ' ') breaks.push(i);
      let start = 0;
      while (start < para.length) {
        let end = para.length;
        if (width(g, para.slice(start).trimEnd(), st) > maxW) {
          let fit = -1;
          for (const b of breaks) if (b > start && width(g, para.slice(start, b).trimEnd(), st) <= maxW) fit = b;
          if (fit < 0) {
            fit = start + 1;
            while (fit < para.length && width(g, para.slice(start, fit + 1), st) <= maxW) fit++;
          }
          end = fit;
        }
        lines.push(para.slice(start, end).trimEnd());
        start = end;
      }
    }
    return lines;
  }

  // ---------------------------------------------------------------------------------------------
  // shapes

  function icon(g, name, x, y, size, color, alpha = 1) {
    g.save();
    g.translate(x, y);
    g.scale(size / 24, size / 24);
    g.fillStyle = color;
    for (const p of icons[name]) {
      g.globalAlpha = alpha * p.alpha;
      g.fill(p.path);
    }
    g.restore();
  }

  function withAlpha(hex, a) {
    const n = parseInt(hex.slice(1), 16);
    return `rgba(${n >> 16}, ${(n >> 8) & 255}, ${n & 255}, ${a})`;
  }

  // a Material filled text field's container: square below, 4dp corners above, and its 1dp indicator
  function fieldBox(g, x, y, w, h) {
    g.fillStyle = SURFACE_HIGHEST;
    g.beginPath();
    g.roundRect(x, y, w, h, [px(4), px(4), 0, 0]);
    g.fill();
    g.fillStyle = C.onSurfaceVariant;
    g.fillRect(x, y + h - px(1), w, px(1));
  }

  // a press: the ripple spreading from the touch, clipped to what was pressed
  function ripple(g, press, x, y, w, h, r, color) {
    if (!press || press.t < 0 || press.t > 0.6) return;
    const grow = fastOutSlowIn(Math.min(1, press.t / 0.3));
    const fade = press.t < 0.25 ? 1 : 1 - (press.t - 0.25) / 0.35;
    g.save();
    g.beginPath();
    g.roundRect(x, y, w, h, r);
    g.clip();
    g.globalAlpha = 0.12 * Math.max(0, fade);
    g.fillStyle = color;
    g.beginPath();
    g.arc(press.x, press.y, Math.hypot(w, h) * (0.2 + 0.8 * grow), 0, Math.PI * 2);
    g.fill();
    g.restore();
  }

  const BUTTON_H = px(40);
  const BUTTON_PAD = px(24);

  function button(g, label, cx, y, container, content, press) {
    const w = width(g, label, TYPE.labelLarge) + 2 * BUTTON_PAD;
    g.fillStyle = container;
    g.beginPath();
    g.roundRect(cx - w / 2, y, w, BUTTON_H, BUTTON_H / 2);
    g.fill();
    ripple(g, press, cx - w / 2, y, w, BUTTON_H, BUTTON_H / 2, content);
    text(g, label, cx, y + (BUTTON_H - TYPE.labelLarge.lh) / 2, TYPE.labelLarge, content, 'center');
    return { x: cx - w / 2, y, w, h: BUTTON_H };
  }

  // ---------------------------------------------------------------------------------------------
  // the system's own parts: the status bar as demo mode draws it, and the gesture handle

  // SystemUI sets these in Google Sans, which isn't to hand, so Roboto is narrowed to its widths
  function condensed(g, s, x, baseline, font, squeeze, color) {
    g.save();
    g.font = font;
    g.letterSpacing = '0px';
    g.fillStyle = color;
    g.textBaseline = 'alphabetic';
    g.translate(x, baseline);
    g.scale(squeeze, 1);
    g.fillText(s, 0, 0);
    g.restore();
  }

  function statusBar(g) {
    const white = '#FFFFFF';
    condensed(g, '5:00', 93, 92, '500 46px Roboto', 0.9, white);
    condensed(g, 'LTE', 743, 91, '700 40px Roboto', 0.78, white);
    g.save();
    g.fillStyle = white;
    for (let i = 0; i < 4; i++) {
      const h = 10 + i * 6.3;
      g.beginPath();
      g.roundRect(812 + i * 10.6, 91 - h, 6.5, h, 3.25);
      g.fill();
    }
    g.strokeStyle = white;
    g.lineWidth = 5.5;
    g.lineCap = 'round';
    for (const r of [13, 22.5]) {
      g.beginPath();
      g.arc(891, 88, r, -Math.PI * 0.79, -Math.PI * 0.21);
      g.stroke();
    }
    g.beginPath();
    g.arc(891, 86.5, 4, 0, Math.PI * 2);
    g.fill();
    g.beginPath();
    g.roundRect(929, 60, 59, 31, 9);
    g.fill();
    g.beginPath();
    g.roundRect(991, 70, 4.5, 11, 2);
    g.fill();
    g.restore();
    condensed(g, '100', 935.5, 85.5, '700 30px Roboto', 0.86, '#333333');
  }

  function gestureHandle(g) {
    g.fillStyle = '#EDEDED';
    g.beginPath();
    g.roundRect(398, 2388, 284, 11, 5.5);
    g.fill();
  }

  // ---------------------------------------------------------------------------------------------
  // the launcher screen (MainScreen.kt), portrait

  const STATUS = 152; // the top inset, under the status bar and the camera
  const LOGO = px((SCREEN_W / DENSITY) * 0.5);
  const LOGO_TOP = STATUS + px(64);
  const FIELD_X = px(16);
  const FIELD_W = SCREEN_W - 2 * FIELD_X;
  const FIELD_TOP = LOGO_TOP + LOGO + px(64);
  // minLines 4, with the label floated above them
  const MAIN_FIELD_H = 2 * px(8) + sp(16) + 4 * sp(24);
  // a button's 48dp touch target, with the 40dp button centered in it
  const REMOVE_Y = FIELD_TOP + MAIN_FIELD_H + px(4) + sp(16) + px(16) + px(32) + (px(48) - BUTTON_H) / 2;
  const LUCKY_Y = REMOVE_Y + px(48) + px(16) + px(16);

  function mainLayout(g) {
    const remove = width(g, app.strings.remove_tracking, TYPE.labelLarge) + 2 * BUTTON_PAD;
    const lucky = width(g, app.strings.feeling_lucky, TYPE.labelLarge) + 2 * BUTTON_PAD;
    return {
      remove: { x: SCREEN_W / 2 - remove / 2, y: REMOVE_Y, w: remove, h: BUTTON_H },
      lucky: { x: SCREEN_W / 2 - lucky / 2, y: LUCKY_Y, w: lucky, h: BUTTON_H },
      field: { x: FIELD_X, y: FIELD_TOP, w: FIELD_W, h: MAIN_FIELD_H },
    };
  }

  function drawMain(g, m) {
    g.fillStyle = C.primaryContainer;
    g.fillRect(0, 0, SCREEN_W, SCREEN_H);
    icon(g, 'sanitize', (SCREEN_W - LOGO) / 2, LOGO_TOP, LOGO, C.primary);

    fieldBox(g, FIELD_X, FIELD_TOP, FIELD_W, MAIN_FIELD_H);
    const inset = FIELD_X + px(16);
    if (m.text) {
      text(g, app.strings.title_activity_sanitize, inset, FIELD_TOP + px(8), TYPE.bodySmall, C.onSurfaceVariant);
      const lines = wrap(g, m.text, TYPE.bodyLarge, FIELD_W - 2 * px(16)).slice(0, 4);
      lines.forEach((l, i) => text(g, l, inset, FIELD_TOP + px(8) + sp(16) + i * sp(24), TYPE.bodyLarge, C.onSurface));
    } else {
      text(g, app.strings.title_activity_sanitize, inset, FIELD_TOP + px(16), TYPE.bodyLarge, C.onSurfaceVariant);
    }
    text(g, app.strings.hint_paste_text_here, inset, FIELD_TOP + MAIN_FIELD_H + px(4), TYPE.bodySmall, C.onSurfaceVariant);

    if (m.text) {
      button(g, app.strings.remove_tracking, SCREEN_W / 2, REMOVE_Y, C.primary, C.onPrimary, m.press?.remove);
    } else {
      button(g, app.strings.remove_tracking, SCREEN_W / 2, REMOVE_Y, withAlpha(C.onSurface, 0.12), withAlpha(C.onSurface, 0.38));
    }
    button(g, app.strings.feeling_lucky, SCREEN_W / 2, LUCKY_Y, C.tertiary, C.onPrimary, m.press?.lucky);
  }

  // ---------------------------------------------------------------------------------------------
  // the share sheet (SanitizeScreen.kt): a ModalBottomSheet sized to its content

  const NAV = px(24); // the bottom inset, over the gesture handle
  const SIDE = px(8); // the column's own padding
  const HANDLE = 2 * px(22) + px(4);
  const TITLE = Math.ceil(sp(28)) + px(16);
  const HEADER = px(8) + Math.ceil(sp(20)) + px(4);
  const ROW = px(4) + px(56) + px(4);
  const CHECK_BLOCK = px(8) + px(48) + px(8);
  const URL_LINE = sp(24);
  const URL_PAD = px(16);
  const SUPPORT = px(4) + sp(16);
  const LOADING = px(2);
  const BUTTONS = px(16) + px(48) + px(16);

  // how far into its show animation the sheet is, `lift` 0 to 1, and how far each part has grown
  function sheetLayout(g, s) {
    const shown = s.rowsShown ?? 1;
    const header = s.rows.length ? HEADER * shown : 0;
    const rows = s.rows.length * ROW * shown;
    const field = 2 * URL_PAD + URL_LINE * s.urlLines;
    const content = HANDLE + TITLE + header + rows + HEADER + px(4) + field + SUPPORT + px(4) + LOADING + BUTTONS;
    const rest = SCREEN_H - NAV - content;
    const top = rest + (1 - (s.lift ?? 1)) * (content + NAV + 60);
    const rowsTop = top + HANDLE + TITLE + header;
    const fieldTop = rowsTop + rows + HEADER + px(4);
    const fieldBottom = fieldTop + field;
    const buttonsTop = fieldBottom + SUPPORT + px(4) + LOADING;
    const checks = s.rows.map((_, i) => ({ x: SCREEN_W - SIDE - CHECK_BLOCK / 2, y: rowsTop + i * ROW * shown + ROW * shown - ROW / 2 }));
    const labels = [app.strings.button_share, app.strings.button_copy, app.strings.button_open];
    const widths = labels.map((l) => width(g, l, TYPE.labelLarge) + 2 * BUTTON_PAD);
    const total = widths.reduce((a, b) => a + b + 2 * px(8), 0);
    let x = SCREEN_W / 2 - total / 2 + px(8);
    const buttons = widths.map((w) => {
      const b = { x, y: buttonsTop + px(16) + (px(48) - BUTTON_H) / 2, w, h: BUTTON_H };
      x += w + 2 * px(8);
      return b;
    });
    return {
      top, rowsTop, fieldTop, fieldBottom, buttonsTop, checks, buttons,
      cloud: { x: SCREEN_W - SIDE - px(4) - px(24), y: fieldTop + field / 2 },
    };
  }

  function checkbox(g, cx, cy, on, press) {
    const box = sp(20);
    const stroke = sp(2);
    const x = cx - box / 2;
    const y = cy - box / 2;
    if (press && press.t >= 0 && press.t < 0.5) {
      g.save();
      g.globalAlpha = 0.1 * (1 - press.t / 0.5);
      g.fillStyle = on > 0.5 ? C.primary : C.onSurface;
      g.beginPath();
      g.arc(cx, cy, px(20), 0, Math.PI * 2);
      g.fill();
      g.restore();
    }
    g.save();
    if (on > 0) {
      g.globalAlpha = Math.min(1, on * 2);
      g.fillStyle = C.primary;
      g.beginPath();
      g.roundRect(x, y, box, box, sp(2));
      g.fill();
      // the check draws itself from its left arm
      const pts = [[0.2, 0.5], [0.4, 0.7], [0.8, 0.3]];
      const along = Math.min(1, on * 1.5);
      g.strokeStyle = C.onPrimary;
      g.lineWidth = stroke;
      g.lineCap = 'square';
      g.lineJoin = 'miter';
      g.beginPath();
      g.moveTo(x + pts[0][0] * box, y + pts[0][1] * box);
      const first = Math.min(1, along / 0.36);
      g.lineTo(x + (pts[0][0] + (pts[1][0] - pts[0][0]) * first) * box, y + (pts[0][1] + (pts[1][1] - pts[0][1]) * first) * box);
      if (along > 0.36) {
        const second = (along - 0.36) / 0.64;
        g.lineTo(x + (pts[1][0] + (pts[2][0] - pts[1][0]) * second) * box, y + (pts[1][1] + (pts[2][1] - pts[1][1]) * second) * box);
      }
      g.stroke();
    }
    if (on < 1) {
      g.globalAlpha = 1 - Math.min(1, on * 2);
      g.strokeStyle = C.onSurfaceVariant;
      g.lineWidth = stroke;
      g.beginPath();
      g.roundRect(x + stroke / 2, y + stroke / 2, box - stroke, box - stroke, sp(2) - stroke / 2);
      g.stroke();
    }
    g.restore();
  }

  // after Material's indeterminate linear indicator: two lines chasing across a 1.75 s cycle
  const LINES = [
    { head: [0, 750, bezier(0.2, 0, 0.8, 1)], tail: [333, 850, bezier(0.4, 0, 1, 1)] },
    { head: [1000, 567, bezier(0, 0, 0.65, 1)], tail: [1267, 533, bezier(0.1, 0, 0.45, 1)] },
  ];
  function progress(g, y, t) {
    const x0 = SIDE;
    const w = SCREEN_W - 2 * SIDE;
    g.fillStyle = C.surfaceVariant;
    g.fillRect(x0, y, w, LOADING);
    const ms = (t * 1000) % 1750;
    g.fillStyle = C.secondary;
    for (const line of LINES) {
      const at = ([delay, dur, ease]) => ease(Math.min(1, Math.max(0, (ms - delay) / dur)));
      const head = at(line.head);
      const tail = at(line.tail);
      if (head > tail) g.fillRect(x0 + tail * w, y, (head - tail) * w, LOADING);
    }
  }

  function drawSheet(g, s) {
    const L = sheetLayout(g, s);
    const lift = s.lift ?? 1;
    g.fillStyle = `rgba(0, 0, 0, ${0.32 * Math.min(1, lift * 1.4)})`;
    g.fillRect(0, 0, SCREEN_W, SCREEN_H);
    if (lift <= 0) return L;

    g.fillStyle = SURFACE_LOW;
    g.beginPath();
    g.roundRect(0, L.top, SCREEN_W, SCREEN_H - L.top + 400, [px(28), px(28), 0, 0]);
    g.fill();

    g.fillStyle = C.onSurfaceVariant;
    g.beginPath();
    g.roundRect((SCREEN_W - px(32)) / 2, L.top + px(22), px(32), px(4), px(2));
    g.fill();
    text(g, app.strings.title_activity_sanitize, SCREEN_W / 2, L.top + HANDLE, TYPE.titleLarge, C.primary, 'center');

    // AnimatedVisibility's expandIn grows each part from its bottom end corner, clipped as it goes
    const shown = s.rowsShown ?? 1;
    const reveal = (x, y, w, h, draw) => {
      if (shown <= 0) return;
      g.save();
      g.beginPath();
      g.rect(x + w * (1 - shown), y + h * (1 - shown), w * shown, h * shown);
      g.clip();
      g.translate(0, -h * (1 - shown));
      draw();
      g.restore();
    };
    const headerTop = L.top + HANDLE + TITLE;
    if (s.rows.length) {
      const hw = width(g, app.strings.query_parameters, TYPE.labelLarge);
      reveal(SIDE, headerTop, hw, HEADER, () => text(g, app.strings.query_parameters, SIDE, headerTop + px(8), TYPE.labelLarge, C.onSurface));
    }
    s.rows.forEach((r, i) => {
      const top = L.rowsTop + i * ROW * shown;
      reveal(SIDE, top, SCREEN_W - 2 * SIDE, ROW, () => {
        const fx = SIDE + px(4);
        const fw = SCREEN_W - SIDE - CHECK_BLOCK - px(4) - fx;
        fieldBox(g, fx, top + px(4), fw, px(56));
        g.save();
        g.beginPath();
        g.rect(fx + px(16), top + px(4), fw - 2 * px(16), px(56));
        g.clip();
        text(g, r.name, fx + px(16), top + px(4) + px(8), TYPE.bodySmall, C.onSurfaceVariant);
        text(g, r.value, fx + px(16), top + px(4) + px(8) + sp(16), TYPE.bodyLarge, C.onSurface);
        g.restore();
        if (r.highlight) r.highlight(g, fx, top + px(4), fw, px(56));
        checkbox(g, L.checks[i].x, top + ROW / 2, r.on ?? (r.enabled ? 1 : 0), r.press);
      });
    });

    const suTop = L.fieldTop - px(4) - HEADER;
    text(g, app.strings.sanitized_url, SIDE, suTop + px(8), TYPE.labelLarge, C.onSurface);

    const fx = SIDE + px(4);
    const fw = SCREEN_W - 2 * fx;
    fieldBox(g, fx, L.fieldTop, fw, L.fieldBottom - L.fieldTop);
    g.save();
    g.beginPath();
    g.rect(fx, L.fieldTop, fw, L.fieldBottom - L.fieldTop - px(1));
    g.clip();
    const lines = wrap(g, s.url, TYPE.bodyLarge, fw - px(16) - px(48));
    const alpha = s.urlAlpha ?? 1;
    g.globalAlpha = alpha;
    lines.forEach((l, i) => text(g, l, fx + px(16), L.fieldTop + URL_PAD + i * URL_LINE, TYPE.bodyLarge, C.onSurface));
    g.restore();
    ripple(g, s.press?.cloud, L.cloud.x - px(24), L.cloud.y - px(24), px(48), px(48), px(24), C.onSurfaceVariant);
    icon(g, 'cloud', L.cloud.x - px(12), L.cloud.y - px(12), px(24), C.onSurfaceVariant, s.cloudEnabled === false ? 0.38 : 1);
    text(g, app.strings.hint_decode_short_url, fx + fw - px(16), L.fieldBottom + px(4), TYPE.bodySmall, C.onSurfaceVariant, 'right');
    if (s.loading !== undefined && s.loading !== null) progress(g, L.fieldBottom + SUPPORT + px(4), s.loading);

    const colors = [C.primary, C.primary, C.tertiary];
    const keys = ['share', 'copy', 'open'];
    L.buttons.forEach((b, i) => {
      g.fillStyle = colors[i];
      g.beginPath();
      g.roundRect(b.x, b.y, b.w, b.h, b.h / 2);
      g.fill();
      ripple(g, s.press?.[keys[i]], b.x, b.y, b.w, b.h, b.h / 2, C.onPrimary);
      text(g, [app.strings.button_share, app.strings.button_copy, app.strings.button_open][i], b.x + b.w / 2, b.y + (b.h - TYPE.labelLarge.lh) / 2, TYPE.labelLarge, C.onPrimary, 'center');
    });
    return L;
  }

  // the whole screen: the launcher, the sheet over it if there is one, and the system bars on top
  function drawScreen(g, st) {
    g.save();
    g.beginPath();
    g.rect(0, 0, SCREEN_W, SCREEN_H);
    g.clip();
    drawMain(g, st.main ?? {});
    let layout = null;
    if (st.sheet) layout = drawSheet(g, st.sheet);
    statusBar(g);
    gestureHandle(g);
    g.restore();
    return layout;
  }

  return { init, drawScreen, sheetLayout, mainLayout, wrap, width, expandIn, bouncy, fastOutSlowIn, bezier, TYPE };
})();
