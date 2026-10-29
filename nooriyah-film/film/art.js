// NOORIYAH brand film — illustration primitives (all unbranded, no invented packaging or labels).
'use strict';

function rrect(g, x, y, w, h, r) {
  r = Math.min(r, w / 2, h / 2);
  g.beginPath();
  g.moveTo(x + r, y);
  g.arcTo(x + w, y, x + w, y + h, r);
  g.arcTo(x + w, y + h, x, y + h, r);
  g.arcTo(x, y + h, x, y, r);
  g.arcTo(x, y, x + w, y, r);
  g.closePath();
}

// Contents palettes for shelf goods (generic food staples)
const GOODS = {
  honey: { c1: '#f2b84b', c2: '#9a5a12', kind: 'jar' },
  ghee: { c1: '#f3e2a8', c2: '#c9a85a', kind: 'jar' },
  mustardOil: { c1: '#e9b52a', c2: '#8f5d09', kind: 'bottle' },
  oliveOil: { c1: '#c9c457', c2: '#6c6a1c', kind: 'bottle' },
  lentil: { c1: '#e98a3c', c2: '#a24f17', kind: 'jar', grain: '#c2601f' },
  rice: { c1: '#f4efe2', c2: '#cfc6b0', kind: 'jar', grain: '#ffffff' },
  chili: { c1: '#c8442c', c2: '#7c1f12', kind: 'jar' },
  turmeric: { c1: '#f0b532', c2: '#b77714', kind: 'jar' },
  pickle: { c1: '#cf7a2a', c2: '#6e3510', kind: 'jar', grain: '#e9a74a' },
  seeds: { c1: '#7b6a4a', c2: '#3c2f1d', kind: 'jar', grain: '#a69067' },
  almond: { c1: '#b9814c', c2: '#6d4220', kind: 'jar', grain: '#d9a46c' },
  dates: { c1: '#6e3a1e', c2: '#2e140a', kind: 'jar', grain: '#8e4f2b' },
};

