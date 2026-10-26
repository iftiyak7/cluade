// Render index.html frame-by-frame with Playwright and pipe JPEG frames to ffmpeg.
// Usage: node render.js <out.mp4> [fps] [startSec] [endSec]
//        node render.js --stills <outDir> t1 t2 ...
const { chromium } = require('playwright');
const { spawn } = require('child_process');
const path = require('path');

async function open() {
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1080, height: 1920 }, deviceScaleFactor: 1 });
  await page.goto('file://' + path.join(__dirname, 'index.html') + '?render=1');
  // Fonts load lazily on first use; force every face in now so no frame renders with a fallback.
  await page.evaluate(async () => {
    const faces = ["500 40px 'Noto Sans Bengali'", "700 40px 'Noto Sans Bengali'", "800 40px 'Noto Sans Bengali'",
      "900 40px 'Noto Sans Bengali'", "600 40px 'Hind Siliguri'", "700 40px 'Hind Siliguri'", "500 40px 'Hind Siliguri'",
      "600 40px Montserrat", "700 40px Montserrat", "800 40px Montserrat", "900 40px Montserrat"];
    await Promise.all(faces.map(fc => document.fonts.load(fc, 'অআ ১২ ABC 2,0')));
    await document.fonts.ready;
  });
  await page.waitForTimeout(300);
  return { browser, page };
}

async function shot(page, cdp, t) {
  await page.evaluate(tt => window.renderAt(tt), t);
  const { data } = await cdp.send('Page.captureScreenshot', { format: 'jpeg', quality: 94 });
  return Buffer.from(data, 'base64');
}

(async () => {
  const args = process.argv.slice(2);
  const { browser, page } = await open();
  const cdp = await page.context().newCDPSession(page);
  if (args[0] === '--stills') {
    const fs = require('fs');
    fs.mkdirSync(args[1], { recursive: true });
    for (const t of args.slice(2)) fs.writeFileSync(path.join(args[1], `t${t}.jpg`), await shot(page, cdp, parseFloat(t)));
  } else {
    const [out, fpsArg, a, b] = args;
    const fps = parseInt(fpsArg || '30', 10);
    const dur = await page.evaluate(() => window.DURATION);
    const start = parseFloat(a || '0'), end = Math.min(parseFloat(b || dur), dur);
    const ff = spawn('ffmpeg', ['-v', 'error', '-y', '-f', 'image2pipe', '-framerate', String(fps), '-c:v', 'mjpeg', '-i', '-',
      '-c:v', 'libx264', '-preset', 'medium', '-crf', '17', '-pix_fmt', 'yuv420p', out], { stdio: ['pipe', 'inherit', 'inherit'] });
    const n0 = Math.round(start * fps), n1 = Math.round(end * fps);
    for (let n = n0; n < n1; n++) {
      const buf = await shot(page, cdp, n / fps);
      if (!ff.stdin.write(buf)) await new Promise(r => ff.stdin.once('drain', r));
      if ((n - n0) % 300 === 0) console.log(`${out}: frame ${n - n0}/${n1 - n0}`);
    }
    ff.stdin.end();
    await new Promise(r => ff.on('close', r));
  }
  await browser.close();
})();
