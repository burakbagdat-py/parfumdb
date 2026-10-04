// Usage: node scripts/shop_check.js [part of a perfume name, ...]   e.g.  node scripts/shop_check.js sauvage,million
// Boyner is left out: it answers scripts with a Cloudflare challenge, which is not worked around.
// Runs the app's own matching code (cut out of docs/index.html) against the stores' live search pages, the way the phone does:
// one plain GET per search, same headers, rules from shops.json. Polite: one request at a time per store, a pause between them.
const fs = require('fs');
const R = require('path').join(__dirname, '..') + '/';
const src = fs.readFileSync(R + 'docs/index.html', 'utf8');
const cut = (a, b) => { const i = src.indexOf(a), j = src.indexOf(b, i); if (i < 0 || j < 0) throw new Error('cut ' + a); return src.slice(i, j); };
const code = [
  cut('const fold = s =>', '\n'), cut('const shortBrand = b =>', '\n'), cut('const ALIASES = {', 'function expandQuery'),
  cut('const M_STOP = new Set', 'const mpMem = new Map()'), (process.env.EXTRA || '')
].join('\n');
const S = {soldConc: {}}; const save = () => {};
const api = new Function('S', 'save', code + '\nreturn {mBuckets, queryVariants, mMatchName, soldConc, mFold, priceSane, uniText};')(S, save);
const rules = JSON.parse(fs.readFileSync(R + 'docs/data/shops.json', 'utf8')).shops;
const UA = 'Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0 Mobile Safari/537.36';
const sleep = ms => new Promise(r => setTimeout(r, ms));
const unesc = s => s.replace(/&amp;/g, '&').replace(/&#39;|&#x27;|&apos;/g, "'").replace(/&quot;/g, '"').replace(/&#(\d+);/g, (_, n) => String.fromCharCode(+n));
const price = (s, fmt) => fmt === 'kurus' ? parseInt(s, 10) / 100 : parseFloat(String(s).trim().replace(/\./g, '').replace(',', '.')) || 0;
async function get(url){
  const t = Date.now();
  try {
    const r = await fetch(url, {headers: {'User-Agent': UA, 'Accept': 'text/html,application/xhtml+xml', 'Accept-Language': 'tr-TR,tr;q=0.9'}, signal: AbortSignal.timeout(20000)});
    const html = r.ok ? await r.text() : '';
    return {code: r.status, html, ms: Date.now() - t};
  } catch (e) { return {code: 0, html: '', ms: Date.now() - t, err: String(e.message || e)}; }
}
function parse(rule, html){
  const out = [];
  if (rule.type === 'ldjson') {
    for (const m of html.matchAll(/<script[^>]*application\/ld\+json[^>]*>([\s\S]*?)<\/script>/g)) {
      try { const d = JSON.parse(m[1].trim()); if (d['@type'] !== 'ItemList') continue;
        for (const e of d.itemListElement || []) { const it = e.item || {}, of = it.offers || {}; out.push({t: it.name, u: it.url || of.url, p: +of.price || 0}); } } catch (e) {}
    }
    return out;
  }
  const segs = html.split(new RegExp(rule.split));
  for (const sg of segs.slice(1, 81)) {
    const u = new RegExp(rule.url).exec(sg), t = new RegExp(rule.title).exec(sg), p = new RegExp(rule.price).exec(sg);
    if (!u || !t || !p) continue;
    out.push({t: api.uniText(unesc(t[1])), u: (rule.base || '') + u[1], p: price(p[1], rule.fmt || 'tr')});
  }
  return out;
}
const SET = [
  {brand: 'Dior', name: 'Sauvage', conc: 'EDT', g: 'E'}, {brand: 'Jean Paul Gaultier', name: 'Le Male Elixir', conc: 'Parfum'}, {brand: 'Chanel', name: 'Bleu de Chanel Eau de Parfum', conc: 'EDP'},
  {brand: 'Yves Saint Laurent', name: 'Y Eau de Parfum', conc: 'EDP'}, {brand: 'Lattafa Perfumes', name: 'Khamrah', conc: 'EDP'}, {brand: 'Versace', name: 'Eros', conc: 'EDT', g: 'E'},
  {brand: 'Giorgio Armani', name: 'Emporio Armani Stronger With You Intensely', conc: 'EDP'}, {brand: 'Tom Ford', name: 'Tobacco Vanille', conc: 'EDP'}, {brand: 'Rabanne', name: '1 Million', conc: 'EDT', g: 'E'},
  {brand: 'Parfums de Marly', name: 'Layton', conc: 'EDP'}, {brand: 'Azzaro', name: 'The Most Wanted Parfum', conc: 'Parfum'}, {brand: 'Carolina Herrera', name: 'Good Girl', conc: 'EDP', g: 'K'},
  {brand: 'Dolce&Gabbana', name: 'Light Blue', conc: 'EDT', g: 'K'}, {brand: 'Maison Francis Kurkdjian', name: 'Baccarat Rouge 540', conc: 'EDP'}
];
(async () => {
  const only = process.argv[2] ? process.argv[2].split(',') : null, stores = Object.keys(rules).filter(k => k !== 'boyner');
  const dump = {};
  for (const p of SET) {
    if (only && !only.some(x => p.name.toLowerCase().includes(x.toLowerCase()))) continue;
    const qs = api.queryVariants(p);
    console.log(`\n== ${p.brand} · ${p.name} (${p.conc})   variants: ${JSON.stringify(qs)}`);
    const data = {shops: {}};
    await Promise.all(stores.map(async k => {
      let line = '';
      for (let i = 0; i < qs.length; i++) {
        const r = await get(rules[k].search.replace('{q}', encodeURIComponent(qs[i]).replace(/%20/g, '+')));
        const items = r.code === 200 ? parse(rules[k], r.html) : [];
        (dump[k] = dump[k] || {})[p.name + '|' + qs[i]] = items;
        const bc = api.mBuckets(items, p, rules[k].market);
        const txt = Object.entries(bc).map(([c, b]) => c + ':{' + Object.entries(b).map(([ml, x]) => ml + '=' + Math.round(x.p) + (x.n > 1 ? '/' + x.n : '')).join(' ') + '}').join(' ');
        const near = items.filter(it => !api.mMatchName(p, it.t)).slice(0, 2).map(it => it.t.slice(0, 50)).join(' ;; ');
        line += `\n   ${k.padEnd(12)} v${i + 1} http ${r.code} ${String(r.ms).padStart(5)}ms ${String(items.length).padStart(3)} items  ${txt || '— no match' + (items.length ? '   e.g. ' + near : '')}`;
        if (txt) data.shops[k] = {byConc: bc};
        if (txt || r.code !== 200) break;
        await sleep(500);
      }
      console.log(line.slice(1));
    }));
    api.priceSane(data, rules);
    const sus = Object.entries(data.shops).flatMap(([k, v]) => Object.entries(v.byConc).flatMap(([c, b]) => Object.entries(b).filter(([, x]) => x.sus).map(([ml, x]) => `${k} ${c} ${ml}ml ${Math.round(x.p)}`)));
    if (sus.length) console.log('   flagged: ' + sus.join(' | '));
    await sleep(700);
  }
  fs.writeFileSync(require('path').join(require('os').tmpdir(), 'shopdump.json'), JSON.stringify(dump));
})();