// A glass jar or bottle standing with its base centre at (x, y)
function vessel(g, x, y, w, h, goodsKey, seed = 1, o = {}) {
  const gd = GOODS[goodsKey];
  const r = rng(seed * 97 + 13);
  g.save();
  g.translate(x, y);
  // contact shadow
  const sh = g.createRadialGradient(0, 0, 2, 0, 0, w * .7);
  sh.addColorStop(0, 'rgba(0,0,0,0.45)');
  sh.addColorStop(1, 'rgba(0,0,0,0)');
  g.fillStyle = sh;
  g.save(); g.scale(1, .18); g.beginPath(); g.arc(0, 0, w * .7, 0, Math.PI * 2); g.fill(); g.restore();

  const kind = o.kind || gd.kind;
  let bodyTop;
  const body = () => {
    if (kind === 'bottle') {
      const nw = w * .3, sh2 = h * .58;
      g.beginPath();
      g.moveTo(-w / 2 + 10, 0);
      g.lineTo(w / 2 - 10, 0);
      g.quadraticCurveTo(w / 2, 0, w / 2, -10);
      g.lineTo(w / 2, -sh2);
      g.bezierCurveTo(w / 2, -sh2 - h * .14, nw / 2, -sh2 - h * .14, nw / 2, -h * .86);
      g.lineTo(nw / 2, -h * .9);
      g.lineTo(-nw / 2, -h * .9);
      g.lineTo(-nw / 2, -h * .86);
      g.bezierCurveTo(-nw / 2, -sh2 - h * .14, -w / 2, -sh2 - h * .14, -w / 2, -sh2);
      g.lineTo(-w / 2, -10);
      g.quadraticCurveTo(-w / 2, 0, -w / 2 + 10, 0);
      g.closePath();
      bodyTop = -h * .9;
    } else {
      rrect(g, -w / 2, -h * .86, w, h * .86, w * .16);
      bodyTop = -h * .86;
    }
  };
  // glass base tint
  body();
  g.fillStyle = 'rgba(220,235,225,0.10)';
  g.fill();
  // contents
  g.save();
  body(); g.clip();
  const level = o.level ?? (kind === 'bottle' ? .78 : .82);
  const ct = -h * level;
  const lg = g.createLinearGradient(-w / 2, 0, w / 2, 0);
  lg.addColorStop(0, gd.c2);
  lg.addColorStop(.42, gd.c1);
  lg.addColorStop(.6, gd.c1);
  lg.addColorStop(1, gd.c2);
  g.fillStyle = lg;
  g.fillRect(-w / 2, ct, w, -ct);
  if (gd.grain) { // granular contents
    g.fillStyle = gd.grain;
    for (let i = 0; i < w * h / 70; i++) {
      const gx = (r() - .5) * w, gy = ct + r() * (-ct);
      g.globalAlpha = .25 + r() * .5;
      g.beginPath(); g.ellipse(gx, gy, 2.4 + r() * 2.5, 1.8 + r() * 1.8, r() * 3, 0, Math.PI * 2); g.fill();
    }
    g.globalAlpha = 1;
  }
  // depth shading
  const vg = g.createLinearGradient(0, ct, 0, 0);
  vg.addColorStop(0, 'rgba(255,240,200,0.18)');
  vg.addColorStop(1, 'rgba(0,0,0,0.28)');
  g.fillStyle = vg;
  g.fillRect(-w / 2, ct, w, -ct);
  g.restore();
  // unprinted kraft label (no text, no brand)
  if (o.label !== false) {
    const lh = h * (kind === 'bottle' ? .26 : .3), ly = kind === 'bottle' ? -h * .38 : -h * .5;
    g.save();
    body(); g.clip();
    const kg = g.createLinearGradient(-w / 2, 0, w / 2, 0);
    kg.addColorStop(0, '#9c855f'); kg.addColorStop(.35, C.kraft); kg.addColorStop(.6, '#e2cfab'); kg.addColorStop(1, '#8d7653');
    g.fillStyle = kg;
    g.fillRect(-w / 2, ly, w, lh);
    g.fillStyle = 'rgba(80,58,30,0.35)';
    g.fillRect(-w * .26, ly + lh * .38, w * .52, 3);
    g.fillRect(-w * .18, ly + lh * .56, w * .36, 2);
    g.restore();
  }
  // glass highlights
  g.save();
  body(); g.clip();
  const hl = g.createLinearGradient(-w / 2, 0, w / 2, 0);
  hl.addColorStop(0, 'rgba(255,255,255,0)');
  hl.addColorStop(.16, 'rgba(255,255,255,0.0)');
  hl.addColorStop(.2, 'rgba(255,255,255,0.42)');
  hl.addColorStop(.25, 'rgba(255,255,255,0.0)');
  hl.addColorStop(.8, 'rgba(255,255,255,0.0)');
  hl.addColorStop(.85, 'rgba(255,255,255,0.16)');
  hl.addColorStop(.9, 'rgba(255,255,255,0)');
  g.fillStyle = hl;
  g.fillRect(-w / 2, bodyTop, w, -bodyTop);
  g.restore();
  body();
  g.strokeStyle = 'rgba(255,255,255,0.18)';
  g.lineWidth = 1.5;
  g.stroke();
  // lid / cap
  const lidH = kind === 'bottle' ? h * .1 : h * .13;
  const lidW = kind === 'bottle' ? w * .36 : w * .92;
  const lc = o.lid || (r() > .5 ? '#6b4a2f' : '#3d5a3a');
  const lgr = g.createLinearGradient(-lidW / 2, 0, lidW / 2, 0);
  lgr.addColorStop(0, mix(lc, '#000000', .45)); lgr.addColorStop(.45, mix(lc, '#ffffff', .12)); lgr.addColorStop(1, mix(lc, '#000000', .5));
  g.fillStyle = lgr;
  rrect(g, -lidW / 2, bodyTop - lidH + 2, lidW, lidH, 5);
  g.fill();
  if (kind !== 'bottle') {
    g.strokeStyle = 'rgba(0,0,0,0.25)'; g.lineWidth = 1.2;
    for (let i = 1; i < 4; i++) { g.beginPath(); g.moveTo(-lidW / 2 + 3, bodyTop - lidH + 2 + lidH * i / 4); g.lineTo(lidW / 2 - 3, bodyTop - lidH + 2 + lidH * i / 4); g.stroke(); }
  }
  g.restore();
}

