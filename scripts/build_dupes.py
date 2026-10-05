#!/usr/bin/env python3
"""Which perfume is a clone of which: docs/data/dupes.json

A clone house (Lattafa, Armaf, Maison Alhambra, Zara ...) rarely says what it copied, so the answer is worked out from
the catalogue itself: for every perfume of such a house, the better-known perfume of an ordinary house that came out
earlier and whose notes and accords overlap most. The measure is the one the app uses for "Benzer" (rare notes weigh
more, heart and base weigh more than the opening). Pairs everyone knows are listed by hand and always win.

Output: {"v": date, "d": {clone id: [original id, percent, 1 if listed by hand]}}
Run:    python scripts/build_dupes.py            (add --check to print how the hand-listed pairs score)
"""
import datetime, json, math, os, re, sys, unicodedata

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
CAT = os.path.join(ROOT, 'docs', 'data', 'catalog.json')
OUT = os.path.join(ROOT, 'docs', 'data', 'dupes.json')

# houses whose catalogue is mostly "inspired by" perfumes
CLONE_HOUSES = {
    'Lattafa Perfumes', 'Maison Alhambra', 'PARIS CORNER', 'Fragrance World', 'Armaf', 'French Avenue', 'Alexandria Fragrances',
    'Zimaya', 'Afnan', 'Khadlaj Perfumes', 'Arabiyat Prestige', 'Rayhaan', 'Ard Al Zaafaran', 'Riiffs Perfumes', 'MAISON ASRAR',
    'Al Wataniah', 'Asdaaf', 'Zara', 'Dossier', 'La Rive', 'Paris Elysees', 'In The Box', 'Navitus Parfums', 'Superz.',
    'Rasasi', 'Al Haramain Perfumes', 'Ahmed Al Maghribi', 'Emper', 'Milestone Perfumes', 'Pendora Scents', 'Dumont', 'Vurv',
    'Ajmal', 'Swiss Arabian', 'Nabeel', 'Flavia', 'Camara', 'Sapil', 'Chatler', 'Bi-es', 'Jean Marc', 'Lomani',
}
# pairs that are common knowledge: (clone house, clone name) -> (house, name) of the original
KNOWN = [
    ('Armaf', 'Club de Nuit Intense Man', 'Creed', 'Aventus'),
    ('Rasasi', 'La Yuqawam Homme', 'Tom Ford', 'Tuscan Leather'),
    ('Armaf', 'Club de Nuit Untold', 'Maison Francis Kurkdjian', 'Baccarat Rouge 540'),
    ('Armaf', 'Club de Nuit Blue Iconic', 'Chanel', 'Bleu de Chanel Eau de Parfum'),
    ('Armaf', 'Club de Nuit Lionheart Man', 'Jean Paul Gaultier', 'Le Male Elixir'),
    ('Armaf', 'Odyssey Mandarin Sky', 'Jean Paul Gaultier', 'Scandal Pour Homme'),
    ('Lattafa Perfumes', 'Khamrah', 'By Kilian', "Angels' Share"),
    ('Lattafa Perfumes', 'Asad', 'Dior', 'Sauvage Elixir'),
    ('Lattafa Perfumes', 'Fakhar Black', 'Yves Saint Laurent', 'Y Eau de Parfum'),
    ('Lattafa Perfumes', "Bade'e Al Oud Oud for Glory", 'Initio Parfums Prives', 'Oud for Greatness'),
    ('Lattafa Perfumes', 'Ana Abiyedh Rouge', 'Maison Francis Kurkdjian', 'Baccarat Rouge 540'),
    ('Afnan', '9pm', 'Jean Paul Gaultier', 'Ultra Male'),
    ('Afnan', 'Supremacy Silver', 'Creed', 'Aventus'),
    ('Afnan', 'Turathi Blue', 'Bvlgari', 'Tygar'),
    ('Al Haramain Perfumes', "L'Aventure", 'Creed', 'Aventus'),
    ('Al Haramain Perfumes', 'Amber Oud Gold Edition', 'Maison Francis Kurkdjian', 'Baccarat Rouge 540 Extrait de Parfum'),
    ('Maison Alhambra', 'Baroque Rouge 540', 'Maison Francis Kurkdjian', 'Baccarat Rouge 540'),
    ('Maison Alhambra', 'Delilah', 'Parfums de Marly', 'Delina'),
    ('Maison Alhambra', 'Salvo', 'Dior', 'Sauvage'),
    ('Maison Alhambra', 'Kismet Angel', 'By Kilian', "Angels' Share"),
    ('Maison Alhambra', 'Jean Lowe Immortal', 'Louis Vuitton', "L'Immensité"),
    ('Maison Alhambra', 'Jorge Di Profumo', 'Giorgio Armani', 'Acqua di Giò Profumo'),
    ('Maison Alhambra', 'Philos Pura', 'Xerjoff', 'Erba Pura'),
    ('French Avenue', 'Liquid Brun', 'Parfums de Marly', 'Althaïr'),
    ('Zimaya', 'Sharaf Blend', 'By Kilian', "Angels' Share"),
]
# clone ids the app already listed before this file existed (kept so nothing it showed disappears)
LEGACY = {34696: 9828, 40405: 9828, 27352: 9828, 63062: 33519, 78476: 33519, 89849: 33519, 75805: 62615, 64948: 53641, 90273: 43871,
          72821: 68415, 93538: 31861, 70465: 50757, 65414: 30947, 94713: 84109, 78475: 25967, 102026: 81642}

TIER_W = [.7, 1, 1.15]
FAM_W = [1, .8, .6, .45, .35]


