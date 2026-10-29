"""NOORIYAH brand film soundtrack.

Builds an original, synthesised ambient score and restrained sound design, then mixes it
under the supplied voice-over. The voice-over is only resampled to 48 kHz and placed in the
centre at unity gain: it is never edited, re-timed, EQ'd or compressed.

Usage: python3 mix.py <voiceover.wav> <out.wav>
"""
import sys
import subprocess
import numpy as np

SR = 48000
DUR = 78.5
N = int(SR * DUR)
rng = np.random.default_rng(2026)
t_all = np.arange(N) / SR


def load_vo(path):
    raw = subprocess.run(['ffmpeg', '-loglevel', 'error', '-i', path, '-ac', '1', '-ar', str(SR),
                          '-f', 'f32le', '-'], capture_output=True, check=True).stdout
    vo = np.frombuffer(raw, dtype=np.float32).astype(np.float64)
    out = np.zeros(N)
    out[:min(N, len(vo))] = vo[:N]
    return out


def midi(m):
    return 440.0 * 2 ** ((m - 69) / 12)


def env_adsr(n, a, r, sustain_end):
    e = np.ones(n)
    ia, ir = int(a * SR), int(r * SR)
    e[:ia] = np.linspace(0, 1, ia) ** 2
    se = int(sustain_end * SR)
    if se < n:
        e[se:se + ir] *= np.linspace(1, 0, min(ir, n - se)) ** 2
        e[se + ir:] = 0
    return e


def place(buf, sig, start, gain=1.0, pan=0.0):
    i = int(start * SR)
    if i >= N:
        return
    sig = sig[:N - i]
    l, r = np.cos((pan + 1) * np.pi / 4), np.sin((pan + 1) * np.pi / 4)
    buf[0, i:i + len(sig)] += sig * gain * l
    buf[1, i:i + len(sig)] += sig * gain * r


def pad_note(f, dur, bright=1.0):
    n = int((dur + 2.5) * SR)
    t = np.arange(n) / SR
    s = np.zeros(n)
    for det in (-0.006, 0.0, 0.0055):
        ph = rng.uniform(0, 2 * np.pi)
        for h in range(1, 10):
            fh = f * h * (1 + det)
            if fh > 4000 * bright:
                break
            s += np.sin(2 * np.pi * fh * t + ph * h) / h ** 1.7
    s *= 1 + 0.08 * np.sin(2 * np.pi * 0.21 * t + rng.uniform(0, 6))  # slow breathing
    return s * env_adsr(n, 1.8, 2.4, dur) / 3


def piano(f, vel=0.6, length=4.0):
    n = int(length * SR)
    t = np.arange(n) / SR
    s = np.zeros(n)
    B = 0.0004
    for k in range(1, 9):
        fk = f * k * np.sqrt(1 + B * k * k)
        if fk > 9000:
            break
        s += np.sin(2 * np.pi * fk * t) * (vel ** (k * 0.35)) / k ** 1.25 * np.exp(-t * (0.9 + 0.55 * k))
    att = np.minimum(1, t / 0.006)
    return s * att * vel * 0.5


def pluck(f, vel=0.5):
    n = int(1.4 * SR)
    t = np.arange(n) / SR
    s = (np.sin(2 * np.pi * f * t) + 0.25 * np.sin(2 * np.pi * 3.01 * f * t) * np.exp(-t * 9)) * np.exp(-t * 3.2)
    return s * np.minimum(1, t / 0.003) * vel * 0.4


def onepole_lp(x, fc):
    a = np.exp(-2 * np.pi * fc / SR)
    y = np.empty_like(x)
    acc = 0.0
    for i in range(len(x)):
        acc = (1 - a) * x[i] + a * acc
        y[i] = acc
    return y


def fft_lp(x, fc, order=2):
    X = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    return np.fft.irfft(X / (1 + (f / fc) ** (2 * order)) ** .5, len(x))


def fft_bp(x, lo, hi):
    X = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    h = 1 / np.sqrt(1 + (lo / np.maximum(f, 1)) ** 4) / np.sqrt(1 + (f / hi) ** 4)
    return np.fft.irfft(X * h, len(x))


def reverb(buf, seconds=3.2, wet=0.32):
    n = int(seconds * SR)
    t = np.arange(n) / SR
    out = np.zeros_like(buf)
    L = 1 << int(np.ceil(np.log2(N + n)))
    for ch in range(2):
        ir = rng.standard_normal(n) * np.exp(-t * 6.9 / seconds)
        ir = fft_lp(ir, 5200)
        ir[:int(0.012 * SR)] = 0  # pre-delay
        ir /= np.sqrt(np.sum(ir ** 2))
        y = np.fft.irfft(np.fft.rfft(buf[ch], L) * np.fft.rfft(ir, L), L)[:N]
        out[ch] = buf[ch] * (1 - wet) + y * wet * 1.6
    return out