// wooden plank (shelf board) seen slightly from above
function plank(g, x, y, w, th, depth, seed = 3) {
  const r = rng(seed);
  // top surface
  const tg = g.createLinearGradient(0, y - depth, 0, y);
  tg.addColorStop(0, '#5a3b22'); tg.addColorStop(1, '#8a6038');
  g.fillStyle = tg;
  g.fillRect(x, y - depth, w, depth);
  // front edge
  const fg = g.createLinearGradient(0, y, 0, y + th);
  fg.addColorStop(0, '#a87a4a'); fg.addColorStop(.2, '#7a5330'); fg.addColorStop(1, '#4a301b');
  g.fillStyle = fg;
  g.fillRect(x, y, w, th);
  // grain
  g.strokeStyle = 'rgba(40,22,10,0.28)';
  g.lineWidth = 1.2;
  for (let i = 0; i < 9; i++) {
    const yy = y + 4 + r() * (th - 8);
    g.beginPath(); g.moveTo(x, yy);
    for (let xx = x; xx <= x + w; xx += 80) g.lineTo(xx, yy + Math.sin(xx * .01 + i) * 2.5);
    g.stroke();
  }
  // shadow under shelf
  const sg = g.createLinearGradient(0, y + th, 0, y + th + 70);
  sg.addColorStop(0, 'rgba(0,0,0,0.5)'); sg.addColorStop(1, 'rgba(0,0,0,0)');
  g.fillStyle = sg;
  g.fillRect(x, y + th, w, 70);
}

// leaf in the spirit of the logo's two leaves (organic, pointed tip)
function leaf(g, x, y, len, ang, o = {}) {
  const wd = len * (o.width ?? .42);
  g.save();
  g.translate(x, y);
  g.rotate(ang);
  const lg = g.createLinearGradient(0, -wd / 2, len, wd / 2);
  lg.addColorStop(0, o.c1 || '#9fd34f');
  lg.addColorStop(1, o.c2 || '#5c9e2e');
  g.fillStyle = lg;
  g.beginPath();
  g.moveTo(0, 0);
  g.bezierCurveTo(len * .25, -wd * .95, len * .78, -wd * .72, len, 0);
  g.bezierCurveTo(len * .72, wd * .62, len * .25, wd * .78, 0, 0);
  g.fill();
  if (o.vein !== false) {
    g.strokeStyle = o.veinColor || 'rgba(255,255,255,0.22)';
    g.lineWidth = Math.max(1, len * .012);
    g.beginPath(); g.moveTo(len * .04, 0); g.quadraticCurveTo(len * .5, -wd * .08, len * .94, 0); g.stroke();
  }
  g.restore();
}

