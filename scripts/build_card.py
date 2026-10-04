"""Builds what the shared card page (docs/k.html) looks up, so a card link only has to carry numbers:

  docs/data/n/<id // 2000>.json   name and house of every catalogue perfume, in small pieces
  docs/data/card.json             the colours of every theme and the list of scent families

Run it after scripts/build_catalog.py, and after a theme's colours or the family list in docs/index.html change.
A card link stores a family as its position in that list, so new families must be added at the END of FAM_HUE.
"""
import io, json, os, re

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'docs')


def names():
    items = json.load(io.open(os.path.join(ROOT, 'data', 'catalog.json'), encoding='utf-8'))['items']
    out = os.path.join(ROOT, 'data', 'n')
    os.makedirs(out, exist_ok=True)
    parts = {}
    for r in items:
        parts.setdefault(r[0] // 2000, {})[str(r[0])] = [r[1], r[2]]
    for k, v in parts.items():
        with io.open(os.path.join(out, '%d.json' % k), 'w', encoding='utf-8', newline='\n') as f:
            json.dump(v, f, ensure_ascii=False, separators=(',', ':'))
    return len(parts), len(items)


def themes():
    src = io.open(os.path.join(ROOT, 'index.html'), encoding='utf-8').read()
    css = src[src.index('<style>'):src.index('</style>')]
    skins = {}
    for m in re.finditer(r'\[data-skin="([a-z]+)"\](\[data-mode="light"\])?\{([^}]*)\}', css):
        key, light, body = m.group(1), bool(m.group(2)), m.group(3)
        col = {k: v for k, v in re.findall(r'--(bg|panel|fg|muted|accent|ink):\s*(#[0-9a-fA-F]{6})', body)}
        s = skins.setdefault(key, {})
        if len(col) == 6:
            s['l' if light else 'd'] = [col[k] for k in ('bg', 'panel', 'fg', 'muted', 'accent', 'ink')]
        f = re.search(r'--f-display:([^;]+);', body)
        if f and not light:
            s['serif'] = bool(re.search(r'(?<!sans-)serif', f.group(1)))
    skins = {k: v for k, v in skins.items() if 'd' in v}
    fam = src[src.index('const FAM_HUE = {'):]
    fam = fam[:fam.index('};')]
    fams = [[n, int(h)] for n, h in re.findall(r"'([^']+)':(\d+)", fam)]
    with io.open(os.path.join(ROOT, 'data', 'card.json'), 'w', encoding='utf-8', newline='\n') as f:
        json.dump({'skins': skins, 'fams': fams}, f, ensure_ascii=False, separators=(',', ':'))
    return skins, fams


if __name__ == '__main__':
    n, total = names()
    skins, fams = themes()
    print('%d perfumes in %d name files; %d themes (%s); %d families' % (total, n, len(skins), ', '.join(skins), len(fams)))
