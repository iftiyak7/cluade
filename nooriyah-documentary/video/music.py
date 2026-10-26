"""Synthesize the music bed and sound effects, synced to timeline.json.

Usage: python3 music.py <timeline.json> <out.wav>
Everything is generated from scratch (no samples), so there is nothing to license.
"""
import json
import subprocess
import sys

import numpy as np
from scipy import signal

SR = 48000
rs = np.random.default_rng(7)


def midi(n):
    return 440.0 * 2 ** ((n - 69) / 12)


def env_adsr(n, a, r):
    e = np.ones(n)
    na, nr = int(a * SR), int(r * SR)
    e[:na] = np.linspace(0, 1, na) ** 2
    e[-nr:] *= np.linspace(1, 0, nr) ** 2
    return e


def pad_voice(freq, n, detune):
    t = np.arange(n) / SR
    out = np.zeros(n)
    for d in (-detune, 0, detune):
        fr = freq * (1 + d)
        ph = rs.uniform(0, 2 * np.pi)
        for h in range(1, 7):  # soft saw: few harmonics, steep rolloff
            out += np.sin(2 * np.pi * fr * h * t + ph * h) / h ** 1.6
    return out / 3


def add(buf, start, x):
    i = int(start * SR)
    if i >= len(buf):
        return
    x = x[: len(buf) - i]
    buf[i:i + len(x)] += x


def lowpass(x, fc, order=2):
    b, a = signal.butter(order, fc / (SR / 2), "low")
    return signal.lfilter(b, a, x, axis=0)


def bandpass_sweep(noise, f0, f1, q=1.4):
    """Block-wise swept band-pass filter on noise."""
    out = np.zeros_like(noise)
    blk = int(0.01 * SR)
    nb = int(np.ceil(len(noise) / blk))
    zi = None
    for i in range(nb):
        fc = f0 * (f1 / f0) ** (i / max(nb - 1, 1))
        bw = fc / q
        lo, hi = max(fc - bw / 2, 30), min(fc + bw / 2, SR / 2 - 100)
        b, a = signal.butter(2, [lo / (SR / 2), hi / (SR / 2)], "band")
        if zi is None:
            zi = signal.lfilter_zi(b, a) * 0
        seg = noise[i * blk:(i + 1) * blk]
        y, zi = signal.lfilter(b, a, seg, zi=zi)
        out[i * blk:i * blk + len(seg)] = y
    return out


def whoosh(dur=1.1, up=True):
    n = int(dur * SR)
    x = rs.standard_normal(n)
    x = bandpass_sweep(x, 300 if up else 3000, 3000 if up else 300)
    t = np.linspace(0, 1, n)
    e = np.sin(np.pi * t) ** 2 * (t ** 0.6)
    return x * e * 0.5


def riser(dur):
    n = int(dur * SR)
    t = np.linspace(0, 1, n)
    x = bandpass_sweep(rs.standard_normal(n), 200, 6000, q=2.5) * t ** 2.2 * 0.6
    tone = np.sin(2 * np.pi * np.cumsum(110 + 330 * t ** 2) / SR) * t ** 3 * 0.12
    return x + tone


def impact():
    n = int(2.8 * SR)
    t = np.arange(n) / SR
    f = 38 + 50 * np.exp(-t * 18)
    boom = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 1.6) * 0.9
    crack = lowpass(rs.standard_normal(n), 2500) * np.exp(-t * 14) * 0.5
    return boom + crack


def pluck(freq, dur=1.6, vel=1.0):
    n = int(dur * SR)
    t = np.arange(n) / SR
    x = (np.sin(2 * np.pi * freq * t) + 0.35 * np.sin(2 * np.pi * 2 * freq * t) * np.exp(-t * 3)
         + 0.12 * np.sin(2 * np.pi * 3 * freq * t) * np.exp(-t * 6))
    e = np.exp(-t * 2.6) * np.minimum(1, t / 0.004)
    return x * e * 0.22 * vel


def chime(freq):
    n = int(5 * SR)
    t = np.arange(n) / SR
    x = np.zeros(n)
    for ratio, amp, dec in ((1, 1, 0.9), (2.76, 0.5, 1.6), (5.4, 0.3, 2.6), (8.93, 0.15, 3.5)):
        x += amp * np.sin(2 * np.pi * freq * ratio * t) * np.exp(-t * dec)
    return x * 0.18 * np.minimum(1, t / 0.003)


def soft_pop():
    n = int(0.25 * SR)
    t = np.arange(n) / SR
    f = 900 * np.exp(-t * 30) + 220
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 28) * 0.25


