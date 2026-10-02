"""Synthesizes the app's notification sounds into android/app/src/main/res/raw/*.wav."""
import math, os, random, struct, wave

RATE = 22050
OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'android', 'app', 'src', 'main', 'res', 'raw')

def env(t, a, d):
    return min(1.0, t / a) * math.exp(-t / d) if a > 0 else math.exp(-t / d)

def tone(f, t, partials=((1, 1.0),), a=0.004, d=0.4):
    return env(t, a, d) * sum(w * math.sin(2 * math.pi * f * k * t) for k, w in partials)

def render(name, dur, fn, gain=0.8):
    n = int(RATE * dur)
    s = [fn(i / RATE) for i in range(n)]
    peak = max(abs(x) for x in s) or 1
    fade = int(RATE * 0.03)
    with wave.open(os.path.join(OUT, name + '.wav'), 'wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(RATE)
        frames = bytearray()
        for i, x in enumerate(s):
            if i > n - fade: x *= (n - i) / fade
            frames += struct.pack('<h', int(x / peak * gain * 32767))
        w.writeframes(bytes(frames))

def kristal(t):  # glass arpeggio
    notes = [(0.0, 1318.5), (0.11, 1661.2), (0.22, 1975.5), (0.33, 2637.0)]
    bell = ((1, 1), (2.76, .25), (5.4, .08))
    return sum(tone(f, t - s, bell, d=0.55) for s, f in notes if t >= s)

def gemi_cani(t):  # two strikes of a ship's bell
    bell = ((1, 1), (2.0, .5), (2.9, .3), (4.2, .12), (5.4, .06))
    return sum(tone(587.3, t - s, bell, a=.002, d=.9) for s in (0.0, 0.42) if t >= s)

def robot(t):  # square-ish synth blips
    seq = [(0.0, 880), (0.09, 1174.7), (0.18, 1760), (0.30, 1318.5)]
    out = 0
    for s, f in seq:
        if s <= t < s + 0.08:
            x = t - s
            out += math.copysign(1, math.sin(2 * math.pi * f * x)) * 0.35 * math.exp(-x / 0.05)
    return out

random.seed(7)
NOISE = [random.uniform(-1, 1) for _ in range(RATE)]
def sprey(t):  # perfume spray: filtered noise burst, twice
    out = 0
    for s in (0.0, 0.32):
        x = t - s
        if 0 <= x < 0.26:
            i = int(t * RATE) % RATE
            hp = NOISE[i] - NOISE[i - 1]
            out += hp * min(1, x / 0.015) * math.exp(-x / 0.09)
    return out

def damla(t):  # water drop: fast upward pitch sweep
    out = 0
    for s, f0 in ((0.0, 700), (0.22, 950)):
        x = t - s
        if x >= 0:
            f = f0 + 1600 * min(x, 0.06) / 0.06
            out += math.sin(2 * math.pi * f * x) * env(x, .002, .07)
    return out

def nabiz(t):  # soft double pulse
    return sum(tone(523.25, t - s, ((1, 1), (2, .2)), a=.02, d=.18) for s in (0.0, 0.28) if t >= s)

if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    render('kristal', 1.6, kristal)
    render('gemi_cani', 2.6, gemi_cani)
    render('robot', 0.6, robot, 0.6)
    render('sprey', 0.7, sprey, 0.7)
    render('damla', 0.7, damla)
    render('nabiz', 0.9, nabiz)
    print('ok')
