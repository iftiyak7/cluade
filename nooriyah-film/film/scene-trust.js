// Scene 7 (54.45 – 70.65 s): trust is not built in a day — a plant that grows with every value named.
'use strict';

const Trust = (() => {
  // growth keyed to the narration: a new pair of leaves for each idea
  const GROW = [[54.6, 0], [57.2, .22], [58.1, .34], [59.6, .46], [61.5, .6], [64.6, .7], [66.1, .78], [67.9, .86], [68.6, .93], [69.6, 1]];
  const NODES = 9;

  function pot(g, x, y) {
    const pg = g.createLinearGradient(x - 160, 0, x + 160, 0);
    pg.addColorStop(0, '#6e2f17'); pg.addColorStop(.4, '#c26a3e'); pg.addColorStop(1, '#5c2512');
    g.fillStyle = pg;
    g.beginPath(); g.moveTo(x - 150, y); g.lineTo(x + 150, y); g.lineTo(x + 112, y + 250); g.lineTo(x - 112, y + 250); g.fill();
    g.fillStyle = '#b25c33'; rrect(g, x - 172, y - 40, 344, 56, 12); g.fill();
    g.fillStyle = 'rgba(255,220,180,0.2)'; g.fillRect(x - 168, y - 36, 336, 6);
    g.fillStyle = '#2b1a0e'; g.beginPath(); g.ellipse(x, y - 36, 150, 22, 0, 0, Math.PI * 2); g.fill();
  }

  function plant(g, t, x, y, G) {
    const hgt = 760 * Math.pow(G, .85);
    const sway = Math.sin(t * .8) * 8 * G;
    const P = u => [x + Math.sin(u * 2.4) * 26 * G + sway * u, y - hgt * u];
    g.strokeStyle = '#5c8f2e'; g.lineWidth = 6 + 10 * G; g.lineCap = 'round';
    g.beginPath();
    for (let i = 0; i <= 30; i++) { const [px, py] = P(i / 30); i ? g.lineTo(px, py) : g.moveTo(px, py); }
    if (hgt > 2) g.stroke();
    // leaf pairs at nodes
    for (let n = 0; n < NODES; n++) {
      const born = (n + 1) / (NODES + 1);
      const k = clamp((G - born * .92) / .12);
      if (k <= 0) continue;
      const u = born;
      const [nx, ny] = P(u * .96);
      const size = (110 + (NODES - n) * 10) * easeOut(k) * (.55 + .45 * G);
      const side = n % 2 ? 1 : -1;
      const flap = Math.sin(t * 1.1 + n) * .05;
      leaf(g, nx, ny, size, side > 0 ? -.45 + flap : Math.PI + .45 + flap, { c1: '#a6d957', c2: '#4f8f29' });
      leaf(g, nx, ny, size * .7, side > 0 ? Math.PI + .7 - flap : -.7 - flap, { c1: '#8cc63f', c2: '#3f7a22' });
    }
    // crown: two leaves in the spirit of the logo
    if (G > .05) {
      const [tx, ty] = P(1);
      const k = easeOut(clamp(G / .25));
      leaf(g, tx, ty, 170 * k * (.6 + .4 * G), -1.05 + Math.sin(t) * .04, { c1: '#b2e05f', c2: '#6aa82f' });
      leaf(g, tx, ty, 120 * k * (.6 + .4 * G), -2.3 + Math.sin(t + 1) * .04, { c1: '#9fd34f', c2: '#4f8f29' });
    }
  }

  function draw(g, t) {
    const G = track(GROW, t, easeInOut);
    const day = ep(t, 65.2, 69.6);
    // deep forest field, warming as the "days" pass
    const bg = g.createLinearGradient(0, 0, 0, H);
    bg.addColorStop(0, mix('#06261a', '#14412c', day)); bg.addColorStop(1, '#04140d');
    g.fillStyle = bg; g.fillRect(0, 0, W, H);
    const pulse = [66.1, 67.9, 68.6].reduce((a, p) => a + Math.exp(-Math.pow((t - p) / .35, 2)), 0);
    glow(g, 540, 820, 900, '#e3b765', .12 + .1 * day + .08 * pulse);
    glow(g, 540, 1350, 500, '#8cc63f', .06 + .05 * G);
    botanicalLines(g, t, .06);
    // camera: gentle rise with the growth, then push towards the crown for the finale
    const z = lerp(1, 1.05, G) + ep(t, 69.4, 70.7, easeIn) * 1.6;
    const ty = lerp(0, -40, G) + ep(t, 69.4, 70.7, easeIn) * 520;
    g.save();
    g.translate(W / 2, 1000); g.scale(z, z); g.translate(-W / 2, -1000 + ty);
    const sh = g.createRadialGradient(540, 1720, 20, 540, 1720, 380);
    sh.addColorStop(0, 'rgba(0,0,0,0.6)'); sh.addColorStop(1, 'rgba(0,0,0,0)');
    g.fillStyle = sh; g.fillRect(0, 1500, W, 420);
    plant(g, t, 540, 1430, G);
    pot(g, 540, 1470);
    g.restore();
    finish(g, t, { vignette: .5, grain: .06 });

    textLine(g, 'আমরা জানি,', t, { x: W / 2, y: 340, font: F.sans(500, 54), color: C.gold, tIn: 54.75, tOut: 57.3 });
    textLine(g, 'বিশ্বাস একদিনে', t, { x: W / 2, y: 470, font: F.serif(700, 88), tIn: 56.05, tOut: 57.3 });
    textLine(g, 'তৈরি হয় না', t, { x: W / 2, y: 585, font: F.serif(700, 88), tIn: 56.7, tOut: 57.3 });

    const KW = [[57.92, 'পণ্যের মান', 330], [59.48, 'ব্যবহারকারীর অভিজ্ঞতা', 455], [61.38, 'দায়িত্বশীলতা', 580]];
    for (const [a, s, y] of KW) {
      textLine(g, s, t, { x: W / 2, y, font: F.serif(600, 68), tIn: a, tOut: 64.55 });
      accentLine(g, W / 2, y + 36, 70, t, a + .2, 64.5, { diamond: true });
    }
    textLine(g, 'বিশ্বাস অর্জনের প্রত্যয়', t, { x: W / 2, y: 400, font: F.serif(700, 80), color: C.gold, tIn: 65.5, tOut: 69.5 });
    textLine(g, 'প্রতিদিন, একটু একটু করে', t, { x: W / 2, y: 520, font: F.sans(500, 56), tIn: 67.88, tOut: 69.5 });
  }
  return { draw };
})();
