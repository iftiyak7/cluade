// Usage: node render.mjs stills <out_dir> t1 t2 ...   |   node render.mjs video <out.mp4> <startFrame> <endFrame>
import { createRequire } from 'node:module';
const { chromium } = createRequire(import.meta.url)('playwright');
import { spawn } from 'node:child_process';
import { writeFileSync, mkdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
const here = path.dirname(fileURLToPath(import.meta.url));
const [mode, out, ...rest] = process.argv.slice(2);
const browser = await chromium.launch({ args: ['--allow-file-access-from-files', '--disable-web-security'] });
const page = await browser.newPage({ viewport: { width: 1080, height: 1920 }, deviceScaleFactor: 1 });
page.on('console', m => { if (m.type() === 'error') console.error('PAGE:', m.text()); });
page.on('pageerror', e => console.error('PAGEERR:', e.message));
await page.goto('file://' + path.join(here, 'film/index.html'));
await page.waitForFunction('window.READY === true', null, { timeout: 120000 });
const grab = async t => {
  const b64 = await page.evaluate(t => { renderFrame(t); return document.getElementById('c').toDataURL('image/png').split(',')[1]; }, t);
  return Buffer.from(b64, 'base64');
};
if (mode === 'stills') {
  mkdirSync(out, { recursive: true });
  for (const s of rest) writeFileSync(path.join(out, `t${(+s).toFixed(2).padStart(6, '0')}.png`), await grab(+s));
} else {
  const [f0, f1] = rest.map(Number);
  const ff = spawn('ffmpeg', ['-loglevel', 'error', '-y', '-f', 'image2pipe', '-framerate', '30', '-c:v', 'png', '-i', '-',
    '-c:v', 'libx264', '-preset', 'slow', '-crf', '14', '-pix_fmt', 'yuv420p', '-tune', 'grain', out], { stdio: ['pipe', 'inherit', 'inherit'] });
  const t0 = Date.now();
  for (let f = f0; f < f1; f++) {
    const buf = await grab(f / 30);
    if (!ff.stdin.write(buf)) await new Promise(r => ff.stdin.once('drain', r));
    if ((f - f0) % 60 === 0) console.log(`${out}: frame ${f} (${((Date.now() - t0) / 1000).toFixed(0)}s)`);
  }
  ff.stdin.end();
  await new Promise(r => ff.on('close', r));
}
await browser.close();
