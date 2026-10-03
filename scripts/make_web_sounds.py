"""Synthesizes downloadable notification sounds into docs/sounds/*.wav.

The Android app (v4+) downloads these on demand, so new sounds need no new APK.
All sounds are kept soft: slow-ish attacks, few high partials, gentle peak level.
"""
import math, os, random, struct, wave

RATE = 22050
OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'docs', 'sounds')

def env(t, a, d):
    if t < 0: return 0.0
    return min(1.0, t / a) * math.exp(-t / d)

def partials(f, t, parts, a, d):
    return env(t, a, d) * sum(w * math.sin(2 * math.pi * f * k * t) * math.exp(-t * k * 0.6) for k, w in parts)

def render(name, dur, fn, gain=0.55, smooth=3):
    n = int(RATE * dur)
    s = [fn(i / RATE) for i in range(n)]
    # gentle low-pass (moving average) to take the edge off
    if smooth > 1:
        acc, out = 0.0, []
        for i, x in enumerate(s):
            acc += x
            if i >= smooth: acc -= s[i - smooth]
            out.append(acc / smooth)
        s = out
    peak = max(abs(x) for x in s) or 1
    fade = int(RATE * 0.06)
    with wave.open(os.path.join(OUT, name + '.wav'), 'wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(RATE)
        frames = bytearray()
        for i, x in enumerate(s):
            if i > n - fade: x *= (n - i) / fade
            frames += struct.pack('<h', int(x / peak * gain * 32767))
        w.writeframes(bytes(frames))

MARIMBA = ((1, 1.0), (4.0, 0.12), (9.2, 0.03))

def bb_yumusak(t):
    # "B B": two soft marimba notes (B3, B4), then a quiet F#5 that resolves the motif
    out = partials(246.94, t, MARIMBA, .008, .45) * .9
    out += partials(493.88, t - .22, MARIMBA, .008, .5)
    out += partials(739.99, t - .5, ((1, 1), (2, .1)), .02, .55) * .35
    return out

def kadeh(t):
    # two crystal glasses touching: soft inharmonic ring with slow beating, a second lighter touch
    def glass(x, f):
        if x < 0: return 0.0
        e = min(1, x / .002) * math.exp(-x / .7)
        return e * (math.sin(2 * math.pi * f * x) + .45 * math.sin(2 * math.pi * f * 2.32 * x) * math.exp(-x * 3)
                    + .5 * math.sin(2 * math.pi * (f + 3.5) * x))
    return glass(t, 1318.5) * .8 + glass(t - .19, 1568.0) * .45

def vanilya(t):
    # warm harp arpeggio: B3 D#4 F#4 B4
    out = 0
    for i, f in enumerate((246.94, 311.13, 369.99, 493.88)):
        out += partials(f, t - i * .11, ((1, 1), (2, .35), (3, .12)), .004, .9) * (1 - i * .08)
    return out

def gumus(t):
    # silver celesta: E6 then B5, sine bells with a faint shimmer
    out = partials(1318.5, t, ((1, 1), (3, .06)), .006, .55) * .7
    out += partials(987.77, t - .2, ((1, 1), (3, .06)), .006, .75)
    out += math.sin(2 * math.pi * 2637 * t) * env(t - .2, .01, .3) * .05
    return out

def fiyat(t):
    # price drop: a rising three-note chime (G5 B5 E6) with a light coin shimmer, then a soft confirming low note
    out = 0
    for i, f in enumerate((783.99, 987.77, 1318.51)):
        out += partials(f, t - i * .09, ((1, 1), (2.01, .25), (3.02, .08)), .003, .5) * (.75 + i * .1)
    for s, f in ((.32, 2637.0), (.38, 3135.96), (.45, 2793.83)):
        out += math.sin(2 * math.pi * f * (t - s)) * env(t - s, .002, .09) * .12
    out += partials(329.63, t - .3, ((1, 1), (2, .2)), .02, .6) * .4
    return out

def kumbara(t):
    # coin dropped into a piggy bank (three bright clinks bouncing), then the till opens: a soft thunk and a two-bell "ka-ching"
    def clink(x, f, a):
        if x < 0: return 0.0
        e = min(1, x / .0008) * math.exp(-x / .07)
        return a * e * (math.sin(2 * math.pi * f * x) + .6 * math.sin(2 * math.pi * f * 1.47 * x) + .35 * math.sin(2 * math.pi * f * 2.13 * x))
    out = 0
    for s0, f, a in ((0, 3520, 1), (.11, 3730, .7), (.19, 3420, .5), (.245, 3600, .32), (.28, 3500, .2)):
        out += clink(t - s0, f, a) * .5
    x = t - .42
    if 0 <= x < .12:
        out += math.sin(2 * math.pi * 95 * x) * math.exp(-x / .03) * .55 + (random.random() * 2 - 1) * math.exp(-x / .015) * .12
    out += partials(1567.98, t - .46, ((1, 1), (2.76, .18), (5.4, .05)), .002, .45) * .55
    out += partials(2093.0, t - .54, ((1, 1), (2.76, .18), (5.4, .05)), .002, .55) * .5
    return out

if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    random.seed(3)
    render('bb_yumusak', 1.6, bb_yumusak, .5)
    render('kadeh', 2.2, kadeh, .45, 2)
    render('vanilya', 2.2, vanilya, .5)
    render('gumus', 1.8, gumus, .45)
    render('fiyat', 1.8, fiyat, .5)
    render('kumbara', 1.7, kumbara, .5, 2)
    print('ok')
