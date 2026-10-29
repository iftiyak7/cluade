// Scene 1–2 (0 – 19.6 s): the everyday problem — a local market stall, too many choices.
'use strict';

const Market = (() => {
  const SW = 3000, SH = 3300;
  const ROWS = [560, 1100, 1640, 2180, 2720];
  let wall, counter, items = [];
  const PICK = {}; // named hero items

  function init() {
    wall = makeCanvas(SW, SH);
    const g = wall.getContext('2d');
    // dark timber back wall
    const bg = g.createLinearGradient(0, 0, 0, SH);
    bg.addColorStop(0, '#22170e'); bg.addColorStop(.5, '#2e1f12'); bg.addColorStop(1, '#1b120a');
    g.fillStyle = bg; g.fillRect(0, 0, SW, SH);
    const r = rng(11);
    for (let x = 0; x < SW; x += 150) {
      g.fillStyle = `rgba(${90 + r() * 30},${60 + r() * 20},${35 + r() * 10},0.18)`;
      g.fillRect(x, 0, 146, SH);
      g.fillStyle = 'rgba(0,0,0,0.35)'; g.fillRect(x + 146, 0, 4, SH);
    }
    // warm practical light pools on the wall
    for (let i = 0; i < 7; i++) glow(g, 220 + i * 430, 380, 700, '#c98a3c', .22);
    // market awning (scalloped canvas edge) at the top
    for (let x = 0; x < SW; x += 120) {
      g.fillStyle = (x / 120) % 2 ? '#e9dfc6' : '#1f5a3d';
      g.fillRect(x, 0, 120, 210);
      g.beginPath(); g.arc(x + 60, 210, 60, 0, Math.PI); g.fill();
    }
    const ag = g.createLinearGradient(0, 0, 0, 280);
    ag.addColorStop(0, 'rgba(0,0,0,0.15)'); ag.addColorStop(1, 'rgba(0,0,0,0.55)');
    g.fillStyle = ag; g.fillRect(0, 0, SW, 275);

    // shelves of everyday food staples
    const keys = Object.keys(GOODS);
    ROWS.forEach((py, ri) => {
      let x = 70;
      while (x < SW - 120) {
        const key = keys[Math.floor(r() * keys.length)];
        const isB = GOODS[key].kind === 'bottle';
        const w = isB ? 120 + r() * 30 : 140 + r() * 70;
        const h = isB ? 330 + r() * 50 : 210 + r() * 110;
        items.push({ x: x + w / 2, y: py - 18, w, h, key, seed: Math.floor(r() * 1e6), row: ri });
        x += w + 22 + r() * 34;
      }
    });
    // hero items: one honey jar (the question), three similar oil bottles (which one?)
    const place = (name, row, x, key, w, h) => {
      items = items.filter(it => !(it.row === row && Math.abs(it.x - x) < (it.w + w) / 2 + 12));
      const it = { x, y: ROWS[row] - 18, w, h, key, seed: 4242 + x, row };
      items.push(it); PICK[name] = it;
    };
    place('J1', 4, 1620, 'honey', 190, 300);
    place('J2', 2, 1240, 'mustardOil', 128, 360);
    place('J3', 2, 1450, 'mustardOil', 134, 372);
    place('J4', 2, 1660, 'mustardOil', 126, 352);
    ROWS.forEach((py, ri) => {
      items.filter(it => it.row === ri).forEach(it => vessel(g, it.x, it.y, it.w, it.h, it.key, it.seed));
      plank(g, 0, py, SW, 40, 36, 30 + ri);
    });

    // front counter with open jute sacks of grains
    counter = makeCanvas(SW, 900);
    const c = counter.getContext('2d');
    const cg = c.createLinearGradient(0, 380, 0, 900);
    cg.addColorStop(0, '#6e4a2a'); cg.addColorStop(.08, '#4a2f19'); cg.addColorStop(1, '#1d120a');
    c.fillStyle = cg; c.fillRect(0, 380, SW, 520);
    c.fillStyle = 'rgba(255,220,170,0.12)'; c.fillRect(0, 380, SW, 6);
    const fills = ['#e98a3c', '#f2ead6', '#7a3d14', '#d9b14a', '#c45a2a', '#efe6cf', '#a8572a'];
    for (let i = 0; i < 9; i++) sack(c, 180 + i * 340 + (r() - .5) * 40, 470 + r() * 20, 300, 330, fills[i % fills.length], 90 + i);
  }

  // camera: [x, y, zoom] keyframes in wall coordinates
  const CAM = [
    [0, [1480, 1880, 1.25]],
    [2.4, [1440, 1840, 1.33]],
    [5.4, [1560, 2650, 1.2]],
    [6.2, [1620, 2400, 1.72]],
    [9.2, [1615, 2420, 1.86]],
    [10.25, [1500, 1960, .6], t => easeOutQuint(t)],
    [11.15, [1490, 1930, .62]],
    [11.75, [1300, 1380, 1.28], t => easeInOut(t)],
    [13.2, [1420, 1390, 1.3]],
    [14.7, [1560, 1400, 1.32]],
    [16.4, [1500, 1420, 1.3]],
    [19.6, [1480, 1440, 1.22]],
  ];
  function cam(t) { return track(CAM, t); }

  function toScreen(px, py, cm, k = 1) {
    return [W / 2 + (px - cm[0]) * cm[2] * k, H / 2 + (py - cm[1]) * cm[2] * k];
  }

  function drawLayer(g, img, ox, oy, cm, k, blur) {
    const [sx, sy] = toScreen(ox, oy, cm, k);
    g.save();
    if (blur > .5) g.filter = `blur(${blur.toFixed(1)}px)`;
    g.drawImage(img, sx, sy, img.width * cm[2] * k, img.height * cm[2] * k);
    g.restore();
  }

  // ring target over time
  const RING = [
    [5.9, 'J1'], [9.15, null], [11.44, 'J2'], [12.88, 'J3'], [14.48, 'J4'], [16.35, null],
  ];
  function heroScreen(name, cm) {
    const it = PICK[name];
    const [x, y] = toScreen(it.x, it.y - it.h * .45, cm);
    return [x, y, it.h * .62 * cm[2]];
  }

  function draw(g, t) {
    const cm = cam(t);
    const defocus = ep(t, 16.4, 18.6) * 9;
    g.fillStyle = '#120b06'; g.fillRect(0, 0, W, H);
    drawLayer(g, wall, 0, 0, cm, 1, defocus + (t > 2.4 && t < 5.6 ? ep(t, 2.4, 3.6) * 2.5 * (1 - ep(t, 5, 5.6)) : 0));

    // focus emphasis: redraw hero item sharp + spotlight falloff
    let target = null, tStart = 0;
    for (const [tt, n] of RING) if (t >= tt) { target = n; tStart = tt; }
    if (target) {
      const prev = RING[RING.findIndex(e => e[1] === target) - 1];
      const from = prev && prev[1] ? heroScreen(prev[1], cm) : null;
      const to = heroScreen(target, cm);
      const mv = from ? easeInOut(inv(tStart, tStart + .55, t)) : 1;
      const cx = from ? lerp(from[0], to[0], mv) : to[0];
      const cy = from ? lerp(from[1], to[1], mv) : to[1];
      const rad = from ? lerp(from[2], to[2], mv) : to[2];
      const on = easeOut(inv(tStart, tStart + .5, t));
      const spot = g.createRadialGradient(cx, cy, rad * .9, cx, cy, rad * 3.2);
      spot.addColorStop(0, 'rgba(0,0,0,0)');
      spot.addColorStop(1, `rgba(5,3,2,${.5 * Math.max(on, from ? 1 : 0)})`);
      g.fillStyle = spot; g.fillRect(0, 0, W, H);
      // selection ring: hairline, drawn on
      g.save();
      g.strokeStyle = hexA(C.ivory, .85);
      g.lineWidth = 2.5;
      g.beginPath();
      g.arc(cx, cy, rad * 1.02, -Math.PI / 2, -Math.PI / 2 + Math.PI * 2 * easeInOut(inv(tStart, tStart + .9, t)));
      g.stroke();
      g.fillStyle = C.gold;
      g.beginPath(); g.arc(cx + Math.cos(-Math.PI / 2 + Math.PI * 2 * easeInOut(inv(tStart, tStart + .9, t))) * rad * 1.02, cy + Math.sin(-Math.PI / 2 + Math.PI * 2 * easeInOut(inv(tStart, tStart + .9, t))) * rad * 1.02, 6, 0, Math.PI * 2); g.fill();
      g.restore();
    } else if (t > 9.1 && t < 9.6) {
      // ring fades with the pull-back
    }

    // counter with sacks (closer to camera: parallax factor 1.12)
    drawLayer(g, counter, -60, 2560, cm, 1.12, defocus * 1.3 + (t < 2.4 || t > 6 ? 5 : 1.5));

    // string lights in front of the stall
    g.save();
    if (defocus > 1) g.filter = `blur(${defocus}px)`;
    const k = 1.1;
    g.strokeStyle = 'rgba(20,12,6,0.9)'; g.lineWidth = 3 * cm[2];
    g.beginPath();
    for (let x = -200; x <= SW + 200; x += 40) {
      const [sx, sy] = toScreen(x, 330 + Math.sin((x / 520) * Math.PI) ** 2 * 60, cm, k);
      x === -200 ? g.moveTo(sx, sy) : g.lineTo(sx, sy);
    }
    g.stroke();
    for (let x = 60; x < SW; x += 260) {
      const [sx, sy] = toScreen(x, 330 + Math.sin((x / 520) * Math.PI) ** 2 * 60 + 40, cm, k);
      const flick = .92 + .08 * Math.sin(t * 7 + x);
      glow(g, sx, sy, 150 * cm[2], '#ffb85a', .55 * flick);
      g.fillStyle = '#fff1cf'; g.beginPath(); g.arc(sx, sy, 11 * cm[2], 0, Math.PI * 2); g.fill();
    }
    g.restore();

    // over-the-shoulder shopper (defocused foreground): human presence without a face
    const fgA = 1 - ep(t, 9.15, 9.9);
    if (fgA > 0) {
      g.save();
      g.globalAlpha = fgA;
      g.filter = 'blur(16px)';
      const sway = Math.sin(t * .9) * 10;
      figureBack(g, 150 - ep(t, 9.15, 9.9) * 400 + sway, 1530 + ep(t, 0, 9) * -30, 2.1, { kameez: '#2c2116', orna: '#173323', reach: 0 });
      g.restore();
    }

    // warm overall grade + darkening while uncertainty settles
    g.fillStyle = `rgba(4,8,5,${ep(t, 16.6, 19.2) * .45})`;
    g.fillRect(0, 0, W, H);
    finish(g, t, { vignette: .55 });

    // ---- typography ----
    scrim(g, 230, 700, .55 * (ep(t, .2, .8) * (1 - ep(t, 2.2, 2.8)) + ep(t, 5.9, 6.4) * (1 - ep(t, 8.9, 9.5)) + ep(t, 11.2, 11.6) * (1 - ep(t, 16.2, 16.8))));
    textLine(g, 'আচ্ছা, একটা কথা বলুন তো…', t, { x: W / 2, y: 470, font: F.sans(600, 60), tIn: .45, tOut: 2.15 });
    textLine(g, 'যা কিনছি,', t, { x: W / 2, y: 420, font: F.serif(600, 74), tIn: 6.05, tOut: 8.95 });
    textLine(g, [['সেটা ', C.ivory], ['কতটা ভালো?', C.gold]], t, { x: W / 2, y: 530, font: F.serif(700, 84), tIn: 7.42, tOut: 8.95 });
    accentLine(g, W / 2, 590, 220, t, 7.7, 8.9, { diamond: true });
    textLine(g, 'কোনটা কিনব?', t, { x: W / 2, y: 470, font: F.serif(600, 72), tIn: 11.44, tOut: 12.7 });
    textLine(g, [['কোনটা ', C.ivory], ['মানে ভালো?', C.gold]], t, { x: W / 2, y: 470, font: F.serif(600, 72), tIn: 12.88, tOut: 14.3 });
    textLine(g, [['পরিবারের জন্য ', C.ivory], ['কোনটা?', C.gold]], t, { x: W / 2, y: 470, font: F.serif(600, 72), tIn: 14.48, tOut: 16.2 });
  }

  return { init, draw };
})();
