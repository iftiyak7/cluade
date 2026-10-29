// Scene 5 (32.95 – 44.55 s): the product range, one category at a time, then a flat-lay of all.
// Illustrative, unbranded ingredients only: no packaging, labels, certifications or claims.
'use strict';

const PRODUCT_CUTS = [34.85, 36.25, 37.92, 39.22, 40.56, 41.4];

const Products = (() => {
  let buf, flat;
  const BN = ['০১', '০২', '০৩', '০৪', '০৫'];

  // shared 3/4 macro set: dark warm backdrop, tabletop, key light from upper left
  function set3q(g, t, warm = '#e3a54a') {
    const bg = g.createLinearGradient(0, 0, 0, H);
    bg.addColorStop(0, '#0b2219'); bg.addColorStop(.55, '#1c140c'); bg.addColorStop(1, '#0d0906');
    g.fillStyle = bg; g.fillRect(0, 0, W, H);
    glow(g, 260, 380, 900, warm, .16);
    glow(g, 540, 1150, 700, warm, .14);
    // tabletop
    const tg = g.createLinearGradient(0, 1300, 0, H);
    tg.addColorStop(0, '#4a3020'); tg.addColorStop(.2, '#33200f'); tg.addColorStop(1, '#120a05');
    g.fillStyle = tg; g.fillRect(0, 1300, W, H - 1300);
    g.fillStyle = 'rgba(255,215,160,0.12)'; g.fillRect(0, 1300, W, 3);
    g.strokeStyle = 'rgba(0,0,0,0.25)'; g.lineWidth = 2;
    for (let i = 1; i < 14; i++) { const y = 1300 + i * i * 3.4; g.beginPath(); g.moveTo(0, y); g.lineTo(W, y + Math.sin(i) * 4); g.stroke(); }
  }

  function camPush(g, lt, amt = .06, cx = W / 2, cy = 1150) {
    const z = 1 + lt * amt;
    g.translate(cx, cy); g.scale(z, z); g.translate(-cx, -cy);
  }

  // ---- 01 honey ----
  function honey(g, t) {
    const lt = t - 32.95;
    set3q(g, t);
    g.save(); camPush(g, lt, .035);
    const bx = 540, by = 1240;
    const b = bowl(g, bx, by, 270, 175, { color: '#ead9bd', band: '#6b4a2f' });
    liquidSurface(g, bx, by + 8, b.rx, b.ry, '#f5c25a', '#9a5a12', t, { impact: [bx + 4, by + 14] });
    // honey folding onto itself where the stream lands
    for (let k = 0; k < 4; k++) {
      const ph = (lt * .7 + k / 4) % 1;
      g.fillStyle = hexA('#f7c867', .55 * (1 - ph));
      g.beginPath(); g.ellipse(bx + 4, by + 14 - ph * 6, 18 + ph * 40, 6 + ph * 12, 0, 0, Math.PI * 2); g.fill();
    }
    glow(g, bx, 980, 260, '#ffb84a', .25);
    // dipper: grooved wooden head, handle rising out of frame
    const dx = 540 + Math.sin(lt * 1.3) * 6, dy = 600;
    g.save();
    g.translate(dx, dy); g.rotate(-.12 + Math.sin(lt * 1.3) * .015);
    const hg = g.createLinearGradient(-30, 0, 30, 0);
    hg.addColorStop(0, '#5a3a1e'); hg.addColorStop(.45, '#c4955c'); hg.addColorStop(1, '#4a2e15');
    g.fillStyle = hg; rrect(g, -16, -700, 32, 640, 14); g.fill();
    for (let k = 0; k < 6; k++) {
      const yy = -40 + k * 22;
      const rg = g.createLinearGradient(-62, 0, 62, 0);
      rg.addColorStop(0, '#6a4524'); rg.addColorStop(.4, '#d7a96b'); rg.addColorStop(1, '#5a3a1e');
      g.fillStyle = rg; g.beginPath(); g.ellipse(0, yy, 62 - Math.abs(k - 2.5) * 6, 13, 0, 0, Math.PI * 2); g.fill();
    }
    // honey coating on the dipper head
    const cg = g.createLinearGradient(-60, 0, 60, 0);
    cg.addColorStop(0, 'rgba(160,90,15,0.85)'); cg.addColorStop(.45, 'rgba(250,200,95,0.8)'); cg.addColorStop(1, 'rgba(140,75,10,0.85)');
    g.fillStyle = cg;
    g.beginPath(); g.moveTo(-58, 40); g.bezierCurveTo(-60, 90, -20, 110, 0, 128); g.bezierCurveTo(20, 110, 60, 90, 58, 40); g.closePath(); g.fill();
    g.restore();
    stream(g, dx + 2, dy + 120, bx + 4, by + 14, 26, 11, lt, '#ffd27a', '#b8771c', { sway: 3 });
    g.restore();
  }

  // ---- 02a wooden ghani with mustard seeds ----
  function ghani(g, t) {
    const lt = t - 34.85;
    set3q(g, t, '#d99a3a');
    g.save(); camPush(g, lt, .05, W / 2, 1200);
    const cx = 540, cy = 1180, rx = 360, ry = 112;
    // mortar body (carved timber)
    const mg = g.createLinearGradient(cx - rx, 0, cx + rx, 0);
    mg.addColorStop(0, '#2a1a0d'); mg.addColorStop(.35, '#7a5230'); mg.addColorStop(.55, '#8c6038'); mg.addColorStop(1, '#22150a');
    g.fillStyle = mg;
    g.beginPath(); g.ellipse(cx, cy, rx, ry, 0, 0, Math.PI); g.lineTo(cx - rx * 1.06, cy + 760); g.lineTo(cx + rx * 1.06, cy + 760); g.lineTo(cx + rx, cy); g.fill();
    g.strokeStyle = 'rgba(30,15,5,0.35)'; g.lineWidth = 3;
    for (let k = 0; k < 12; k++) { const x = cx - rx + k * rx / 6 + 20; g.beginPath(); g.moveTo(x, cy + 40); g.bezierCurveTo(x + 10, cy + 300, x - 12, cy + 500, x + 4, cy + 760); g.stroke(); }
    // rim
    g.strokeStyle = '#a77a48'; g.lineWidth = 16; g.beginPath(); g.ellipse(cx, cy, rx, ry, 0, 0, Math.PI * 2); g.stroke();
    // seeds churning inside
    g.save(); g.beginPath(); g.ellipse(cx, cy, rx - 12, ry - 6, 0, 0, Math.PI * 2); g.clip();
    g.fillStyle = '#3a210c'; g.fillRect(cx - rx, cy - ry, rx * 2, ry * 2);
    const r = rng(31);
    for (let i = 0; i < 900; i++) {
      const a0 = r() * Math.PI * 2, d = .15 + Math.sqrt(r()) * .85;
      const a = a0 + lt * (.9 / d) * .35;
      SEED.mustard(g, cx + Math.cos(a) * (rx - 14) * d, cy + Math.sin(a) * (ry - 6) * d, r);
    }
    // oily sheen ring
    g.strokeStyle = 'rgba(255,205,90,0.25)'; g.lineWidth = 10;
    g.beginPath(); g.ellipse(cx, cy, rx * .55, ry * .5, 0, 0, Math.PI * 2); g.stroke();
    g.restore();
    // pestle beam grinding in a slow orbit
    const pa = lt * 1.1;
    const px = cx + Math.cos(pa) * 70, py = cy + Math.sin(pa) * 22;
    g.save();
    g.translate(px, py); g.rotate(-.42 + Math.cos(pa) * .05);
    const lg = g.createLinearGradient(-70, 0, 70, 0);
    lg.addColorStop(0, '#2c1b0c'); lg.addColorStop(.4, '#9a6a3a'); lg.addColorStop(1, '#2a180a');
    g.fillStyle = lg; rrect(g, -62, -1300, 124, 1310, 50); g.fill();
    g.strokeStyle = 'rgba(30,15,5,0.4)'; g.lineWidth = 2;
    for (let k = -2; k <= 2; k++) { g.beginPath(); g.moveTo(k * 20, -1300); g.lineTo(k * 20 + 6, 0); g.stroke(); }
    g.restore();
    g.restore();
    // foreground seeds (defocused)
    g.save(); g.filter = 'blur(6px)';
    seedField(g, 540, 1850, 600, 70, 220, 41, SEED.mustard);
    g.restore();
  }

  // ---- 02b mustard oil into a ceramic dish ----
  function mustardOil(g, t) {
    const lt = t - 36.25;
    set3q(g, t, '#e0a83a');
    g.save(); camPush(g, lt, .05);
    const bx = 540, by = 1240;
    seedField(g, 230, 1420, 160, 46, 260, 52, SEED.mustard);
    const b = bowl(g, bx, by, 300, 110, { color: '#e3d2b4', band: '#3d5a3a', tilt: .3 });
    liquidSurface(g, bx, by + 6, b.rx, b.ry, '#f6c63a', '#9c6408', t, { impact: [bx - 6, by + 10] });
    glow(g, bx, 900, 300, '#ffc23a', .18);
    // brass jug spout entering from the top
    g.save(); g.translate(470, 520); g.rotate(.5);
    const jg = g.createLinearGradient(-120, 0, 120, 0);
    jg.addColorStop(0, '#5a3d10'); jg.addColorStop(.45, '#e7bd62'); jg.addColorStop(1, '#4a3008');
    g.fillStyle = jg;
    g.beginPath(); g.moveTo(-160, -500); g.lineTo(160, -500); g.lineTo(150, -120); g.quadraticCurveTo(120, -40, 40, -20); g.lineTo(70, 30); g.lineTo(10, 20); g.quadraticCurveTo(-130, -30, -150, -140); g.closePath(); g.fill();
    g.restore();
    stream(g, 520, 560, bx - 6, by + 10, 20, 12, lt, '#ffd65a', '#c48a10', { sway: 4, curve: 8 });
    seedField(g, 860, 1460, 150, 40, 180, 53, SEED.mustard);
    g.restore();
  }

  // ---- 03 olive oil into a small glass bowl ----
  function olive(g, t) {
    const lt = t - 37.92;
    set3q(g, t, '#c9c070');
    g.save(); camPush(g, lt, .05);
    const bx = 540, by = 1260, rx = 250, ry = 78;
    // glass bowl: transparent body with refraction highlights
    g.save();
    g.beginPath(); g.ellipse(bx, by, rx, ry, 0, 0, Math.PI); g.bezierCurveTo(bx - rx, by + 150, bx - rx * .5, by + 165, bx, by + 165); g.bezierCurveTo(bx + rx * .5, by + 165, bx + rx, by + 150, bx + rx, by); g.closePath();
    const og = g.createLinearGradient(0, by, 0, by + 165);
    og.addColorStop(0, 'rgba(214,205,90,0.85)'); og.addColorStop(1, 'rgba(110,105,25,0.9)');
    g.fillStyle = og; g.fill();
    g.strokeStyle = 'rgba(255,255,240,0.45)'; g.lineWidth = 3; g.stroke();
    g.fillStyle = 'rgba(255,255,255,0.25)';
    g.beginPath(); g.ellipse(bx - rx * .62, by + 70, 14, 55, -.25, 0, Math.PI * 2); g.fill();
    g.restore();
    liquidSurface(g, bx, by + 4, rx, ry, '#e2dc78', '#8a8424', t, { impact: [bx + 10, by + 8] });
    g.strokeStyle = 'rgba(255,255,240,0.6)'; g.lineWidth = 3; g.beginPath(); g.ellipse(bx, by, rx, ry, 0, 0, Math.PI * 2); g.stroke();
    stream(g, 548 + Math.sin(lt) * 4, -20, bx + 10, by + 8, 16, 10, lt, '#ece27a', '#9a9228', { sway: 2 });
    // olive sprig
    g.save(); g.translate(860, 1520); g.rotate(-.5);
    g.strokeStyle = '#5c4a2a'; g.lineWidth = 7; g.beginPath(); g.moveTo(0, 0); g.quadraticCurveTo(-80, -200, -40, -420); g.stroke();
    for (let k = 0; k < 9; k++) {
      const yy = -40 - k * 44, side = k % 2 ? 1 : -1;
      leaf(g, -20 - k * 4, yy, 150, side > 0 ? -.3 : Math.PI + .3, { c1: '#a9b688', c2: '#5d6e44', width: .22 });
    }
    for (const [ox, oy] of [[-10, -120], [30, -260], [-50, -330]]) {
      const ol = g.createRadialGradient(ox - 8, oy - 10, 2, ox, oy, 30);
      ol.addColorStop(0, '#8c9a4a'); ol.addColorStop(1, '#2f3a12');
      g.fillStyle = ol; g.beginPath(); g.ellipse(ox, oy, 24, 30, .3, 0, Math.PI * 2); g.fill();
    }
    g.restore();
    g.restore();
  }

  // ---- top-down flat lay world (seeds, nuts, everything) ----
  const FW = 2200, FH = 2600;
  const SPOT = { seeds: [760, 650], nuts: [1100, 1320], all: [1100, 1300] };
  function bowlTop(g, x, y, r, col = '#ece0c8') {
    const sg = g.createRadialGradient(x + 14, y + 22, r * .8, x + 14, y + 22, r * 1.25);
    sg.addColorStop(0, 'rgba(0,0,0,0.45)'); sg.addColorStop(1, 'rgba(0,0,0,0)');
    g.fillStyle = sg; g.beginPath(); g.arc(x + 14, y + 22, r * 1.25, 0, Math.PI * 2); g.fill();
    const og = g.createRadialGradient(x - r * .3, y - r * .3, r * .2, x, y, r);
    og.addColorStop(0, mix(col, '#ffffff', .2)); og.addColorStop(1, mix(col, '#000000', .25));
    g.fillStyle = og; g.beginPath(); g.arc(x, y, r, 0, Math.PI * 2); g.fill();
    const ig = g.createRadialGradient(x + r * .2, y + r * .2, r * .1, x, y, r * .86);
    ig.addColorStop(0, mix(col, '#000000', .05)); ig.addColorStop(1, mix(col, '#000000', .35));
    g.fillStyle = ig; g.beginPath(); g.arc(x, y, r * .86, 0, Math.PI * 2); g.fill();
  }
  function fillBowl(g, x, y, r, n, seed, fn) {
    g.save(); g.beginPath(); g.arc(x, y, r * .84, 0, Math.PI * 2); g.clip();
    seedField(g, x, y, r * .86, r * .86, n, seed, fn);
    const sh = g.createRadialGradient(x - r * .3, y - r * .3, r * .1, x, y, r);
    sh.addColorStop(0, 'rgba(255,240,210,0.15)'); sh.addColorStop(1, 'rgba(0,0,0,0.35)');
    g.fillStyle = sh; g.fillRect(x - r, y - r, r * 2, r * 2);
    g.restore();
  }
  function liquidTop(g, x, y, r, c1, c2) {
    g.save(); g.beginPath(); g.arc(x, y, r * .84, 0, Math.PI * 2); g.clip();
    const lg = g.createRadialGradient(x - r * .35, y - r * .35, 4, x, y, r);
    lg.addColorStop(0, c1); lg.addColorStop(1, c2);
    g.fillStyle = lg; g.fillRect(x - r, y - r, r * 2, r * 2);
    g.fillStyle = 'rgba(255,250,225,0.35)'; g.beginPath(); g.ellipse(x - r * .35, y - r * .4, r * .3, r * .1, -.6, 0, Math.PI * 2); g.fill();
    g.restore();
  }
  function buildFlat() {
    flat = makeCanvas(FW, FH);
    const g = flat.getContext('2d');
    // warm wooden board
    g.fillStyle = '#6a4528'; g.fillRect(0, 0, FW, FH);
    const r = rng(99);
    for (let x = 0; x < FW; x += 300) {
      g.fillStyle = `rgba(${120 + r() * 30},${80 + r() * 20},${45 + r() * 10},0.35)`; g.fillRect(x, 0, 296, FH);
      g.fillStyle = 'rgba(25,12,4,0.55)'; g.fillRect(x + 296, 0, 4, FH);
      g.strokeStyle = 'rgba(40,20,8,0.22)'; g.lineWidth = 2;
      for (let k = 0; k < 10; k++) { const xx = x + 15 + r() * 270; g.beginPath(); g.moveTo(xx, 0); for (let y = 0; y < FH; y += 100) g.lineTo(xx + Math.sin(y * .004 + k) * 8, y); g.stroke(); }
    }
    // linen runner
    g.save(); g.translate(1100, 1300); g.rotate(-.12);
    g.fillStyle = '#e6dcc6'; g.fillRect(-520, -1500, 1040, 3000);
    g.strokeStyle = 'rgba(120,100,70,0.12)'; g.lineWidth = 1;
    for (let i = -520; i < 520; i += 6) { g.beginPath(); g.moveTo(i, -1500); g.lineTo(i, 1500); g.stroke(); }
    g.fillStyle = 'rgba(31,90,61,0.55)'; g.fillRect(-520, -1500, 22, 3000); g.fillRect(498, -1500, 22, 3000);
    g.restore();
    // seeds cluster
    const [sx, sy] = SPOT.seeds;
    const SB = [[-140, -130, 'pumpkin', 150], [140, -120, 'sunflower', 170], [-135, 140, 'chia', 900], [145, 150, 'flax', 420]];
    SB.forEach(([ox, oy, k, n], i) => { bowlTop(g, sx + ox, sy + oy, 125); fillBowl(g, sx + ox, sy + oy, 125, n, 60 + i, SEED[k]); });
    // honey bowl with a dipper resting across
    bowlTop(g, 1470, 620, 200, '#ead9bd');
    liquidTop(g, 1470, 620, 200, '#f8c862', '#a5600f');
    g.save(); g.translate(1470, 620); g.rotate(.7);
    g.fillStyle = '#9a6a3a'; rrect(g, -20, -40, 40, 420, 16); g.fill();
    for (let k = 0; k < 5; k++) { g.fillStyle = k % 2 ? '#c9965a' : '#8a5a2e'; rrect(g, -50, -150 + k * 22, 100, 20, 9); g.fill(); }
    g.restore();
    // nuts bowl
    const [nx, ny] = SPOT.nuts;
    bowlTop(g, nx, ny, 240, '#3d5a3a');
    fillBowl(g, nx, ny, 240, 120, 70, (g2, x, y, rr) => (rr() > .45 ? SEED.almond : SEED.cashew)(g2, x, y, rr));
    seedField(g, nx + 260, ny + 230, 90, 60, 6, 71, SEED.almond);
    seedField(g, nx - 300, ny - 230, 70, 50, 4, 72, SEED.cashew);
    // mustard oil dish + seed heap
    bowlTop(g, 720, 1980, 200, '#e3d2b4');
    liquidTop(g, 720, 1980, 200, '#f6c63a', '#9c6408');
    seedField(g, 470, 2230, 120, 70, 420, 73, SEED.mustard);
    // olive oil glass bowl + olives
    bowlTop(g, 1480, 2010, 180, '#cfd3b0');
    liquidTop(g, 1480, 2010, 180, '#e2dc78', '#7d7a1e');
    for (let k = 0; k < 6; k++) leaf(g, 1700 + k * 14, 1760 + k * 40, 140, 2.2 + k * .5, { c1: '#a9b688', c2: '#5d6e44', width: .22 });
    // fresh leaves (logo palette) scattered
    leaf(g, 1700, 1180, 220, 2.6, {}); leaf(g, 430, 1300, 200, -.6, {}); leaf(g, 1150, 360, 180, .9, { c1: '#8cc63f', c2: '#3f7a22' });
    // subtle top light falloff
    const v = g.createRadialGradient(1100, 1250, 400, 1100, 1300, 1500);
    v.addColorStop(0, 'rgba(0,0,0,0)'); v.addColorStop(1, 'rgba(0,0,0,0.45)');
    g.fillStyle = v; g.fillRect(0, 0, FW, FH);
  }
  const FCAM = [
    [39.22, [...SPOT.seeds, 1.75, -.15]],
    [40.4, [SPOT.seeds[0] + 30, SPOT.seeds[1] + 20, 1.9, -.05]],
    [40.9, [...SPOT.nuts, 1.85, .02], t => easeInOut(t)],
    [41.5, [...SPOT.nuts, 1.8, .04]],
    [43.2, [...SPOT.all, .78, .1], t => easeInOut(t)],
    [44.6, [...SPOT.all, .74, .12]],
  ];
  function flatLay(g, t) {
    const [x, y, z, rot] = track(FCAM, t);
    g.fillStyle = '#2a1a0d'; g.fillRect(0, 0, W, H);
    g.save();
    g.translate(W / 2, H / 2 + 120 + ep(t, 41.5, 43.2) * 140); g.rotate(rot); g.scale(z, z); g.translate(-x, -y);
    g.drawImage(flat, 0, 0);
    g.restore();
  }

  function init() { buf = makeCanvas(W, H); buildFlat(); }

  const SHOTS = [
    [32.95, honey], [34.85, ghani], [36.25, mustardOil], [37.92, olive], [39.22, flatLay],
  ];
  function shotAt(t) { let s = SHOTS[0]; for (const e of SHOTS) if (t >= e[0]) s = e; return s; }

  function draw(g, t) {
    const [st, fn] = shotAt(t);
    const xf = .24;
    const i = SHOTS.findIndex(e => e[0] === st);
    if (i > 0 && t < st + xf) { // match cut: brief dissolve with a scale settle on the centred vessel
      SHOTS[i - 1][1](g, t);
      const b = buf.getContext('2d');
      b.save(); b.clearRect(0, 0, W, H);
      const p = easeOut(inv(st, st + xf, t));
      b.translate(W / 2, 1200); b.scale(1.06 - .06 * p, 1.06 - .06 * p); b.translate(-W / 2, -1200);
      fn(b, t); b.restore();
      g.save(); g.globalAlpha = p; g.drawImage(buf, 0, 0); g.restore();
    } else fn(g, t);
    finish(g, t, { vignette: .5, grain: .065 });

    // typography: index + label, one category at a time
    scrim(g, 210, 720, .45);
    const L = [
      [33.25, 34.75, 0, null, 'মধু'],
      [34.95, 37.8, 1, 'কাঠের ঘানির', 'সরিষার তেল'],
      [37.98, 39.12, 2, null, 'অলিভ অয়েল'],
      [39.3, 40.46, 3, null, 'বিভিন্ন সিডস'],
      [40.62, 41.3, 4, null, 'বাদাম'],
    ];
    for (const [a, b, k, sub, main] of L) {
      textLine(g, BN[k], t, { x: W / 2, y: 330, font: F.sans(600, 40), color: C.gold, tIn: a, tOut: b, dur: .5 });
      if (sub) textLine(g, sub, t, { x: W / 2, y: 430, font: F.sans(500, 54), color: C.gold, tIn: a + .1, tOut: b, dur: .6 });
      textLine(g, main, t, { x: W / 2, y: sub ? 545 : 490, font: F.serif(700, 104), tIn: a + .08, tOut: b, dur: .6 });
    }
    // progress diamonds (five categories)
    const on = ep(t, 33.2, 33.7) * (1 - ep(t, 41.3, 41.7));
    if (on > 0) {
      g.save(); g.globalAlpha = on;
      for (let k = 0; k < 5; k++) {
        const active = L[k][0] <= t + .05;
        const x = W / 2 - 120 + k * 60, y = 640, s = active ? 8 : 6;
        g.fillStyle = active ? C.gold : hexA(C.ivory, .35);
        g.beginPath(); g.moveTo(x, y - s); g.lineTo(x + s, y); g.lineTo(x, y + s); g.lineTo(x - s, y); g.fill();
      }
      g.strokeStyle = hexA(C.ivory, .25); g.lineWidth = 1.5;
      g.beginPath(); g.moveTo(W / 2 - 112, 640); g.lineTo(W / 2 + 112, 640); g.stroke();
      g.restore();
    }
    textLine(g, 'নানা ধরনের', t, { x: W / 2, y: 400, font: F.sans(500, 58), color: C.gold, tIn: 41.45, tOut: 43.95 });
    textLine(g, 'খাদ্যপণ্য', t, { x: W / 2, y: 530, font: F.serif(700, 112), tIn: 41.6, tOut: 43.95 });
  }
  return { init, draw };
})();