def fold(s):
    s = (s or '').replace('ı', 'i').replace('İ', 'i').replace('’', "'")
    return ''.join(c for c in unicodedata.normalize('NFD', s) if not unicodedata.combining(c)).lower()


def nb(s):
    s = fold(s).replace('&', 'and')
    s = re.sub(r'\bparfums?\b|\bperfumes?\b|\bfragrances?\b|\bparis\b', '', s)
    return re.sub(r'[^a-z0-9]', '', s)


def main():
    items = json.load(open(CAT, encoding='utf-8'))['items']
    N = len(items)
    df = {}
    for r in items:
        for n in {fold(x) for t in r[9:12] for x in t}:
            df[n] = df.get(n, 0) + 1

    def nvec(r):
        v = {}
        for t in range(3):
            for n in r[9 + t]:
                k = fold(n)
                if not k: continue
                w = math.log(N / (df.get(k, 0) + 1)) * TIER_W[t]
                if w > v.get(k, 0): v[k] = w
        return v, math.sqrt(sum(w * w for w in v.values()))

    def fvec(r):
        v = {f: (FAM_W[i] if i < len(FAM_W) else .25) for i, f in enumerate(r[8][:6])}
        return v, math.sqrt(sum(w * w for w in v.values()))

    def cos(a, b):
        (va, na), (vb, nb_) = a, b
        if not na or not nb_: return 0
        if len(va) > len(vb): va, vb = vb, va
        return sum(w * vb.get(k, 0) for k, w in va.items()) / (na * nb_)

    clone_keys = {nb(b) for b in CLONE_HOUSES}
    is_clone = lambda r: nb(r[2]) in clone_keys
    by_id = {r[0]: r for r in items}
    # originals: ordinary houses, known well enough to be worth copying
    origs = [r for r in items if not is_clone(r) and r[5] >= 1500 and r[15] in ('D', 'N', 'U')]
    ovec = {r[0]: (nvec(r), fvec(r)) for r in origs}
    # index originals by note, so a clone is only compared with perfumes it shares something with
    inv = {}
    for r in origs:
        for k in ovec[r[0]][0][0]:
            inv.setdefault(k, []).append(r[0])

    def score(c, o, cn, cf):
        on, of = ovec[o[0]]
        ns, fs = cos(cn, on), cos(cf, of)
        if not cn[1] or not on[1]: return 0
        s = ns * .7 + fs * .3
        if c[4] != 'U' and o[4] != 'U' and c[4] != o[4]: s *= .85
        return s

    def best(c):
        cn, cf = nvec(c), fvec(c)
        if len(cn[0]) < 4: return None
        cand = {}
        for k, w in cn[0].items():
            if df.get(k, 0) > 4000: continue          # bergamot and musk are in everything
            for oid in inv.get(k, ()): cand[oid] = cand.get(oid, 0) + 1
        top = None
        for oid, shared in cand.items():
            if shared < 3: continue
            o = by_id[oid]
            if o[3] and c[3] and o[3] > c[3]: continue    # the original comes first
            s = score(c, o, cn, cf)
            if s and (not top or s > top[1] or (s == top[1] and o[5] > by_id[top[0]][5])): top = (oid, s, shared)
        return top

    find = {}
    for r in items: find.setdefault((nb(r[2]), fold(r[1])), []).append(r)

    def lookup(brand, name):
        l = find.get((nb(brand), fold(name)), [])
        return max(l, key=lambda r: r[5]) if l else None

    out, known_ids = {}, {}
    for cb, cn_, ob, on_ in KNOWN:
        c, o = lookup(cb, cn_), lookup(ob, on_)
        if c and o: known_ids[c[0]] = o[0]
        elif '--check' in sys.argv: print('not in the catalogue:', (cb, cn_) if not c else (ob, on_))
    for cid, oid in LEGACY.items():
        if cid in by_id and oid in by_id: known_ids.setdefault(cid, oid)

    if '--check' in sys.argv:
        ok = 0
        for cid, oid in known_ids.items():
            b = best(by_id[cid])
            hit = b and b[0] == oid
            same_line = b and nb(by_id[b[0]][2]) == nb(by_id[oid][2])
            ok += 1 if hit else 0
            print(('OK  ' if hit else 'near' if same_line else 'MISS'), by_id[cid][2], '|', by_id[cid][1], '->', by_id[oid][1],
                  '| found:', (by_id[b[0]][2] + ' ' + by_id[b[0]][1], round(b[1], 2), b[2]) if b else None)
        print(ok, '/', len(known_ids))

    T = float(os.environ.get('DUPE_T', '.56'))
    n_auto = 0
    for c in items:
        if c[0] in known_ids:
            out[c[0]] = [known_ids[c[0]], 0, 1]; continue
        if not is_clone(c) or c[5] < 30: continue
        b = best(c)
        if b and b[1] >= T and b[2] >= 4:
            out[c[0]] = [b[0], round(min(.99, b[1]) * 100), 0]; n_auto += 1
    if '--sample' in sys.argv:
        import random
        random.seed(3)
        for cid in random.sample([k for k, v in out.items() if not v[2]], 60):
            o = by_id[out[cid][0]]; c = by_id[cid]
            print(out[cid][1], '|', c[2], '-', c[1], c[3], '=>', o[2], '-', o[1], o[3])
    json.dump({'v': datetime.date.today().isoformat(), 'd': out}, open(OUT, 'w', encoding='utf-8'), separators=(',', ':'))
    print(len(known_ids), 'listed by hand,', n_auto, 'worked out ->', OUT, os.path.getsize(OUT), 'bytes')


if __name__ == '__main__':
    main()
