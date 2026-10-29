# NOORIYAH — Brand Introduction Film (Facebook / Instagram Reels)

The final video is `out/nooriyah-brand-film.mp4`: 1080×1920, 30 fps, H.264 High, AAC 48 kHz stereo, 78.5 s.

The film is built only from the two supplied assets:
- `assets/src/nooriyah-logo-original.jpg` (the original logo, composited as-is)
- `assets/src/voiceover-original.wav` (the final Bengali voice-over, used unedited)

Everything else is generated in code: an illustrated 2.5D motion-graphics world, Bengali typography and an original score. See `timeline.md` for the voice-over analysis and the scene-by-scene timing.

## Layout

| Path | What it is |
|------|------------|
| `film/index.html` | Frame-exact renderer page (Canvas 2D, fonts, scene scripts) |
| `film/core.js` | Timing, easing, palette, text animation, grain/vignette, logo compositing |
| `film/art.js` | Illustration primitives: jars, shelves, bowls, pouring liquid, seeds, leaves, figure |
| `film/scene-*.js` | One file per scene. All on-screen Bengali is plain text in these files and can be edited |
| `film/main.js` | Scene list, transitions, sub-frame motion blur |
| `render.mjs` | Headless Chromium → PNG frames → ffmpeg |
| `audio/mix.py` | Score + sound design + voice-over mix (numpy) |
| `assets/fonts/` | Hind Siliguri and Noto Serif Bengali (SIL OFL 1.1) |

## Re-render

```bash
# stills for review
node render.mjs stills /tmp/stills 7.5 20.6 48.5 76.5
# video (frame range), e.g. full film in one pass
node render.mjs video /tmp/picture.mp4 0 2355
python3 audio/mix.py assets/src/voiceover-original.wav out/nooriyah-mix.wav
ffmpeg -i /tmp/picture.mp4 -i out/nooriyah-mix.wav -map 0:v -map 1:a -c:v copy \
  -c:a aac -b:a 320k -ar 48000 -movflags +faststart -shortest out/nooriyah-brand-film.mp4
```

To change wording, edit the `textLine(...)` calls in the scene files. Each has its own `tIn`/`tOut` cue in seconds.