// ceramic bowl seen at a 3/4 angle. Returns rim ellipse info. (x,y) = rim centre
function bowl(g, x, y, rw, depthPx, o = {}) {
  const rh = rw * (o.tilt ?? .32);
  const col = o.color || '#efe6d4';
  // shadow
  const sg = g.createRadialGradient(x, y + depthPx, 4, x, y + depthPx, rw * 1.2);
  sg.addColorStop(0, 'rgba(0,0,0,0.5)'); sg.addColorStop(1, 'rgba(0,0,0,0)');
  g.save(); g.translate(x, y + depthPx * .98); g.scale(1, .22); g.translate(-x, -(y + depthPx * .98));
  g.fillStyle = sg; g.beginPath(); g.arc(x, y + depthPx, rw * 1.2, 0, Math.PI * 2); g.fill(); g.restore();
  // outer body
  g.beginPath();
  g.ellipse(x, y, rw, rh, 0, 0, Math.PI);
  g.bezierCurveTo(x - rw, y + depthPx * .9, x - rw * .45, y + depthPx, x, y + depthPx);
  g.bezierCurveTo(x + rw * .45, y + depthPx, x + rw, y + depthPx * .9, x + rw, y);
  g.closePath();
  const bg = g.createLinearGradient(x - rw, 0, x + rw, 0);
  bg.addColorStop(0, mix(col, '#000000', .55));
  bg.addColorStop(.35, mix(col, '#ffffff', .1));
  bg.addColorStop(.55, col);
  bg.addColorStop(1, mix(col, '#000000', .6));
  g.fillStyle = bg;
  g.fill();
  if (o.band) { // painted rim band
    g.save(); g.clip();
    g.strokeStyle = o.band; g.lineWidth = depthPx * .07;
    g.beginPath(); g.ellipse(x, y + depthPx * .12, rw * .99, rh, 0, 0, Math.PI); g.stroke();
    g.restore();
  }
  // inner
  g.beginPath(); g.ellipse(x, y, rw, rh, 0, 0, Math.PI * 2);
  const ig = g.createLinearGradient(0, y - rh, 0, y + rh);
  ig.addColorStop(0, mix(col, '#000000', .35)); ig.addColorStop(1, mix(col, '#000000', .1));
  g.fillStyle = ig; g.fill();
  g.strokeStyle = mix(col, '#ffffff', .4); g.lineWidth = 3; g.stroke();
  return { rx: rw, ry: rh };
}

// liquid surface inside a bowl rim
function liquidSurface(g, x, y, rx, ry, c1, c2, t, o = {}) {
  g.save();
  g.beginPath(); g.ellipse(x, y + ry * .12, rx * .93, ry * .86, 0, 0, Math.PI * 2); g.clip();
  const lg = g.createRadialGradient(x - rx * .25, y - ry * .3, 4, x, y, rx);
  lg.addColorStop(0, c1); lg.addColorStop(1, c2);
  g.fillStyle = lg;
  g.fillRect(x - rx, y - ry, rx * 2, ry * 2.2);
  // ripples from impact point
  if (o.impact) {
    for (let k = 0; k < 5; k++) {
      const ph = ((t * .9 + k / 5) % 1);
      g.strokeStyle = `rgba(255,236,190,${.28 * (1 - ph)})`;
      g.lineWidth = 2.2;
      g.beginPath(); g.ellipse(o.impact[0], o.impact[1], rx * .9 * ph + 8, ry * .9 * ph + 3, 0, 0, Math.PI * 2); g.stroke();
    }
  }
  // specular
  g.fillStyle = 'rgba(255,250,230,0.28)';
  g.beginPath(); g.ellipse(x - rx * .35, y - ry * .25, rx * .32, ry * .12, -.1, 0, Math.PI * 2); g.fill();
  g.restore();
}

