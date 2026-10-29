// Scene 6 (44.0 – 55.25 s): beyond online — NOORIYAH's own outlet in Jashore.
// Illustrative interior only: not a depiction of the real shop, no address, map or contact details.
'use strict';

const Outlet = (() => {
  let room, phoneUI;
  const RW = 1300, RH = 2200, OX = 110, OY = 140; // room canvas, offset so it can be pushed into
  const VP = [540, 880];

  function buildRoom() {
    room = makeCanvas(RW, RH);
    const g = room.getContext('2d');
    g.translate(OX, OY);
    const r = rng(404);
    const BW = [140, 940], BH = [400, 1290]; // back wall rect
    // ceiling: warm timber slats converging to the vanishing point
    g.fillStyle = '#6b4a2f';
    g.beginPath(); g.moveTo(-OX, -OY); g.lineTo(W + OX, -OY); g.lineTo(BW[1], BH[0]); g.lineTo(BW[0], BH[0]); g.fill();
    g.strokeStyle = 'rgba(30,15,5,0.4)'; g.lineWidth = 3;
    for (let i = 0; i <= 14; i++) { const x = lerp(-OX, W + OX, i / 14); g.beginPath(); g.moveTo(x, -OY); g.lineTo(lerp(BW[0], BW[1], i / 14), BH[0]); g.stroke(); }
    // floor: light terrazzo with perspective joints
    const fg = g.createLinearGradient(0, BH[1], 0, H + OY);
    fg.addColorStop(0, '#cdbb98'); fg.addColorStop(1, '#a58f6a');
    g.fillStyle = fg;
    g.beginPath(); g.moveTo(BW[0], BH[1]); g.lineTo(BW[1], BH[1]); g.lineTo(W + OX, H + OY * 2); g.lineTo(-OX, H + OY * 2); g.fill();
    g.strokeStyle = 'rgba(90,70,40,0.25)'; g.lineWidth = 2;
    for (let i = 0; i <= 10; i++) { g.beginPath(); g.moveTo(lerp(BW[0], BW[1], i / 10), BH[1]); g.lineTo(lerp(-900, W + 900, i / 10), H + OY * 2); g.stroke(); }
    for (let k = 1; k < 8; k++) { const y = BH[1] + Math.pow(k / 8, 1.8) * (H + OY * 2 - BH[1]); g.beginPath(); g.moveTo(-OX, y); g.lineTo(W + OX, y); g.stroke(); }
    // left wall with a tall window of daylight
    g.fillStyle = '#e2d4b4';
    g.beginPath(); g.moveTo(-OX, -OY); g.lineTo(BW[0], BH[0]); g.lineTo(BW[0], BH[1]); g.lineTo(-OX, H + OY * 2); g.fill();
    const wg = g.createLinearGradient(-OX, 0, BW[0], 0);
    wg.addColorStop(0, '#fffbe8'); wg.addColorStop(1, '#d9ecc6');
    g.fillStyle = wg;
    g.beginPath(); g.moveTo(-OX + 20, 260); g.lineTo(BW[0] - 30, 520); g.lineTo(BW[0] - 30, 1150); g.lineTo(-OX + 20, 1500); g.fill();
    // right wall, forest-green lower panel, angled shelves with jars
    g.fillStyle = '#e8dcc0';
    g.beginPath(); g.moveTo(W + OX, -OY); g.lineTo(BW[1], BH[0]); g.lineTo(BW[1], BH[1]); g.lineTo(W + OX, H + OY * 2); g.fill();
    g.fillStyle = '#1f5a3d';
    g.beginPath(); g.moveTo(W + OX, 1640); g.lineTo(BW[1], 1120); g.lineTo(BW[1], BH[1]); g.lineTo(W + OX, H + OY * 2); g.fill();
    const keys = ['honey', 'mustardOil', 'ghee', 'seeds', 'almond', 'oliveOil', 'lentil', 'dates'];
    for (let s = 0; s < 3; s++) {
      const yFront = 520 + s * 330, yBack = BH[0] + 120 + s * 200;
      for (let i = 9; i >= 0; i--) {
        const u = i / 10;
        const x = lerp(W + 40, BW[1] + 30, u), y = lerp(yFront, yBack, u), sc = lerp(1, .45, u);
        vessel(g, x, y, 95 * sc, 150 * sc, keys[(i + s * 3) % keys.length], 900 + i * 7 + s, {});
      }
      g.strokeStyle = '#7a5230'; g.lineWidth = 16;
      g.beginPath(); g.moveTo(W + OX, yFront + 4); g.lineTo(BW[1], yBack + 2); g.stroke();
    }
    // back wall: cream with neatly organised timber shelves
    g.fillStyle = '#efe4cc'; g.fillRect(BW[0], BH[0], BW[1] - BW[0], BH[1] - BH[0]);
    g.fillStyle = '#1f5a3d'; g.fillRect(BW[0], 1080, BW[1] - BW[0], BH[1] - 1080);
    for (let row = 0; row < 3; row++) {
      const py = 610 + row * 200;
      let x = BW[0] + 40;
      let n = 0;
      while (x < BW[1] - 70) {
        const k = keys[(n + row * 2) % keys.length], isB = GOODS[k].kind === 'bottle';
        const w = isB ? 52 : 70, h = isB ? 150 : 108;
        vessel(g, x + w / 2, py - 6, w, h, k, 300 + row * 40 + n);
        x += w + 18; n++;
      }
      plank(g, BW[0], py, BW[1] - BW[0], 16, 14, 70 + row);
    }
    // display table at the centre
    const T = { y: 1400, x0: 300, x1: 790 };
    g.fillStyle = '#8a6038';
    g.beginPath(); g.moveTo(T.x0 + 40, T.y - 70); g.lineTo(T.x1 - 40, T.y - 70); g.lineTo(T.x1, T.y); g.lineTo(T.x0, T.y); g.fill();
    const tf = g.createLinearGradient(0, T.y, 0, T.y + 300);
    tf.addColorStop(0, '#5e3d22'); tf.addColorStop(1, '#3b2614');
    g.fillStyle = tf; g.fillRect(T.x0, T.y, T.x1 - T.x0, 300);
    g.fillStyle = 'rgba(255,230,190,0.2)'; g.fillRect(T.x0, T.y, T.x1 - T.x0, 4);
    // goods on the display table (centre and right; the left is where the customer picks)
    const TB = [[600, 'honey', 80, 118], [680, 'ghee', 74, 100], [745, 'mustardOil', 56, 160], [520, 'almond', 72, 104]];
    for (const [x, k, w, h] of TB) vessel(g, x, T.y - 22, w, h, k, 500 + x);
    // bamboo basket with jute bags
    g.fillStyle = '#c9a160'; g.beginPath(); g.ellipse(410, T.y - 40, 70, 22, 0, 0, Math.PI * 2); g.fill();
    g.fillStyle = '#e98a3c'; g.beginPath(); g.ellipse(410, T.y - 46, 58, 16, 0, Math.PI, 0); g.fill();
    // potted plant (back right corner)
    g.fillStyle = '#a2583a'; rrect(g, 860, 1180, 70, 110, 10); g.fill();
    for (let i = 0; i < 9; i++) leaf(g, 895, 1190, 120 + r() * 40, -Math.PI / 2 + (r() - .5) * 2.2, { c1: '#5f9c3a', c2: '#2c5a22' });
  }

  function buildPhone() {
    phoneUI = makeCanvas(500, 1040);
    const g = phoneUI.getContext('2d');
    g.fillStyle = '#f5efe1'; g.fillRect(0, 0, 500, 1040);
    g.fillStyle = '#e7dcc4'; g.fillRect(0, 0, 500, 120);
    g.fillStyle = 'rgba(31,90,61,0.8)'; rrect(g, 30, 56, 160, 18, 9); g.fill();
    g.fillStyle = 'rgba(31,90,61,0.25)'; rrect(g, 30, 160, 440, 56, 28); g.fill();
    const cards = [['honey', 0, 0], ['mustardOil', 1, 0], ['seeds', 0, 1], ['almond', 1, 1], ['ghee', 0, 2], ['oliveOil', 1, 2]];
    for (const [k, cx, cy] of cards) {
      const x = 30 + cx * 230, y = 250 + cy * 260;
      g.fillStyle = '#ffffff'; rrect(g, x, y, 210, 240, 18); g.fill();
      g.fillStyle = '#efe4cc'; rrect(g, x + 12, y + 12, 186, 150, 12); g.fill();
      vessel(g, x + 105, y + 150, GOODS[k].kind === 'bottle' ? 50 : 72, GOODS[k].kind === 'bottle' ? 120 : 96, k, 700 + cx + cy * 2, { label: true });
      g.fillStyle = 'rgba(40,40,30,0.35)'; rrect(g, x + 16, y + 184, 120, 12, 6); g.fill();
      g.fillStyle = 'rgba(40,40,30,0.18)'; rrect(g, x + 16, y + 206, 80, 10, 5); g.fill();
    }
  }

  function init() { buildRoom(); buildPhone(); }

  // customer seen from behind; returns hand position for the picked jar
  function customer(g, t) {
    const reach = track([[49.9, 0], [51.0, 1], [51.5, 1], [52.6, 1.85], [54.6, 1.8]], t);
    const s = 1.0, x = 300, y = 1170;
    const th = lerp(.25, -.85, reach);
    const hand = [x + (100 - 300 * Math.sin(th) * 1) * s, y + (30 + 300 * Math.cos(th)) * s];
    figureBack(g, x, y, s, { kameez: '#c08a3e', orna: '#1f5a3d', reach });
    return { hand, reach };
  }

  function interior(g, t) {
    const z = track([[46.6, 1.0], [50.0, 1.1], [54.4, 1.42], [55.3, 1.46]], t, easeInOutSine);
    const fx = track([[46.6, 540], [50.0, 540], [54.4, 470]], t, easeInOutSine);
    const fy = track([[46.6, 960], [50.0, 980], [54.4, 1130]], t, easeInOutSine);
    g.fillStyle = '#efe4cc'; g.fillRect(0, 0, W, H);
    g.save();
    g.translate(W / 2, H / 2); g.scale(z, z); g.translate(-fx, -fy);
    g.drawImage(room, -OX, -OY);
    // pendant lamps
    for (const lx of [320, 540, 760]) {
      const ly = 560 + Math.abs(lx - 540) * .25;
      g.strokeStyle = '#2a1a0d'; g.lineWidth = 3; g.beginPath(); g.moveTo(lx, -200); g.lineTo(lx, ly - 40); g.stroke();
      const lg = g.createLinearGradient(lx - 60, 0, lx + 60, 0);
      lg.addColorStop(0, '#6a4a1a'); lg.addColorStop(.5, '#e2b864'); lg.addColorStop(1, '#5a3c12');
      g.fillStyle = lg; g.beginPath(); g.moveTo(lx - 18, ly - 44); g.lineTo(lx + 18, ly - 44); g.lineTo(lx + 62, ly); g.lineTo(lx - 62, ly); g.fill();
      glow(g, lx, ly + 10, 260, '#ffc874', .55);
      g.fillStyle = '#fff3d6'; g.beginPath(); g.ellipse(lx, ly + 2, 30, 8, 0, 0, Math.PI * 2); g.fill();
    }
    // daylight from the window across the floor
    g.save(); g.globalCompositeOperation = 'screen';
    const dl = g.createLinearGradient(0, 1300, 700, 1900);
    dl.addColorStop(0, 'rgba(255,245,215,0.35)'); dl.addColorStop(1, 'rgba(255,245,215,0)');
    g.fillStyle = dl; g.beginPath(); g.moveTo(0, 1250); g.lineTo(180, 1300); g.lineTo(760, 2100); g.lineTo(0, 2100); g.fill();
    g.restore();
    // the jar the customer chooses: rests on the table, then is lifted to look closely
    const c = customer(g, t);
    const onTable = [455, 1380];
    const lift = inv(50.95, 51.05, t) > 0 && t > 51.0;
    const jp = lift ? [c.hand[0] + 6, c.hand[1] + 46] : onTable;
    g.save();
    if (lift) { g.translate(jp[0], jp[1]); g.rotate(Math.sin((t - 51) * 1.4) * .06); g.translate(-jp[0], -jp[1]); }
    vessel(g, jp[0], jp[1], 78, 112, 'honey', 4321, { lid: '#3d5a3a' });
    g.restore();
    if (lift) { // fingers over the jar: simple, quiet shape
      g.fillStyle = '#9a6844';
      rrect(g, jp[0] - 34, jp[1] - 86, 46, 30, 14); g.fill();
    }
    g.restore();
    // foreground plant (defocused, faster parallax)
    g.save(); g.filter = 'blur(14px)';
    const px = 980 - (z - 1) * 400, py = 1900;
    for (let i = 0; i < 8; i++) leaf(g, px, py, 420, -Math.PI / 2 - .9 + i * .28, { c1: '#3f7a2a', c2: '#163a1a', vein: false });
    g.restore();
  }

  function phone(g, t) {
    const lt = t - 44.0;
    g.fillStyle = C.forest; g.fillRect(0, 0, W, H);
    logoField(g, W / 2, 1050, 0, 1300, C.forestDeep);
    botanicalLines(g, t, .07);
    const zoom = easeIn(inv(46.55, 47.35, t));
    const s = lerp(1, 7.5, zoom);
    const pw = 540, ph = 1100, cx = 540, cy = 1080 + Math.sin(lt * 1.2) * 8 - zoom * 60;
    g.save();
    g.translate(cx, cy); g.scale(s, s); g.rotate((1 - zoom) * -.035);
    g.shadowColor = 'rgba(0,0,0,0.5)'; g.shadowBlur = 60; g.shadowOffsetY = 30;
    g.fillStyle = '#101512'; rrect(g, -pw / 2, -ph / 2, pw, ph, 74); g.fill();
    g.shadowColor = 'transparent';
    g.save();
    rrect(g, -pw / 2 + 20, -ph / 2 + 20, pw - 40, ph - 40, 56); g.clip();
    g.drawImage(phoneUI, -pw / 2 + 20, -ph / 2 + 30);
    g.restore();
    g.fillStyle = '#101512'; rrect(g, -60, -ph / 2 + 34, 120, 26, 13); g.fill();
    g.restore();
    return { zoom, s, cx, cy, pw, ph };
  }

  let ibuf;
  function draw(g, t) {
    if (t < 46.55) {
      phone(g, t);
    } else if (t < 47.35) {
      const p = phone(g, t);
      // through the screen into the shop
      if (!ibuf) ibuf = makeCanvas(W, H);
      interior(ibuf.getContext('2d'), t);
      g.save();
      g.translate(p.cx, p.cy); g.scale(p.s, p.s);
      rrect(g, -p.pw / 2 + 20, -p.ph / 2 + 20, p.pw - 40, p.ph - 40, 56);
      g.restore();
      g.save(); g.clip();
      g.globalAlpha = easeInOut(inv(46.6, 47.1, t));
      g.drawImage(ibuf, 0, 0);
      g.restore();
    } else {
      interior(g, t);
    }
    finish(g, t, { vignette: .38, grain: .06 });

    // typography
    textLine(g, 'শুধু অনলাইনেই নয়', t, { x: W / 2, y: 360, font: F.serif(600, 76), tIn: 45.45, tOut: 46.7 });
    scrim(g, 160, 860, .62 * ep(t, 47.2, 47.7) * (1 - ep(t, 54.3, 54.9)), '#06130d');
    // location pin (outline glyph)
    const pin = ep(t, 47.55, 48.1, easeOutQuint) * (1 - ep(t, 54.2, 54.7));
    if (pin > 0) {
      g.save(); g.globalAlpha = pin; g.translate(W / 2, 300 - (1 - pin) * 20);
      g.strokeStyle = C.gold; g.lineWidth = 4;
      g.beginPath(); g.moveTo(0, 30); g.bezierCurveTo(-34, -6, -30, -46, 0, -46); g.bezierCurveTo(30, -46, 34, -6, 0, 30); g.stroke();
      g.beginPath(); g.arc(0, -18, 9, 0, Math.PI * 2); g.stroke();
      g.restore();
    }
    textLine(g, 'যশোরে নুরিয়াহর', t, { x: W / 2, y: 450, font: F.serif(700, 88), tIn: 47.64, tOut: 54.25 });
    textLine(g, 'নিজস্ব আউটলেট', t, { x: W / 2, y: 575, font: F.serif(700, 104), color: C.gold, tIn: 48.0, tOut: 54.25 });
    accentLine(g, W / 2, 640, 260, t, 48.3, 54.2, { diamond: true });
    textLine(g, 'সরাসরি এসে, নিজে দেখে', t, { x: W / 2, y: 735, font: F.sans(500, 54), tIn: 50.1, tOut: 52.45 });
    textLine(g, 'নিজের পছন্দমতো', t, { x: W / 2, y: 735, font: F.sans(500, 54), tIn: 52.62, tOut: 54.25 });
  }
  return { init, draw };
})();
