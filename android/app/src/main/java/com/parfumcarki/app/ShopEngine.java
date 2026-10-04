package com.parfumcarki.app;

import android.net.Uri;
import android.text.Html;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a store's search page with rules that live in docs/data/shops.json, so stores can be added or fixed
 * without a new APK. Only the hosts listed here can ever be contacted.
 */
final class ShopEngine {
    private static final String[] HOSTS = {"boyner.com.tr", "beymen.com", "gratis.com", "n11.com", "sephora.com.tr",
            "trendyol.com", "hepsiburada.com", "amazon.com.tr", "rossmann.com.tr", "watsons.com.tr", "perfumepoint.com.tr", "pazarama.com"};
    static final Set<String> STOP = new HashSet<>(Arrays.asList(("ml erkek kadin unisex parfum parfumu eau de du la le toilette cologne "
            + "edt edp extrait spray sprey vapo natural for men women pour homme femme fragrance refillable orijinal orjinal yeni new diger christian "
            + "ve icin tr gr mens womens ladies bayan bay erkeklere kadinlara perfume parfume vaporisateur vaporizer").split(" ")));
    static final Set<String> BAD = new HashSet<>(Arrays.asList(("set seti deodorant deo dus shower balm lotion losyon sampuan serum vucut "
            + "body after sabun kit minyatur miniature refill stick krem hair sac tester decant gift hediye mist gel jel kolonya").split(" ")));
    private static final Pattern SIZE = Pattern.compile("(\\d{2,3})\\s*ml", Pattern.CASE_INSENSITIVE);

    private ShopEngine() { }

    static boolean allowed(String url) {
        Uri u = Uri.parse(url);
        if (!"https".equals(u.getScheme()) || u.getHost() == null) return false;
        for (String h : HOSTS) if (u.getHost().equals(h) || u.getHost().endsWith("." + h)) return true;
        return false;
    }

    /** Items found on the store's search page: [{t: title, u: url, p: price}] */
    static JSONArray search(JSONObject rule, String query, int ml) throws Exception {
        String type = rule.optString("type", "regex");
        JSONArray out = new JSONArray();
        if ("boyner".equals(type)) {
            JSONObject r = new JSONObject(MainActivity.fetchBoyner(query, ml > 0 ? ml + "-ml" : ""));
            if (r.has("error")) throw new IllegalStateException(r.optBoolean("blocked") ? "blocked" : r.optString("error"));
            JSONArray ps = r.optJSONArray("products");
            for (int i = 0; ps != null && i < ps.length(); i++) {
                JSONObject p = ps.getJSONObject(i);
                if (!fold(p.optString("c")).contains("parfum")) continue;
                out.put(new JSONObject().put("t", p.optString("t")).put("u", "https://www.boyner.com.tr/" + p.optString("u"))
                        .put("p", price(p.optString("p"), "tr")).put("ml", ml));
            }
            return out;
        }
        String url = rule.getString("search").replace("{q}", URLEncoder.encode(query, "UTF-8"));
        if (!allowed(url)) throw new IllegalArgumentException("host not allowed");
        String html = MainActivity.httpText(url);
        if ("ldjson".equals(type)) {
            Matcher m = Pattern.compile("<script[^>]*application/ld\\+json[^>]*>(.*?)</script>", Pattern.DOTALL).matcher(html);
            while (m.find()) {
                try {
                    JSONObject d = new JSONObject(m.group(1).trim());
                    if (!"ItemList".equals(d.optString("@type"))) continue;
                    JSONArray list = d.optJSONArray("itemListElement");
                    for (int i = 0; list != null && i < list.length(); i++) {
                        JSONObject it = list.getJSONObject(i).optJSONObject("item");
                        if (it == null) continue;
                        JSONObject of = it.optJSONObject("offers");
                        if (of == null) continue;
                        out.put(new JSONObject().put("t", it.optString("name")).put("u", it.optString("url", of.optString("url")))
                                .put("p", of.optDouble("price", 0)));
                    }
                } catch (Exception ignored) { }
            }
            return out;
        }
        Pattern pu = Pattern.compile(rule.getString("url")), pt = Pattern.compile(rule.getString("title")), pp = Pattern.compile(rule.getString("price"));
        String[] segs = html.split(rule.getString("split"));
        for (int i = 1; i < segs.length && out.length() < 80; i++) {
            Matcher u = pu.matcher(segs[i]), t = pt.matcher(segs[i]), p = pp.matcher(segs[i]);
            if (!u.find() || !t.find() || !p.find()) continue;
            out.put(new JSONObject().put("t", Html.fromHtml(t.group(1), Html.FROM_HTML_MODE_LEGACY).toString())
                    .put("u", rule.optString("base", "") + u.group(1)).put("p", price(p.group(1), rule.optString("fmt", "tr"))));
        }
        return out;
    }

    static double price(String s, String fmt) {
        try {
            if ("kurus".equals(fmt)) return Long.parseLong(s.trim()) / 100.0;
            return Double.parseDouble(s.trim().replace(".", "").replace(',', '.'));
        } catch (Exception e) {
            return 0;
        }
    }