// a pouring liquid ribbon from (x0,y0) down to (x1,y1), slight sway; translucent with highlight
function stream(g, x0, y0, x1, y1, w0, w1, t, c1, c2, o = {}) {
  const N = 40, pts = [];
  for (let i = 0; i <= N; i++) {
    const u = i / N;
    const sway = Math.sin(t * 2.2 + u * 5.5) * (o.sway ?? 6) * u * (1 - u) * 4;
    const x = lerp(x0, x1, u) + sway + (o.curve || 0) * Math.sin(u * Math.PI);
    pts.push([x, lerp(y0, y1, u), lerp(w0, w1, Math.pow(u, .6)) * (1 + Math.sin(u * 23 - t * 9) * .05)]);
  }
  g.save();
  g.beginPath();
  pts.forEach(([x, y, w], i) => i ? g.lineTo(x - w / 2, y) : g.moveTo(x - w / 2, y));
  for (let i = N; i >= 0; i--) g.lineTo(pts[i][0] + pts[i][2] / 2, pts[i][1]);
  g.closePath();
  const sg = g.createLinearGradient(x1 - w0, 0, x1 + w0, 0);
  sg.addColorStop(0, c2); sg.addColorStop(.45, c1); sg.addColorStop(1, c2);
  g.fillStyle = sg;
  g.globalAlpha = o.alpha ?? .95;
  g.fill();
  // specular core
  g.globalAlpha = .55;
  g.strokeStyle = 'rgba(255,248,225,0.8)';
  g.lineWidth = Math.max(1.5, w1 * .14);
  g.beginPath();
  pts.forEach(([x, y, w], i) => i ? g.lineTo(x - w * .18, y) : g.moveTo(x - w * .18, y));
  g.stroke();
  g.restore();
}

// many small seeds/grains in a mound or area
function seedField(g, cx, cy, rx, ry, n, seed, draw) {
  const r = rng(seed);
  for (let i = 0; i < n; i++) {
    const a = r() * Math.PI * 2, d = Math.sqrt(r());
    draw(g, cx + Math.cos(a) * rx * d, cy + Math.sin(a) * ry * d, r, d);
  }
}
const SEED = {
  mustard(g, x, y, r) {
    const s = 5 + r() * 2.5;
    const gr = g.createRadialGradient(x - s * .35, y - s * .35, .5, x, y, s);
    const base = r() > .6 ? '#8a4a1a' : (r() > .5 ? '#b0701f' : '#5e2e12');
    gr.addColorStop(0, '#f3c27a'); gr.addColorStop(.35, base); gr.addColorStop(1, '#2a1306');
    g.fillStyle = gr; g.beginPath(); g.arc(x, y, s, 0, Math.PI * 2); g.fill();
  },
  pumpkin(g, x, y, r) {
    g.save(); g.translate(x, y); g.rotate(r() * Math.PI);
    g.fillStyle = r() > .5 ? '#5f8a3a' : '#4b7530';
    g.beginPath(); g.ellipse(0, 0, 13, 6.5, 0, 0, Math.PI * 2); g.fill();
    g.fillStyle = 'rgba(220,240,180,0.35)'; g.beginPath(); g.ellipse(-3, -2, 7, 2, 0, 0, Math.PI * 2); g.fill();
    g.restore();
  },
  sunflower(g, x, y, r) {
    g.save(); g.translate(x, y); g.rotate(r() * Math.PI);
    g.fillStyle = '#e8dcc2'; g.beginPath(); g.ellipse(0, 0, 11, 5, 0, 0, Math.PI * 2); g.fill();
    g.fillStyle = '#8f8574'; g.beginPath(); g.ellipse(0, 0, 11, 2.2, 0, 0, Math.PI * 2); g.fill();
    g.restore();
  },
  chia(g, x, y, r) {
    g.fillStyle = r() > .5 ? '#3b3632' : (r() > .5 ? '#7a7066' : '#c9c0b4');
    g.beginPath(); g.ellipse(x, y, 3.2, 2.4, r() * 3, 0, Math.PI * 2); g.fill();
  },
  flax(g, x, y, r) {
    g.save(); g.translate(x, y); g.rotate(r() * Math.PI * 2);
    const gr = g.createLinearGradient(-6, 0, 6, 0); gr.addColorStop(0, '#5a2f12'); gr.addColorStop(1, '#a8682e');
    g.fillStyle = gr; g.beginPath(); g.ellipse(0, 0, 7, 3.6, 0, 0, Math.PI * 2); g.fill();
    g.fillStyle = 'rgba(255,220,170,0.4)'; g.beginPath(); g.ellipse(-2, -1, 3, 1, 0, 0, Math.PI * 2); g.fill();
    g.restore();
  },
  almond(g, x, y, r) {
    g.save(); g.translate(x, y); g.rotate(r() * Math.PI * 2);
    const gr = g.createLinearGradient(-20, -10, 20, 10); gr.addColorStop(0, '#c58b55'); gr.addColorStop(1, '#6f4021');
    g.fillStyle = gr;
    g.beginPath(); g.moveTo(-22, 0); g.bezierCurveTo(-14, -14, 14, -12, 24, 0); g.bezierCurveTo(14, 12, -14, 14, -22, 0); g.fill();
    g.strokeStyle = 'rgba(60,30,10,0.35)'; g.lineWidth = 1;
    for (let k = -2; k <= 2; k++) { g.beginPath(); g.moveTo(-16, k * 3.4); g.quadraticCurveTo(0, k * 4.5, 18, k * 2); g.stroke(); }
    g.restore();
  },
  cashew(g, x, y, r) {
    g.save(); g.translate(x, y); g.rotate(r() * Math.PI * 2);
    const gr = g.createRadialGradient(-4, -6, 2, 0, 0, 26); gr.addColorStop(0, '#f7e8c8'); gr.addColorStop(1, '#c9a46a');
    g.fillStyle = gr;
    g.beginPath(); g.arc(0, 0, 22, Math.PI * 1.05, Math.PI * 1.95 + .9); g.arc(6, 4, 11, Math.PI * 1.95 + .9, Math.PI * 1.05, true); g.closePath(); g.fill();
    g.restore();
  },
};

