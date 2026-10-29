// NOORIYAH brand film — timeline, transitions, motion blur, frame entry point.
'use strict';

// Every boundary below is locked to the voice-over analysis in ../timeline.md
// (scene `t0` = start of its incoming transition; previous scene runs until t0 + in.dur).
const SCENES = [
  { s: () => Market, t0: 0 },
  { s: () => Brand, t0: 18.95, in: { type: 'leaves', dur: .8 } },
  { s: () => Kitchen, t0: 26.95, in: { type: 'wipeUp', dur: .7 } },
  { s: () => Products, t0: 32.95, in: { type: 'iris', dur: .6 } },
  { s: () => Outlet, t0: 44.0, in: { type: 'fade', dur: .55 } },
  { s: () => Trust, t0: 54.45, in: { type: 'fade', dur: .8 } },
  { s: () => Finale, t0: 69.75, in: { type: 'leaves', dur: .9 } },
];
// windows where fast camera moves get sub-frame motion blur
const BLUR = [[9.2, 10.1, 6], [11.15, 11.8, 6], [18.95, 19.8, 12], [26.95, 27.65, 8], [40.4, 40.95, 6], [46.5, 47.4, 10], [69.75, 70.7, 12]];

let main, ctx, bufA, bufB;

async function init() {
  main = document.getElementById('c');
  main.width = W; main.height = H;
  ctx = main.getContext('2d');
  bufA = makeCanvas(W, H); bufB = makeCanvas(W, H);
  const fonts = [];
  for (const wt of [400, 500, 600, 700]) fonts.push(document.fonts.load(F.sans(wt, 40), 'বাংলা,?'));
  for (const wt of [400, 500, 600, 700]) fonts.push(document.fonts.load(F.serif(wt, 40), 'বাংলা,?'));
  await Promise.all(fonts);
  LOGO = await new Promise((res, rej) => {
    const im = new Image();
    im.onload = () => res(im); im.onerror = rej;
    im.src = '../assets/src/nooriyah-logo-original.jpg';
  });
  buildGrain();
  for (const sc of SCENES) { let m; try { m = sc.s(); } catch (e) { continue; } m.init && m.init(); }
  window.READY = true;
}

function transition(type, p, g, a, b) {
  // a: outgoing canvas (already on g), b: incoming canvas
  g.save();
  if (type === 'fade') {
    g.globalAlpha = easeInOut(p);
    g.drawImage(b, 0, 0);
  } else if (type === 'wipeUp') {
    // outgoing slides up, incoming rises behind a soft edge
    const e = easeInOut(p);
    g.drawImage(a, 0, -e * H * .35);
    g.beginPath(); g.rect(0, H * (1 - e), W, H); g.clip();
    g.drawImage(b, 0, (1 - e) * H * .25);
    g.restore(); g.save();
    g.fillStyle = hexA(C.gold, .6 * Math.sin(Math.PI * e));
    g.fillRect(0, H * (1 - e) - 2, W, 3);
  } else if (type === 'iris') {
    const e = easeInOut(p);
    g.beginPath(); g.arc(W / 2, 1180, Math.hypot(W, H) * e, 0, Math.PI * 2); g.clip();
    g.drawImage(b, 0, 0);
  } else if (type === 'leaves') {
    // two large logo-like leaves sweep across; the new scene is revealed in their wake
    const e = easeInOut(p);
    g.beginPath();
    g.moveTo(-200, H + 200);
    const reach = e * (W + H) * 1.25;
    g.lineTo(-200 + reach, H + 200);
    g.lineTo(-200, H + 200 - reach);
    g.closePath();
    g.clip();
    g.drawImage(b, 0, 0);
    g.restore(); g.save();
    const lx = -500 + reach * .78, ly = H + 500 - reach * .78;
    leaf(g, lx - 220, ly + 80, 1100, -Math.PI / 4 - .25, { c1: '#a6d957', c2: '#4f8f29', width: .46 });
    leaf(g, lx + 120, ly + 260, 820, -Math.PI / 4 + .35, { c1: '#8cc63f', c2: '#3f7a22', width: .44 });
  }
  g.restore();
}

function drawAt(g, t) {
  let idx = 0;
  for (let i = 0; i < SCENES.length; i++) if (t >= SCENES[i].t0) idx = i;
  const cur = SCENES[idx];
  const prev = SCENES[idx - 1];
  if (prev && cur.in && t < cur.t0 + cur.in.dur) {
    const p = inv(cur.t0, cur.t0 + cur.in.dur, t);
    const ga = bufA.getContext('2d'), gb = bufB.getContext('2d');
    prev.s().draw(ga, t);
    cur.s().draw(gb, t);
    g.drawImage(bufA, 0, 0);
    transition(cur.in.type, p, g, bufA, bufB);
  } else {
    cur.s().draw(g, t);
  }
}

// motion blur by averaging sub-frames across a 180° shutter
let acc;
function renderFrame(t) {
  const win = BLUR.find(([a, b]) => t >= a && t <= b);
  if (!win) { drawAt(ctx, t); return; }
  if (!acc) acc = makeCanvas(W, H);
  const N = win[2], ga = acc.getContext('2d');
  for (let i = 0; i < N; i++) {
    const st = t + (i / (N - 1) - .5) * (.5 / FPS);
    drawAt(ga, st);
    ctx.save();
    ctx.globalAlpha = 1 / (i + 1);
    ctx.drawImage(acc, 0, 0);
    ctx.restore();
  }
}
window.renderFrame = renderFrame;
window.addEventListener('load', init);
