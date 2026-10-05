#!/usr/bin/env node
// How well are the catalogue's notes explained? Runs the app's own note code (cut out of docs/index.html) over every note.
//   node scripts/notes_check.js            summary and the commonest unexplained names
//   node scripts/notes_check.js --sample   60 random explained names with their text, to read for nonsense
const fs = require('fs'), path = require('path');
const root = path.join(__dirname, '..', 'docs');
const html = fs.readFileSync(path.join(root, 'index.html'), 'utf8');
const m = /\/\/ NOTE-ENGINE[\s\S]*?\/\/ END NOTE-ENGINE/.exec(html);
if (!m) { console.error('note engine not found in index.html'); process.exit(1); }
const fold = s => String(s || '').replace(/ı/g, 'i').replace(/İ/g, 'i').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();
const {noteRules, noteDesc} = new Function('fold', m[0] + '\nreturn {noteRules, noteDesc};')(fold);
const d = noteRules(JSON.parse(fs.readFileSync(path.join(root, 'data', 'notes2.json'), 'utf8')));
const cat = JSON.parse(fs.readFileSync(path.join(root, 'data', 'catalog.json'), 'utf8')).items;
const count = new Map();
for (const r of cat) for (const t of [r[9], r[10], r[11]]) for (const n of t) count.set(n, (count.get(n) || 0) + 1);
let total = 0, ok = 0, okNames = 0; const miss = [], hit = [];
for (const [n, c] of count) { total += c; const t = noteDesc(d, n); if (t && t !== d.tm) { ok += c; okNames++; hit.push([n, t, !!d.exact[n]]); } else miss.push([n, c, t ? 'tm' : '']); }
console.log(`${count.size} names, ${total} uses; explained: ${okNames} names (${(okNames / count.size * 100).toFixed(1)}%), ${(ok / total * 100).toFixed(2)}% of uses`);
miss.sort((a, b) => b[1] - a[1]);
console.log('unexplained:', miss.length, '\n' + miss.slice(0, process.argv.includes('--all') ? 5000 : 150).map(x => `${x[0]} (${x[1]}${x[2] ? ' tm' : ''})`).join(' ; '));
if (process.argv.includes('--sample')) {
  const pool = hit.filter(x => !x[2]); let seed = +(process.argv[process.argv.indexOf('--sample') + 1]) || 7;
  const rnd = () => (seed = (seed * 16807) % 2147483647) / 2147483647;
  for (let i = 0; i < 70; i++) { const x = pool[Math.floor(rnd() * pool.length)]; console.log(x[0], '=>', x[1]); }
}