// soft glowing bulb (bokeh-aware)
function glow(g, x, y, r, color, a = 1) {
  const gr = g.createRadialGradient(x, y, 0, x, y, r);
  gr.addColorStop(0, hexA(color, .9 * a));
  gr.addColorStop(.18, hexA(color, .45 * a));
  gr.addColorStop(1, hexA(color, 0));
  g.fillStyle = gr;
  g.beginPath(); g.arc(x, y, r, 0, Math.PI * 2); g.fill();
}

// jute sack with heaped grains, base centre at (x,y)
function sack(g, x, y, w, h, fill, seed) {
  const r = rng(seed);
  g.save();
  g.translate(x, y);
  const sg = g.createRadialGradient(0, 0, 4, 0, 0, w * .8);
  sg.addColorStop(0, 'rgba(0,0,0,0.5)'); sg.addColorStop(1, 'rgba(0,0,0,0)');
  g.save(); g.scale(1, .2); g.fillStyle = sg; g.beginPath(); g.arc(0, 0, w * .8, 0, Math.PI * 2); g.fill(); g.restore();
  // body
  g.beginPath();
  g.moveTo(-w * .5, -h * .72);
  g.bezierCurveTo(-w * .56, -h * .4, -w * .6, -h * .1, -w * .46, 0);
  g.lineTo(w * .46, 0);
  g.bezierCurveTo(w * .6, -h * .1, w * .56, -h * .4, w * .5, -h * .72);
  g.closePath();
  const bg = g.createLinearGradient(-w / 2, 0, w / 2, 0);
  bg.addColorStop(0, '#5e4526'); bg.addColorStop(.4, '#b08c5a'); bg.addColorStop(.7, '#9a774a'); bg.addColorStop(1, '#4e3820');
  g.fillStyle = bg; g.fill();
  g.save(); g.clip();
  g.strokeStyle = 'rgba(50,32,14,0.25)'; g.lineWidth = 1;
  for (let i = -w; i < w; i += 7) { g.beginPath(); g.moveTo(i, 0); g.lineTo(i + 10, -h); g.stroke(); }
  for (let j = 0; j < h; j += 7) { g.beginPath(); g.moveTo(-w, -j); g.lineTo(w, -j - 3); g.stroke(); }
  g.restore();
  // rolled rim
  g.fillStyle = '#c7a473';
  g.beginPath(); g.ellipse(0, -h * .72, w * .52, h * .11, 0, 0, Math.PI * 2); g.fill();
  g.fillStyle = '#7a5a33';
  g.beginPath(); g.ellipse(0, -h * .72 + 4, w * .47, h * .08, 0, 0, Math.PI * 2); g.fill();
  // heap
  const hg = g.createRadialGradient(-w * .1, -h * .86, 4, 0, -h * .74, w * .5);
  hg.addColorStop(0, mix(fill, '#ffffff', .25)); hg.addColorStop(1, mix(fill, '#000000', .35));
  g.fillStyle = hg;
  g.beginPath(); g.ellipse(0, -h * .74, w * .47, h * .08, 0, 0, Math.PI); g.bezierCurveTo(-w * .4, -h * .95, w * .4, -h * .95, w * .47, -h * .74); g.fill();
  g.fillStyle = mix(fill, '#ffffff', .3);
  for (let i = 0; i < 160; i++) {
    const a = r() * Math.PI, d = r();
    const px = Math.cos(a) * w * .44 * d, py = -h * .74 - Math.sin(a) * h * .17 * (1 - d * .7);
    g.globalAlpha = .4 + r() * .4;
    g.beginPath(); g.arc(px, py, 1.8 + r() * 1.6, 0, Math.PI * 2); g.fill();
  }
  g.restore();
}

