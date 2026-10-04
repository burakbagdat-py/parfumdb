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

def render(name, dur, fn, gain=0.55, smooth=3, echo=None):
    n = int(RATE * dur)
    s = [fn(i / RATE) for i in range(n)]
    if echo:
        d, g = int(RATE * echo[0]), echo[1]
        for i in range(d, n): s[i] += s[i - d] * g
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

# ---------- Scandal set: a Paris cabaret after midnight ----------
TINE = ((1, 1.0), (2, .32), (4, .09), (7, .02))

def sc_kadife(t):
    # "Velvet": an electric-piano Db maj9 rolled upward, a single bell answer on top
    out = 0
    for i, f in enumerate((138.59, 207.65, 261.63, 311.13, 349.23)):
        out += partials(f, t - i * .045, TINE, .006, 1.25) * (1 - i * .06) * (1 + .06 * math.sin(2 * math.pi * 5.2 * t))
    out += partials(830.61, t - .42, ((1, 1), (3, .08)), .004, .9) * .38
    out += partials(1046.5, t - .62, ((1, 1), (3, .06)), .004, .8) * .22
    return out

random.seed(11)
_BUBBLES = [(.07 + random.random() ** 1.6 * 1.25, 2400 + random.random() * 3600, .05 + random.random() * .09) for _ in range(70)]

def sc_sampanya(t):
    # "Champagne": the cork, the fizz, then two flutes touching
    out = 0
    if t < .09:
        out += math.sin(2 * math.pi * (190 - 1100 * t) * t) * math.exp(-t / .022) * .9 + (random.random() * 2 - 1) * math.exp(-t / .012) * .35
    for s0, f, a in _BUBBLES:
        x = t - s0
        if 0 <= x < .05: out += a * math.sin(2 * math.pi * (f + 9000 * x) * x) * math.exp(-x / .009) * (1 - s0 / 1.5)
    def glass(x, f):
        if x < 0: return 0.0
        return min(1, x / .002) * math.exp(-x / .75) * (math.sin(2 * math.pi * f * x) + .4 * math.sin(2 * math.pi * f * 2.32 * x) * math.exp(-x * 3) + .45 * math.sin(2 * math.pi * (f + 4) * x))
    return out + glass(t - .62, 1760.0) * .5 + glass(t - .8, 2093.0) * .34

def sc_topuk(t):
    # "Heels": two stiletto steps, a plucked double-bass slide, a finger snap and one vibraphone wink
    out = 0
    for s0, a in ((0, 1), (.17, .8)):
        x = t - s0
        if 0 <= x < .08: out += a * (math.sin(2 * math.pi * 1750 * x) * math.exp(-x / .006) * .6 + math.sin(2 * math.pi * 820 * x) * math.exp(-x / .012) * .5 + (random.random() * 2 - 1) * math.exp(-x / .004) * .25)
    x = t - .38
    if x >= 0:
        f = 73.42 if x < .3 else 73.42 + (98.0 - 73.42) * min(1, (x - .3) / .08)
        out += min(1, x / .005) * math.exp(-x / .55) * (math.sin(2 * math.pi * f * x) + .5 * math.sin(4 * math.pi * f * x) * math.exp(-x * 4) + .2 * math.sin(6 * math.pi * f * x) * math.exp(-x * 8)) * .9
    x = t - .7
    if 0 <= x < .06: out += (random.random() * 2 - 1) * math.exp(-x / .008) * .5 + math.sin(2 * math.pi * 2300 * x) * math.exp(-x / .01) * .3
    out += partials(587.33, t - .98, ((1, 1), (4, .12)), .004, .8) * .4 + partials(739.99, t - 1.0, ((1, 1), (4, .1)), .004, .8) * .32
    return out

MUSICBOX = ((1, 1.0), (3.0, .22), (5.4, .08))

def sc_muzik(t):
    # "Music box": a small falling-then-rising phrase in F# minor, like a jewellery box opened in the dark
    out = 0
    for i, f in enumerate((1479.98, 1318.51, 1108.73, 1318.51, 1661.22, 1479.98, 1108.73, 1760.0)):
        out += partials(f, t - i * .17, MUSICBOX, .002, .55) * (.7 if i % 2 else .9)
    out += partials(277.18, t - .02, ((1, 1), (2, .2)), .02, 1.4) * .25
    return out

def sc_altin(t):
    # "Gold": a harp glissando up Db pentatonic that lands on a shimmering top note; for good news (price drops)
    out = 0
    for i, f in enumerate((415.3, 466.16, 554.37, 622.25, 698.46, 830.61, 932.33, 1108.73, 1244.51)):
        out += partials(f, t - i * .055, ((1, 1), (2, .3), (3, .1)), .003, .75) * (.55 + i * .05)
    for s0, f in ((.5, 3322.4), (.58, 4186.0), (.66, 3729.3), (.74, 4434.9)):
        out += math.sin(2 * math.pi * f * (t - s0)) * env(t - s0, .002, .12) * .08
    out += partials(138.59, t - .5, ((1, 1), (2, .25)), .03, 1.0) * .32
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
    random.seed(21)
    render('sc_kadife', 2.8, sc_kadife, .5, 3, (.24, .26))
    render('sc_sampanya', 2.3, sc_sampanya, .5, 2)
    render('sc_topuk', 2.2, sc_topuk, .52, 2, (.19, .14))
    render('sc_muzik', 2.9, sc_muzik, .42, 2, (.23, .3))
    render('sc_altin', 2.2, sc_altin, .46, 2, (.21, .22))
    print('ok')
