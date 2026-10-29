// NOORIYAH brand film — core: timing, easing, palette, typography, compositing.
'use strict';

const W = 1080, H = 1920, FPS = 30;
const DURATION = 78.5; // voice-over is 75.68 s, plus a clean final hold

const C = {
  forestDeep: '#062a1d',
  forest: '#0a3d2b',
  logoGreen: '#09784e', // sampled from the supplied logo background (9,120,78)
  botanical: '#7fa36a',
  lime: '#8cc63f',
  ivory: '#f7f1e3',
  cream: '#efe4cc',
  kraft: '#d6c09a',
  earth: '#6b4a2f',
  earthDark: '#3a2718',
  honey: '#e0a846',
  honeyDeep: '#b8771c',
  gold: '#e3b765',
};

// ---------- math ----------
const clamp = (v, a = 0, b = 1) => Math.min(b, Math.max(a, v));
const lerp = (a, b, t) => a + (b - a) * t;
const inv = (a, b, v) => clamp((v - a) / (b - a));
const easeInOut = t => t < .5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
const easeOut = t => 1 - Math.pow(1 - t, 3);
const easeOutQuint = t => 1 - Math.pow(1 - t, 5);
const easeIn = t => t * t * t;
const easeInOutSine = t => -(Math.cos(Math.PI * t) - 1) / 2;
// eased progress of t through [a, b]
const ep = (t, a, b, f = easeInOut) => f(inv(a, b, t));

function rng(seed) { // mulberry32
  return function () {
    seed |= 0; seed = seed + 0x6D2B79F5 | 0;
    let t = Math.imul(seed ^ seed >>> 15, 1 | seed);
    t = t + Math.imul(t ^ t >>> 7, 61 | t) ^ t;
    return ((t ^ t >>> 14) >>> 0) / 4294967296;
  };
}

// keyframe track: [[t, value], ...] with per-segment easing
function track(keys, t, f = easeInOut) {
  if (t <= keys[0][0]) return keys[0][1];
  for (let i = 0; i < keys.length - 1; i++) {
    const [t0, v0] = keys[i], [t1, v1] = keys[i + 1];
    if (t <= t1) {
      const p = (keys[i + 1][2] || f)(inv(t0, t1, t));
      if (Array.isArray(v0)) return v0.map((v, k) => lerp(v, v1[k], p));
      return lerp(v0, v1, p);
    }
  }
  return keys[keys.length - 1][1];
}

function hexA(hex, a) {
  const n = parseInt(hex.slice(1), 16);
  return `rgba(${n >> 16 & 255},${n >> 8 & 255},${n & 255},${a})`;
}
function mix(h1, h2, t) {
  const a = parseInt(h1.slice(1), 16), b = parseInt(h2.slice(1), 16);
  const r = Math.round(lerp(a >> 16 & 255, b >> 16 & 255, t));
  const g = Math.round(lerp(a >> 8 & 255, b >> 8 & 255, t));
  const bl = Math.round(lerp(a & 255, b & 255, t));
  return '#' + ((1 << 24) | (r << 16) | (g << 8) | bl).toString(16).slice(1);
}

function makeCanvas(w, h) {
  const c = document.createElement('canvas');
  c.width = w; c.height = h;
  return c;
}

// ---------- typography (real text, shaped by the browser; never scaled non-uniformly) ----------
const F = {
  serif: (wt, px) => `${wt} ${px}px "Noto Serif Bengali"`,
  sans: (wt, px) => `${wt} ${px}px "Hind Siliguri"`,
};

// segs: string or [[text, color], ...]
function segWidth(g, segs) {
  return segs.reduce((s, [tx]) => s + g.measureText(tx).width, 0);
}

/**
 * Animated text line with a mask reveal (rises out of a clip), fades out upward.
 * o: {x, y, font, color, align, tIn, tOut, dur, rise, shadow, alpha}
 */
function textLine(g, line, t, o) {
  const tIn = o.tIn, tOut = o.tOut ?? 1e9, dur = o.dur ?? .8;
  if (t < tIn || t > tOut + .6) return;
  const pin = easeOutQuint(inv(tIn, tIn + dur, t));
  const pout = easeInOut(inv(tOut, tOut + .55, t));
  const segs = typeof line === 'string' ? [[line, o.color || C.ivory]] : line;
  g.save();
  g.font = o.font;
  g.textBaseline = 'alphabetic';
  const w = segWidth(g, segs);
  const px = parseFloat(o.font.match(/(\d+)px/)[1]);
  let x = o.x;
  if ((o.align || 'center') === 'center') x -= w / 2;
  else if (o.align === 'right') x -= w;
  const rise = (o.rise ?? .55) * px;
  // mask box generous enough for Bengali matras and descenders
  g.beginPath();
  g.rect(x - 40, o.y - px * 1.25 - pout * px * .6, w + 80, px * 1.75);
  g.clip();
  g.globalAlpha = (o.alpha ?? 1) * pin * (1 - pout);
  const yy = o.y + (1 - pin) * rise - pout * px * .35;
  if (o.shadow !== false) {
    g.shadowColor = o.shadowColor || 'rgba(0,0,0,0.38)';
    g.shadowBlur = o.shadowBlur ?? 28;
    g.shadowOffsetY = 4;
  }
  for (const [tx, col] of segs) {
    g.fillStyle = col;
    g.fillText(tx, x, yy);
    x += g.measureText(tx).width;
  }
  g.restore();
}