# ---------------- score ----------------
def score():
    mus = np.zeros((2, N))
    # chord plan (MIDI notes), times locked to the story beats
    D, Bm, G, A, Em, Gm6 = [50, 57, 61, 66], [47, 54, 62, 66], [43, 55, 59, 66], [45, 57, 61, 64], [40, 55, 59, 62], [43, 58, 62, 64]
    Dadd9 = [50, 57, 64, 66, 69]
    plan = [
        # problem: unresolved, pensive (minor colours, no tonic)
        (0.0, Bm, .55), (4.6, G, .55), (9.2, Em, .6), (13.8, G, .6), (16.4, A, .5),
        # NOORIYAH's beginning: first arrival on the tonic
        (19.55, D, .8), (23.0, Bm, .75), (26.95, G, .75), (30.0, A, .75),
        # product range
        (32.95, D, .85), (35.7, G, .85), (38.4, Bm, .85), (41.4, A, .85),
        # Jashore outlet
        (44.0, G, .8), (47.6, D, .9), (51.0, Bm, .85),
        # trust: warmer, slower harmonic rhythm
        (54.45, G, .75), (57.9, D, .8), (61.4, Em, .8), (65.2, G, .85), (67.9, A, .85),
        # brand signature: home
        (69.9, Dadd9, 1.0),
    ]
    for i, (st, ch, br) in enumerate(plan):
        end = plan[i + 1][0] if i + 1 < len(plan) else DUR - 2.2
        for j, m in enumerate(ch):
            place(mus, pad_note(midi(m), end - st + .4, br), st, 0.055 * (0.8 if j == 0 else 0.55), pan=(j - 1.5) * 0.25)
        # sub-octave root for warmth
        place(mus, pad_note(midi(ch[0] - 12), end - st + .4, .4), st, 0.04)

    # felt-piano motif from the brand reveal onwards
    motif = [(0, 74), (.86, 73), (1.71, 69), (2.57, 66)]
    starts = [19.6, 26.95, 47.65, 57.95, 65.5]
    for s0 in starts:
        for dt, m in motif:
            place(mus, piano(midi(m), 0.42), s0 + dt, 0.5, pan=0.15)
    # sparse opening notes (the question)
    for s0, m in [(0.55, 66), (2.45, 62), (6.05, 64), (7.45, 66), (11.45, 62), (12.9, 64), (14.5, 66)]:
        place(mus, piano(midi(m), 0.3), s0, 0.42, pan=-0.1)
    # gentle pulse of plucks under the product range (one category per beat group)
    beat = 60 / 84
    t0 = 33.0
    arp = [62, 66, 69, 74, 69, 66]
    k = 0
    while t0 < 44.2:
        place(mus, pluck(midi(arp[k % len(arp)] + 12), 0.5), t0, 0.22, pan=0.35 if k % 2 else -0.35)
        t0 += beat / 2
        k += 1
    # final resolution: low D and a last high note, then silence by the end
    place(mus, piano(midi(38), 0.55, 6.0), 74.02, 0.55)
    place(mus, piano(midi(62), 0.5, 6.0), 74.02, 0.4)
    place(mus, piano(midi(78), 0.4, 6.0), 74.85, 0.35, pan=0.2)
    for s0, m in [(70.2, 69), (71.0, 73), (71.9, 74)]:
        place(mus, piano(midi(m), 0.38), s0, 0.42, pan=0.1)
    mus = reverb(mus, 3.4, 0.38)
    return mus


# ---------------- sound design ----------------
def noise(sec):
    return rng.standard_normal(int(sec * SR))


def swell(sec, lo, hi, peak=.6):
    n = int(sec * SR)
    s = fft_bp(noise(sec), lo, hi)
    e = np.sin(np.pi * np.minimum(1, np.arange(n) / (n * peak)) / 2) ** 2 * np.linspace(1, 0, n) ** 1.5
    return s * e / (np.max(np.abs(s)) + 1e-9)


def tap(f1, f2, sec=.5):
    t = np.arange(int(sec * SR)) / SR
    return (np.sin(2 * np.pi * f1 * t) * .6 + np.sin(2 * np.pi * f2 * t) * .4) * np.exp(-t * 16)