// Faceless figure seen from behind (flat illustration), shoulders at (x,y), scale s
function figureBack(g, x, y, s, o = {}) {
  g.save();
  g.translate(x, y);
  g.scale(s, s);
  const kameez = o.kameez || '#b9823a', orna = o.orna || '#2f6a48', hair = '#1a120c', skin = '#9a6844';
  // torso
  g.fillStyle = kameez;
  g.beginPath();
  g.moveTo(-95, 0); g.bezierCurveTo(-120, 10, -128, 80, -122, 200);
  g.lineTo(-110, 520); g.lineTo(110, 520); g.lineTo(122, 200);
  g.bezierCurveTo(128, 80, 120, 10, 95, 0);
  g.closePath(); g.fill();
  const tg = g.createLinearGradient(-120, 0, 120, 0);
  tg.addColorStop(0, 'rgba(0,0,0,0.25)'); tg.addColorStop(.5, 'rgba(255,255,255,0.06)'); tg.addColorStop(1, 'rgba(0,0,0,0.3)');
  g.fillStyle = tg; g.fill();
  // raised right arm (reaching / holding)
  const reach = o.reach ?? 0;
  g.save();
  g.translate(100, 30);
  g.rotate(lerp(.25, -.85, reach));
  g.fillStyle = kameez;
  rrect(g, -26, 0, 52, 210, 26); g.fill();
  g.fillStyle = skin;
  rrect(g, -20, 190, 40, 130, 20); g.fill();
  g.restore();
  // orna draped over left shoulder
  g.fillStyle = orna;
  g.beginPath();
  g.moveTo(-100, -8); g.bezierCurveTo(-40, -20, 10, 30, 30, 120);
  g.bezierCurveTo(40, 260, -30, 420, -60, 520); g.lineTo(-118, 520);
  g.bezierCurveTo(-130, 300, -140, 100, -100, -8);
  g.fill();
  g.strokeStyle = 'rgba(255,255,255,0.12)'; g.lineWidth = 4;
  g.beginPath(); g.moveTo(-90, 40); g.bezierCurveTo(-60, 200, -80, 360, -95, 500); g.stroke();
  // neck + head (back of head, hair bun)
  g.fillStyle = skin;
  rrect(g, -24, -60, 48, 70, 18); g.fill();
  g.fillStyle = hair;
  g.beginPath(); g.ellipse(0, -118, 66, 78, 0, 0, Math.PI * 2); g.fill();
  g.beginPath(); g.ellipse(4, -58, 34, 26, 0, 0, Math.PI * 2); g.fill();
  g.fillStyle = 'rgba(255,255,255,0.08)';
  g.beginPath(); g.ellipse(-20, -150, 30, 18, -.5, 0, Math.PI * 2); g.fill();
  g.restore();
}