// thin accent line drawn on from its center (or left)
function accentLine(g, x, y, len, t, tIn, tOut, o = {}) {
  const p = ep(t, tIn, tIn + (o.dur ?? .9), easeOutQuint) * (1 - ep(t, tOut ?? 1e9, (tOut ?? 1e9) + .5));
  if (p <= 0) return;
  g.save();
  g.strokeStyle = o.color || hexA(C.gold, .9);
  g.lineWidth = o.width || 2;
  g.lineCap = 'round';
  g.beginPath();
  if (o.fromLeft) { g.moveTo(x, y); g.lineTo(x + len * p, y); }
  else { g.moveTo(x - len / 2 * p, y); g.lineTo(x + len / 2 * p, y); }
  g.stroke();
  // small diamond at center, echoing the logo's star accents
  if (o.diamond) {
    const s = 7 * easeOut(p);
    const cx = o.fromLeft ? x : x;
    g.fillStyle = o.color || C.gold;
    g.beginPath();
    g.moveTo(cx, y - s); g.lineTo(cx + s, y); g.lineTo(cx, y + s); g.lineTo(cx - s, y); g.closePath();
    g.fill();
  }
  g.restore();
}

// soft gradient band behind text for legibility over imagery
function scrim(g, y0, y1, alpha, color = '#03140d') {
  if (alpha <= 0) return;
  const gr = g.createLinearGradient(0, y0, 0, y1);
  gr.addColorStop(0, hexA(color, 0));
  gr.addColorStop(.35, hexA(color, alpha));
  gr.addColorStop(.65, hexA(color, alpha));
  gr.addColorStop(1, hexA(color, 0));
  g.fillStyle = gr;
  g.fillRect(0, y0, W, y1 - y0);
}

// ---------- film finishing: grain + vignette ----------
let grainTiles = [];
function buildGrain() {
  const r = rng(7);
  for (let k = 0; k < 6; k++) {
    const c = makeCanvas(540, 960), g = c.getContext('2d');
    const id = g.createImageData(540, 960);
    for (let i = 0; i < id.data.length; i += 4) {
      const v = 128 + (r() + r() + r() - 1.5) * 90;
      id.data[i] = id.data[i + 1] = id.data[i + 2] = v;
      id.data[i + 3] = 255;
    }
    g.putImageData(id, 0, 0);
    grainTiles.push(c);
  }
}
function finish(g, t, o = {}) {
  // vignette
  const v = g.createRadialGradient(W / 2, H * .48, H * .28, W / 2, H * .5, H * .78);
  v.addColorStop(0, 'rgba(0,0,0,0)');
  v.addColorStop(1, `rgba(3,10,6,${o.vignette ?? .42})`);
  g.fillStyle = v;
  g.fillRect(0, 0, W, H);
  // grain
  const tile = grainTiles[Math.floor(t * FPS) % grainTiles.length];
  g.save();
  g.globalCompositeOperation = 'overlay';
  g.globalAlpha = o.grain ?? .07;
  g.imageSmoothingEnabled = false;
  g.drawImage(tile, 0, 0, W, H);
  g.restore();
}

// ---------- logo (original artwork, uniform scale only, composited as its own top layer) ----------
let LOGO = null;
function drawLogo(g, cx, cy, width, alpha = 1, reveal = 1) {
  const s = width / LOGO.width;
  const w = LOGO.width * s, h = LOGO.height * s;
  if (alpha <= 0 || reveal <= 0) return;
  g.save();
  g.globalAlpha = alpha;
  if (reveal < 1) { // circular iris reveal (a transition mask, artwork itself untouched)
    g.beginPath();
    g.arc(cx, cy, Math.hypot(w, h) / 2 * reveal, 0, Math.PI * 2);
    g.clip();
  }
  g.imageSmoothingQuality = 'high';
  g.drawImage(LOGO, cx - w / 2, cy - h / 2, w, h);
  g.restore();
}
// background that is exactly the logo's own green around it, falling off to deep forest green
function logoField(g, cx, cy, inner, outer, edge = C.forest) {
  g.fillStyle = edge;
  g.fillRect(0, 0, W, H);
  const r = g.createRadialGradient(cx, cy, inner, cx, cy, outer);
  r.addColorStop(0, C.logoGreen);
  r.addColorStop(1, edge);
  g.fillStyle = r;
  g.beginPath(); g.arc(cx, cy, outer, 0, Math.PI * 2); g.fill();
  g.fillStyle = C.logoGreen;
  g.beginPath(); g.arc(cx, cy, inner, 0, Math.PI * 2); g.fill();
}
