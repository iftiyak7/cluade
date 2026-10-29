// Scene 8 (69.75 – 78.5 s): brand signature — original logo, final lines, clean hold.
'use strict';

const Finale = (() => {
  let sweepBuf;
  function draw(g, t) {
    const cx = W / 2, cy = 650, lw = 600;
    const lh = lw * LOGO.height / LOGO.width;
    logoField(g, cx, cy, lw * .74, lw * 2.0, C.forest);
    glow(g, 160, 1700, 700, '#e3b765', .07);
    leafShadows(g, t, .38, [cx, cy, lw * .76, lw * 1.4]);
    botanicalLines(g, t, .08 * ep(t, 70.2, 71.5));
    // slow warm light sweep across the field (kept clear of the logo)
    if (!sweepBuf) sweepBuf = makeCanvas(W, H);
    const sp = ep(t, 73.8, 76.2, easeInOutSine);
    if (sp > 0 && sp < 1) {
      const b = sweepBuf.getContext('2d');
      b.clearRect(0, 0, W, H);
      const x = lerp(-600, W + 600, sp);
      const sg = b.createLinearGradient(x - 300, 0, x + 300, 0);
      sg.addColorStop(0, 'rgba(255,214,140,0)'); sg.addColorStop(.5, 'rgba(255,214,140,0.16)'); sg.addColorStop(1, 'rgba(255,214,140,0)');
      b.save(); b.translate(W / 2, H / 2); b.rotate(.35); b.translate(-W / 2, -H / 2);
      b.fillStyle = sg; b.fillRect(-W, -H, W * 3, H * 3); b.restore();
      b.save(); b.globalCompositeOperation = 'destination-out';
      const hole = b.createRadialGradient(cx, cy, lw * .76, cx, cy, lw * 1.25);
      hole.addColorStop(0, 'rgba(0,0,0,1)'); hole.addColorStop(1, 'rgba(0,0,0,0)');
      b.fillStyle = hole; b.fillRect(0, 0, W, H); b.restore();
      g.save(); g.globalCompositeOperation = 'screen'; g.drawImage(sweepBuf, 0, 0); g.restore();
    }
    finish(g, t, { vignette: .45, grain: .045 });

    // logo arrives with an iris reveal and a uniform scale settle
    const rev = ep(t, 70.0, 71.1, easeOutQuint);
    drawLogo(g, cx, cy, lw * (1.05 - .05 * rev), 1, rev);
    const rp = ep(t, 70.1, 71.9, easeOutQuint);
    if (rp > 0 && rp < 1) {
      g.strokeStyle = hexA(C.ivory, .45 * (1 - rp)); g.lineWidth = 2;
      g.beginPath(); g.arc(cx, cy, lw * .6 + rp * 480, 0, Math.PI * 2); g.stroke();
    }

    const dim = 1 - .35 * ep(t, 74.0, 74.8);
    const y0 = cy + lh / 2;
    textLine(g, 'আপনার পরিবারের', t, { x: W / 2, y: y0 + 150, font: F.sans(500, 58), tIn: 70.2, alpha: dim });
    textLine(g, 'প্রতিদিনের খাবারের জন্য', t, { x: W / 2, y: y0 + 230, font: F.sans(500, 58), tIn: 71.0, alpha: dim });
    accentLine(g, W / 2, y0 + 300, 240, t, 73.6, null, { diamond: true });
    textLine(g, 'নুরিয়াহ থাকুক', t, { x: W / 2, y: y0 + 425, font: F.serif(700, 96), color: C.ivory, tIn: 74.02, dur: 1.0 });
    textLine(g, 'আপনার পাশে।', t, { x: W / 2, y: y0 + 545, font: F.serif(700, 96), color: C.gold, tIn: 74.85, dur: 1.0 });
  }
  return { draw };
})();
