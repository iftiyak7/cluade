// Scene 3 (18.95 – 27.65 s): "নুরিয়াহর শুরুটা এই ভাবনা থেকেই" — first logo reveal, botanical light.
'use strict';

// slow dappled leaf shadows (a gobo) for any dark green field; excluded rect keeps the logo untouched
let shadowBuf = null;
function leafShadows(g, t, alpha, hole) {
  if (!shadowBuf) shadowBuf = makeCanvas(W, H);
  const b = shadowBuf.getContext('2d');
  b.save();
  b.clearRect(0, 0, W, H);
  b.filter = 'blur(22px)';
  const r = rng(5);
  for (let i = 0; i < 16; i++) {
    const x = r() * W * 1.3 - 100 + Math.sin(t * .25 + i) * 30;
    const y = r() * H + Math.cos(t * .2 + i * 1.7) * 30;
    leaf(b, x, y, 260 + r() * 260, r() * 6.28 + Math.sin(t * .3 + i) * .08, { c1: '#021a10', c2: '#021a10', vein: false });
  }
  b.restore();
  if (hole) { // soft clearing around the logo so its square never reads as a box
    b.save();
    b.globalCompositeOperation = 'destination-out';
    const rg = b.createRadialGradient(hole[0], hole[1], hole[2], hole[0], hole[1], hole[3]);
    rg.addColorStop(0, 'rgba(0,0,0,1)'); rg.addColorStop(1, 'rgba(0,0,0,0)');
    b.fillStyle = rg; b.fillRect(0, 0, W, H);
    b.restore();
  }
  g.save(); g.globalAlpha = alpha; g.drawImage(shadowBuf, 0, 0); g.restore();
}

// fine botanical line-art (outline leaves) used as texture
function botanicalLines(g, t, alpha) {
  g.save();
  g.globalAlpha = alpha;
  g.strokeStyle = C.ivory;
  g.lineWidth = 1.4;
  const sprig = (x, y, s, a) => {
    g.save(); g.translate(x, y); g.rotate(a); g.scale(s, s);
    g.beginPath(); g.moveTo(0, 0); g.bezierCurveTo(30, -120, 10, -260, 40, -380); g.stroke();
    for (let k = 0; k < 6; k++) {
      const yy = -60 - k * 55, side = k % 2 ? 1 : -1;
      g.save(); g.translate(14 + k * 3, yy); g.rotate(side * .9 - .3);
      g.beginPath(); g.moveTo(0, 0); g.bezierCurveTo(20, -26, 64, -22, 84, 0); g.bezierCurveTo(60, 18, 22, 18, 0, 0); g.stroke();
      g.restore();
    }
    g.restore();
  };
  sprig(70, 1820, 1.25, -.25 + Math.sin(t * .4) * .02);
  sprig(1010, 1700, 1.05, .35 + Math.sin(t * .35 + 1) * .02);
  sprig(980, 380, .9, Math.PI + .5 + Math.sin(t * .3) * .02);
  sprig(100, 260, .8, Math.PI - .4 + Math.sin(t * .33 + 2) * .02);
  g.restore();
}

const Brand = (() => {
  function draw(g, t) {
    // logo: centre reveal, then glides up to make room for the thought
    const up = ep(t, 21.75, 22.75);
    const cx = W / 2, cy = lerp(880, 660, up);
    const lw = lerp(600, 470, up);
    const lh = lw * LOGO.height / LOGO.width;
    logoField(g, cx, cy, lw * .74, lw * 1.9, C.forest);
    // warm light breathing in the field
    glow(g, W * .2 + Math.sin(t * .4) * 40, 300, 700, '#e3b765', .08);
    glow(g, W * .85, 1600 + Math.cos(t * .3) * 40, 800, '#8cc63f', .06);
    leafShadows(g, t, .55, [cx, cy, lw * .75, lw * 1.35]);
    botanicalLines(g, t, .07 * ep(t, 19.2, 20.5));
    finish(g, t, { vignette: .45, grain: .05 });

    // expanding hairline ring as the logo arrives
    const rp = ep(t, 19.6, 21.2, easeOutQuint);
    if (rp > 0 && rp < 1) {
      g.strokeStyle = hexA(C.ivory, .5 * (1 - rp));
      g.lineWidth = 2;
      g.beginPath(); g.arc(cx, cy, lw * .55 + rp * 420, 0, Math.PI * 2); g.stroke();
    }
    const rev = ep(t, 19.55, 20.45, easeOutQuint);
    drawLogo(g, cx, cy, lw * (1.04 - .04 * rev), 1, rev); // uniform scale settle

    const ty = cy + lh / 2;
    textLine(g, 'নুরিয়াহর শুরুটা', t, { x: W / 2, y: ty + 150, font: F.serif(600, 70), tIn: 20.0, tOut: 21.55 });
    textLine(g, [['এই ', C.ivory], ['ভাবনা', C.gold], [' থেকেই', C.ivory]], t, { x: W / 2, y: ty + 250, font: F.serif(600, 70), tIn: 20.62, tOut: 21.55 });
    textLine(g, 'প্রতিদিনের খাবার কেনায়', t, { x: W / 2, y: 1170, font: F.sans(500, 56), tIn: 22.8, tOut: 27.6 });
    textLine(g, [['একটু ', C.ivory], ['ভরসা', C.gold]], t, { x: W / 2, y: 1320, font: F.serif(700, 110), tIn: 26.1, tOut: 27.6, dur: .7 });
    accentLine(g, W / 2, 1060, 160, t, 22.6, 27.6, { diamond: true });
  }
  return { draw };
})();
