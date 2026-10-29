// Scene 4 (26.95 – 33.55 s): a calm home kitchen — choosing with understanding, deciding with peace of mind.
'use strict';

const Kitchen = (() => {
  let wallC, tableC, propsC;
  const KW = 1500;

  function init() {
    // wall + window (background, parallax .8)
    wallC = makeCanvas(KW, 1300);
    let g = wallC.getContext('2d');
    const wg = g.createLinearGradient(0, 0, KW, 1300);
    wg.addColorStop(0, '#efe3c8'); wg.addColorStop(1, '#d9c7a2');
    g.fillStyle = wg; g.fillRect(0, 0, KW, 1300);
    const r = rng(21);
    for (let i = 0; i < 2500; i++) { // plaster texture
      g.fillStyle = r() > .5 ? 'rgba(255,255,255,0.05)' : 'rgba(90,60,30,0.04)';
      g.fillRect(r() * KW, r() * 1300, 2 + r() * 6, 2 + r() * 6);
    }
    // window with garden beyond
    const wx = 860, wy = 170, ww = 470, wh = 640;
    const og = g.createLinearGradient(0, wy, 0, wy + wh);
    og.addColorStop(0, '#dff0d0'); og.addColorStop(.5, '#9cc47c'); og.addColorStop(1, '#5d8a45');
    g.fillStyle = og; g.fillRect(wx, wy, ww, wh);
    g.save(); g.beginPath(); g.rect(wx, wy, ww, wh); g.clip(); g.filter = 'blur(10px)';
    for (let i = 0; i < 26; i++) leaf(g, wx + r() * ww, wy + 200 + r() * wh, 120 + r() * 160, r() * 6.3, { c1: '#4f8a35', c2: '#2c5a22', vein: false });
    g.restore();
    g.strokeStyle = '#7a5532'; g.lineWidth = 22;
    g.strokeRect(wx, wy, ww, wh);
    g.lineWidth = 12;
    g.beginPath(); g.moveTo(wx + ww / 2, wy); g.lineTo(wx + ww / 2, wy + wh); g.moveTo(wx, wy + wh * .45); g.lineTo(wx + ww, wy + wh * .45); g.stroke();
    // sill
    g.fillStyle = '#6b4a2f'; g.fillRect(wx - 40, wy + wh + 10, ww + 80, 26);
    // hanging shelf with clay pots on the left
    g.fillStyle = '#5a3b22'; g.fillRect(90, 560, 520, 22);
    for (let i = 0; i < 3; i++) {
      const px = 170 + i * 170, py = 560;
      const pg = g.createRadialGradient(px - 20, py - 70, 5, px, py - 55, 80);
      pg.addColorStop(0, '#c9784a'); pg.addColorStop(1, '#7a3a1c');
      g.fillStyle = pg;
      g.beginPath(); g.ellipse(px, py - 55, 62 - i * 6, 58 - i * 5, 0, 0, Math.PI * 2); g.fill();
      g.fillStyle = '#5e2a12'; g.fillRect(px - 26, py - 122 + i * 6, 52, 14);
    }

    // table top (midground)
    tableC = makeCanvas(KW + 200, 900);
    g = tableC.getContext('2d');
    const tg = g.createLinearGradient(0, 0, 0, 900);
    tg.addColorStop(0, '#9b6b3e'); tg.addColorStop(.25, '#7c5230'); tg.addColorStop(1, '#3d2716');
    g.fillStyle = tg; g.fillRect(0, 0, KW + 200, 900);
    g.strokeStyle = 'rgba(40,20,8,0.3)'; g.lineWidth = 2;
    for (let i = 0; i < 26; i++) {
      const y = 10 + i * i * 1.4;
      g.beginPath(); g.moveTo(0, y);
      for (let x = 0; x < KW + 200; x += 60) g.lineTo(x, y + Math.sin(x * .007 + i) * 3);
      g.stroke();
    }
    g.fillStyle = 'rgba(255,230,190,0.25)'; g.fillRect(0, 0, KW + 200, 4);

    // props on the table
    propsC = makeCanvas(KW + 200, 700);
    g = propsC.getContext('2d');
    // bamboo dala with green chillies and lemons
    const dx = 300, dy = 420;
    g.fillStyle = '#c9a160';
    g.beginPath(); g.ellipse(dx, dy, 230, 70, 0, 0, Math.PI * 2); g.fill();
    g.strokeStyle = 'rgba(110,75,30,0.5)'; g.lineWidth = 2;
    for (let k = 1; k < 7; k++) { g.beginPath(); g.ellipse(dx, dy, 230 * k / 7, 70 * k / 7, 0, 0, Math.PI * 2); g.stroke(); }
    g.strokeStyle = '#8a6430'; g.lineWidth = 10; g.beginPath(); g.ellipse(dx, dy, 230, 70, 0, 0, Math.PI * 2); g.stroke();
    const rr = rng(8);
    for (let i = 0; i < 7; i++) {
      g.save(); g.translate(dx - 150 + rr() * 200, dy - 30 + rr() * 50); g.rotate(rr() * 3);
      g.strokeStyle = '#3f8a2a'; g.lineWidth = 13; g.lineCap = 'round';
      g.beginPath(); g.moveTo(0, 0); g.quadraticCurveTo(40, -10, 80, 6); g.stroke();
      g.strokeStyle = 'rgba(200,255,170,0.4)'; g.lineWidth = 3; g.beginPath(); g.moveTo(6, -3); g.quadraticCurveTo(40, -12, 72, 2); g.stroke();
      g.restore();
    }
    for (let i = 0; i < 3; i++) {
      const lx = dx + 40 + i * 52, ly = dy - 10 + (i % 2) * 24;
      const lg = g.createRadialGradient(lx - 10, ly - 12, 3, lx, ly, 36);
      lg.addColorStop(0, '#f6f0a0'); lg.addColorStop(1, '#a8b52a');
      g.fillStyle = lg; g.beginPath(); g.ellipse(lx, ly, 36, 30, .2, 0, Math.PI * 2); g.fill();
    }
    // steel bowl of lentils
    const b = bowl(g, 720, 470, 120, 80, { color: '#c8ccd0', tilt: .3 });
    g.save(); g.beginPath(); g.ellipse(720, 474, b.rx * .9, b.ry * .8, 0, 0, Math.PI * 2); g.clip();
    seedField(g, 720, 474, b.rx, b.ry, 420, 3, (g2, x, y, r2) => { g2.fillStyle = r2() > .5 ? '#e98a3c' : '#c9662a'; g2.beginPath(); g2.ellipse(x, y, 5, 3.5, 0, 0, Math.PI * 2); g2.fill(); });
    g.restore();
    // honey jar + mustard oil bottle (unbranded)
    vessel(g, 1010, 560, 150, 230, 'honey', 51, { lid: '#6b4a2f' });
    vessel(g, 1200, 575, 118, 360, 'mustardOil', 52, { lid: '#3d5a3a' });
    vessel(g, 1370, 560, 130, 200, 'almond', 53, { lid: '#6b4a2f' });
  }

  function draw(g, t) {
    const u = ep(t, 26.95, 33.6, easeInOutSine);
    const camX = lerp(300, 520, u);
    const z = lerp(1.0, 1.06, u);
    g.fillStyle = '#e8dcc0'; g.fillRect(0, 0, W, H);
    g.save();
    g.translate(W / 2, H / 2); g.scale(z, z); g.translate(-W / 2, -H / 2);
    g.drawImage(wallC, -150 - camX * .7, 0, KW, 1300);
    // daylight beam from the window
    g.save();
    g.globalCompositeOperation = 'screen';
    const beam = g.createLinearGradient(900, 300, 300, 1400);
    beam.addColorStop(0, 'rgba(255,236,190,0.35)'); beam.addColorStop(1, 'rgba(255,236,190,0)');
    g.fillStyle = beam;
    g.beginPath(); g.moveTo(700 - camX * .7, 170); g.lineTo(1250 - camX * .7, 170); g.lineTo(700 - camX, 1500); g.lineTo(-200 - camX, 1500); g.closePath(); g.fill();
    g.restore();
    // dappled leaf shadows on the wall
    g.save(); g.globalAlpha = .16; g.filter = 'blur(16px)';
    const r = rng(77);
    for (let i = 0; i < 12; i++) leaf(g, r() * 900 - camX * .7, 250 + r() * 800, 160 + r() * 140, r() * 6.3 + Math.sin(t * .6 + i) * .06, { c1: '#4a3418', c2: '#4a3418', vein: false });
    g.restore();
    g.drawImage(tableC, -100 - camX, 1180, KW + 200, 900);
    // the chosen bottle eases forward a little: a considered choice
    g.drawImage(propsC, -500 - camX * 1.15, 900, propsC.width * 1.35, propsC.height * 1.35);
    g.restore();
    // selection ring around the bottle while "বেছে নিতে পারে"
    const bx = (1200 * 1.35 - 500 - camX * 1.15 - W / 2) * z + W / 2, by = (900 + (575 - 170) * 1.35 - H / 2) * z + H / 2;
    const rp = ep(t, 28.8, 29.8);
    if (rp > 0) {
      const fade = 1 - ep(t, 31.6, 32.2);
      g.save(); g.strokeStyle = hexA('#3a2718', .75 * fade); g.lineWidth = 2.5;
      g.beginPath(); g.arc(bx, by, 215 * z, -Math.PI / 2, -Math.PI / 2 + Math.PI * 2 * rp); g.stroke(); g.restore();
    }
    finish(g, t, { vignette: .32, grain: .06 });
    const ink = '#173d2b';
    textLine(g, 'নিজের প্রয়োজন বুঝে', t, { x: W / 2, y: 330, font: F.serif(600, 70), color: ink, tIn: 27.45, tOut: 30.2, shadow: false });
    textLine(g, 'পণ্য বেছে নেওয়া', t, { x: W / 2, y: 420, font: F.sans(500, 54), color: C.earth, tIn: 28.75, tOut: 30.2, shadow: false });
    textLine(g, 'আর কেনার সিদ্ধান্ত', t, { x: W / 2, y: 330, font: F.sans(500, 54), color: C.earth, tIn: 30.4, tOut: 33.2, shadow: false });
    textLine(g, 'নিশ্চিন্তে', t, { x: W / 2, y: 470, font: F.serif(700, 120), color: '#9a6a1c', tIn: 32.2, tOut: 33.2, dur: .7, shadow: false });
    accentLine(g, W / 2, 530, 200, t, 32.5, 33.2, { color: hexA('#9a6a1c', .9), diamond: true });
  }
  return { init, draw };
})();