def sfx():
    s = np.zeros((2, N))
    # soft air movement on major transitions (no hard impacts)
    for t0, g in [(9.15, .05), (18.85, .06), (26.9, .045), (32.9, .045), (43.95, .04), (46.45, .06), (54.4, .04), (69.65, .06)]:
        place(s, swell(1.1, 300, 2600), t0, g, pan=-.2)
        place(s, swell(1.1, 300, 2600), t0 + .03, g, pan=.2)
    # market room tone with a distant murmur bed (0–19 s)
    amb = fft_bp(noise(19.6), 120, 900)
    amb /= np.max(np.abs(amb))
    amb *= np.minimum(1, np.arange(len(amb)) / (0.4 * SR)) * np.minimum(1, (len(amb) - np.arange(len(amb))) / (1.5 * SR))
    place(s, amb, 0, .018, -.3)
    place(s, amb[::-1].copy(), 0, .018, .3)
    # honey: one soft low drop as the stream meets the bowl
    tt = np.arange(int(.4 * SR)) / SR
    place(s, np.sin(2 * np.pi * (180 + 300 * np.exp(-tt * 30)) * tt) * np.exp(-tt * 14), 33.6, .05)
    # wooden ghani: slow grinding rustle
    gr = fft_bp(noise(1.45), 500, 4000) * (0.6 + 0.4 * np.sin(2 * np.pi * 1.1 * np.arange(int(1.45 * SR)) / SR))
    place(s, gr / np.max(np.abs(gr)) * np.hanning(len(gr)), 34.85, .03)
    # oil pours: gentle trickle
    for t0, d in [(36.3, 1.55), (37.95, 1.2)]:
        tr = fft_bp(noise(d), 900, 3800)
        tr *= np.hanning(len(tr))
        place(s, tr / np.max(np.abs(tr)), t0, .025, .1)
    # seeds and nuts: light rattles
    for t0 in [39.3, 39.6, 40.62, 40.9]:
        place(s, swell(.35, 2500, 9000, .1), t0, .02, rng.uniform(-.4, .4))
    # outlet: warm room tone + one soft glass jar tap when it is picked up
    rt = fft_bp(noise(7.6), 150, 1200)
    rt = rt / np.max(np.abs(rt)) * np.minimum(1, np.arange(len(rt)) / SR) * np.minimum(1, (len(rt) - np.arange(len(rt))) / SR)
    place(s, rt, 47.2, .016, -.2)
    place(s, tap(2200, 3350), 51.0, .03, .25)
    return s


def main():
    vo = load_vo(sys.argv[1])
    mus = score()
    fx = sfx()
    # duck the bed under the voice (envelope follower, 30 ms attack / 400 ms release)
    lvl = np.sqrt(np.convolve(vo ** 2, np.ones(int(.05 * SR)) / int(.05 * SR), mode='same'))
    act = np.clip((20 * np.log10(lvl + 1e-6) + 45) / 15, 0, 1)
    sm = np.empty_like(act)
    a_att, a_rel, acc = np.exp(-1 / (.03 * SR)), np.exp(-1 / (.4 * SR)), 0.0
    for i in range(0, N, 32):  # control-rate smoothing
        v = act[i]
        acc = (a_att if v > acc else a_rel) ** 32 * (acc - v) + v
        sm[i:i + 32] = acc
    duck = 10 ** (-5.5 * sm / 20) * 10 ** (4 / 20)  # bed sits ~15 dB under the voice
    bed = (mus + fx) * duck
    # final fade of the bed over the clean hold
    fade = np.ones(N)
    fs = int((DUR - 2.4) * SR)
    fade[fs:] = np.linspace(1, 0, N - fs) ** 1.5
    bed *= fade
    out = bed + vo[None, :] * 0.7071 * np.sqrt(2)  # voice centred, unity gain per channel
    peak = np.max(np.abs(out))
    print(f'peak before safety: {20 * np.log10(peak):.2f} dBFS; bed rms {20 * np.log10(np.sqrt(np.mean(bed ** 2)) + 1e-9):.1f} dBFS')
    if peak > 0.97:  # safety: scale the bed only, never the voice
        print('warning: peak near full scale')
    pcm = (np.clip(out, -1, 1).T * 32767).astype('<i2')
    import wave
    with wave.open(sys.argv[2], 'wb') as w:
        w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    # stems for editing
    for name, sig in (('music', mus * duck * fade), ('sfx', fx * duck * fade)):
        p = (np.clip(sig, -1, 1).T * 32767).astype('<i2')
        with wave.open(sys.argv[2].replace('.wav', f'-{name}-stem.wav'), 'wb') as w:
            w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR)
            w.writeframes(p.tobytes())


if __name__ == '__main__':
    main()