def main():
    tl = json.load(open(sys.argv[1]))
    dur = tl["duration"]
    N = int(dur * SR) + SR
    sc = {s["id"]: s for s in tl["scenes"]}
    A = lambda sid, li, ci=0: sc[sid]["lines"][li]["chunks"][ci]["start"]
    E = lambda sid, li: sc[sid]["lines"][li]["end"]

    music = np.zeros((N, 2))
    # Am - F - C - G, 8 s per chord, warm low voicing
    chords = [[45, 52, 57, 60, 64], [41, 48, 57, 60, 65], [48, 55, 60, 64, 67], [43, 50, 55, 59, 62]]
    bar = 8.0
    nb = int(np.ceil(dur / bar)) + 1
    for i in range(nb):
        ch = chords[i % 4]
        n = int((bar + 2.5) * SR)
        for j, m in enumerate(ch):
            v = pad_voice(midi(m), n, 0.0035) * env_adsr(n, 2.0, 2.5) * (0.5 if j == 0 else 0.3)
            pan = 0.5 + 0.35 * np.sin(j * 1.7)
            add(music[:, 0], i * bar, v * (1 - pan) * 2)
            add(music[:, 1], i * bar, v * pan * 2)
        sub = np.sin(2 * np.pi * midi(ch[0] - 12) * np.arange(n) / SR) * env_adsr(n, 2.0, 2.5) * 0.35
        add(music[:, 0], i * bar, sub)
        add(music[:, 1], i * bar, sub)
    music = lowpass(music, 1800)
    music *= 0.10

    # gentle plucked arpeggio from scene 2, thinning out for the reveal and outro
    arp = np.zeros((N, 2))
    step = 0.5
    pattern = [0, 2, 3, 4, 3, 2, 1, 2]
    t = sc["s2"]["start"]
    k = 0
    rev = A("s6", 1, 0)
    while t < dur - 4:
        quiet = (rev - 4.5 < t < rev + 1.5)
        if not quiet:
            ch = chords[int(t // bar) % 4]
            note = ch[pattern[k % 8]] + 12
            vel = 0.75 + 0.25 * (k % 4 == 0)
            if t > sc["s8"]["start"]:
                vel *= 0.7
            p = pluck(midi(note), vel=vel)
            pan = 0.5 + 0.3 * np.sin(k * 0.9)
            add(arp[:, 0], t, p * (1 - pan) * 2)
            add(arp[:, 1], t, p * pan * 2)
        t += step
        k += 1
    music += lowpass(arp, 4000) * 0.55

    sfx = np.zeros((N, 2))

    def put(t0, x, gain=1.0, width=0.0):
        add(sfx[:, 0], t0, x * gain * (1 - width))
        add(sfx[:, 1], t0, x * gain * (1 + width))

    for s in tl["scenes"][1:]:
        put(s["start"] - 0.75, whoosh(), 0.55, 0.15)
    # heartbeat build under the lifetime line, then riser -> impact on "1/12"
    hb = np.arange(int(0.5 * SR)) / SR
    beat = np.sin(2 * np.pi * (55 + 30 * np.exp(-hb * 25)) * hb) * np.exp(-hb * 9) * 0.6
    tb = A("s6", 0, 0) + 0.5
    while tb < rev - 0.6:
        put(tb, beat, 0.9)
        put(tb + 0.28, beat, 0.55)
        tb += 1.05
    put(rev - 2.6, riser(2.6), 0.7)
    put(rev, impact(), 1.0)
    # pops on key graphic reveals
    for tp in (A("s1", 1, 3), A("s2", 1, 3) + 0.5, A("s3", 1, 3) + 0.4, A("s4", 2, 0), A("s5", 2, 0) - 0.3,
               A("s5", 3, 1), A("s6", 2, 3), A("s7", 0, 2)):
        put(tp, soft_pop(), 0.8)
    for i in range(12):  # 12 bees popping in
        put(A("s6", 2, 1) + i * (A("s6", 2, 3) - A("s6", 2, 1)) / 12, soft_pop(), 0.35, (i % 2 - 0.5) * 0.4)
    # logo chime + shimmer swell
    l2 = A("s8", 1, 0)
    for j, m in enumerate((76, 81, 83, 88)):
        put(l2 + j * 0.09, chime(midi(m)), 0.9, (j - 1.5) * 0.15)
    put(l2 - 1.4, whoosh(1.6), 0.5)

    # gentle overall shape: fade in, lift at the reveal, fade out at the end
    tt = np.arange(N) / SR
    shape = np.clip(tt / 2.0, 0, 1) * np.clip((dur + 0.2 - tt) / 3.0, 0, 1)
    shape *= 1 + 0.35 * np.exp(-((tt - rev - 3) / 4) ** 2) + 0.4 * np.clip((tt - l2 + 2) / 2, 0, 1)
    out = music * shape[:, None] + sfx
    out = out[: int(dur * SR)]
    out *= 0.8 / np.abs(out).max()
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "f32le", "-ar", str(SR), "-ac", "2", "-i", "-", sys.argv[2]],
                   input=out.astype(np.float32).tobytes(), check=True)


if __name__ == "__main__":
    main()