    static int sizeOf(String title) {
        Matcher m = SIZE.matcher(title);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    static String fold(String s) {
        String n = Normalizer.normalize(s.toLowerCase(new Locale("tr")).replace('ı', 'i'), Normalizer.Form.NFD);
        return n.replaceAll("\\p{M}", "").replaceAll("[^a-z0-9]+", " ").trim();
    }

    /** Every concentration a title states, strongest first; a standalone "Parfum" counts, "Eau de Parfum" is EDP. */
    static List<String> concs(String folded) {
        String t = " " + folded.replaceAll("\\b(erkek|kadin|unisex|bayan|bay) parfum(u|leri)?\\b", " ") + " ";
        List<String> out = new ArrayList<>();
        if (t.contains(" extrait ")) out.add("Extrait");
        if (t.contains(" elixir ")) out.add("Elixir");
        String u = t.replace(" extrait de parfum ", " ").replace(" eau de parfum ", " edp ");
        if (u.contains(" parfum ")) out.add("Parfum");
        if (u.contains(" edp ")) out.add("EDP");
        if (t.contains(" eau de toilette ") || t.contains(" edt ")) out.add("EDT");
        if (t.contains(" eau de cologne ") || t.contains(" cologne ") || t.contains(" edc ")) out.add("EDC");
        return out;
    }

    static String concOf(String folded) {
        List<String> c = concs(folded);
        return c.isEmpty() ? "" : c.get(0);
    }

    /** A name like "Le Male Le Parfum" already carries a concentration word; only what the title adds on top decides. */
    static boolean concOk(String conc, String name, String t) {
        if (conc == null || conc.isEmpty()) return true;
        List<String> cs = concs(t);
        if (cs.isEmpty()) return true;
        List<String> nc = concs(fold(name));
        // "Le Male Elixir" is sold as "Le Male Elixir EDP" although it is a Parfum: the name already pins the product
        if (!nc.isEmpty()) return cs.containsAll(nc);
        return cs.get(0).equals(conc);
    }

    private static List<String> toks(String s) {
        List<String> out = new ArrayList<>();
        for (String w : fold(s).split(" ")) if (!w.isEmpty()) out.add("one".equals(w) ? "1" : w);
        return out;
    }

    /** Turkish "Parfüm" just means perfume, it is not the Parfum concentration. */
    static String cleanTitle(String title) {
        return fold(title.split(" - ")[0].replaceAll("(?i)parf[üÜ]m", " "));
    }

    /** Same perfume? All name words present, nothing that marks another product or flanker, same concentration when known.
     *  brand may also carry the shorthands stores use (YSL, PDM…); the page sends them. */
    static boolean matches(String name, String brand, String conc, String title) {
        String t = cleanTitle(title);
        List<String> tl = toks(t);
        Set<String> tt = new HashSet<>(tl);
        List<String> nameT = new ArrayList<>();
        boolean longWord = false;
        for (String w : toks(name)) if (!STOP.contains(w)) { nameT.add(w); if (w.length() > 1) longWord = true; }
        if (longWord) nameT.removeIf(w -> w.length() < 2);
        if (nameT.isEmpty() || !tt.containsAll(nameT)) return false;
        for (String b : BAD) if (tt.contains(b)) return false;
        Set<String> brandT = new HashSet<>(toks(brand));
        for (String w : tl) if (w.length() > 1 && !nameT.contains(w) && !brandT.contains(w) && !STOP.contains(w) && !w.matches("\\d+(ml|gr?|cl)?")) return false;
        // "Eau Sauvage" is a different perfume from "Sauvage"
        int i = tl.indexOf(nameT.get(0));
        if (i > 0 && "eau".equals(tl.get(i - 1)) && !fold(name).startsWith("eau")) return false;
        return concOk(conc, name, t);
    }

    /** Lowest price per size for this perfume on one store: {"100": {"p": 7120, "u": "..."}} — explicit concentration matches win. */
    static JSONObject sizes(JSONArray items, String name, String brand, String conc, int fixedMl) throws Exception {
        JSONObject best = new JSONObject(), exact = new JSONObject();
        for (int i = 0; i < items.length(); i++) {
            JSONObject it = items.getJSONObject(i);
            String title = it.optString("t");
            double p = it.optDouble("p", 0);
            if (p <= 0 || !matches(name, brand, conc, title)) continue;
            int ml = fixedMl > 0 ? fixedMl : sizeOf(title);
            if (ml <= 0) continue;
            boolean explicit = conc != null && !conc.isEmpty() && concs(cleanTitle(title)).contains(conc);
            JSONObject target = explicit ? exact : best;
            String k = String.valueOf(ml);
            if (!target.has(k) || target.getJSONObject(k).getDouble("p") > p) target.put(k, new JSONObject().put("p", p).put("u", it.optString("u")).put("t", title));
        }
        JSONArray ks = exact.names();
        for (int i = 0; ks != null && i < ks.length(); i++) best.put(ks.getString(i), exact.get(ks.getString(i)));
        return best;
    }
}
