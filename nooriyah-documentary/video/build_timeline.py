"""Lay voiceover lines on a timeline and emit timeline.json + vo.wav.

Usage: python3 build_timeline.py <vo_dir> <out_dir>
<vo_dir> holds processed per-line WAVs named <scene>_<index>.wav (48 kHz mono).
"""
import json
import subprocess
import sys
from pathlib import Path

import numpy as np

SR = 48000
LEAD_IN = 1.2          # silence before the first line
LINE_GAP = 0.35        # between lines of one scene
SCENE_GAP = 0.9        # between scenes
DRAMATIC = {("s6", 1): 1.3, ("s8", 1): 0.5, ("s8", 2): 0.5}  # extra pause before these lines
TAIL = 5.0             # logo hold after the last line


def read_wav(path):
    raw = subprocess.run(
        ["ffmpeg", "-v", "error", "-i", str(path), "-f", "f32le", "-ac", "1", "-ar", str(SR), "-"],
        check=True, capture_output=True).stdout
    return np.frombuffer(raw, dtype=np.float32)


def spoken(line):
    """The TTS text: script lines mark subtitle breaks with ' / '."""
    return line.replace(" / ", " ")


def chunk_words(line):
    """Subtitle chunks, split at the hand-placed ' / ' markers."""
    return [c.strip() for c in line.split(" / ")]


def main():
    vo_dir, out_dir = Path(sys.argv[1]), Path(sys.argv[2])
    script = json.loads((Path(__file__).parent / "script.json").read_text())
    t = LEAD_IN
    pieces, scenes = [], []
    for si, scene in enumerate(script):
        if si:
            t += SCENE_GAP
        scene_start = t if si else 0.0
        lines = []
        for li, text in enumerate(scene["lines"]):
            if li:
                t += LINE_GAP
            t += DRAMATIC.get((scene["id"], li), 0.0)
            audio = read_wav(vo_dir / f"{scene['id']}_{li}.wav")
            dur = len(audio) / SR
            pieces.append((t, audio))
            chunks = chunk_words(text)
            weights = [len(c.replace(" ", "")) + 2 for c in chunks]
            total = sum(weights)
            ct, subs = t, []
            for c, w in zip(chunks, weights):
                d = dur * w / total
                subs.append({"text": c.rstrip("।,?"), "start": round(ct, 3), "end": round(ct + d, 3)})
                ct += d
            lines.append({"text": spoken(text), "start": round(t, 3), "end": round(t + dur, 3), "chunks": subs})
            t += dur
        scenes.append({"id": scene["id"], "title": scene["title"], "start": round(scene_start, 3),
                       "lines": lines})
    total = t + TAIL
    for a, b in zip(scenes, scenes[1:]):
        a["end"] = round(b["start"] - SCENE_GAP / 2, 3)
        b["start"] = a["end"]
    scenes[-1]["end"] = round(total, 3)

    track = np.zeros(int(total * SR) + 1, dtype=np.float32)
    for start, audio in pieces:
        i = int(start * SR)
        track[i:i + len(audio)] += audio
    peak = np.abs(track).max()
    track *= 0.89 / peak
    out_dir.mkdir(parents=True, exist_ok=True)
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "f32le", "-ar", str(SR), "-ac", "1", "-i", "-",
                    str(out_dir / "vo.wav")], input=track.tobytes(), check=True)
    (out_dir / "timeline.json").write_text(
        json.dumps({"duration": round(total, 3), "scenes": scenes}, ensure_ascii=False, indent=1))
    print(f"duration {total:.1f}s")


if __name__ == "__main__":
    main()
