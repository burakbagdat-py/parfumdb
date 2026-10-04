package com.parfumcarki.app;

import android.net.Uri;

import java.util.Locale;

/** Turns what the camera read (a product barcode, a QR code, a GS1 square code) into a short report. Offline; nothing is looked up. */
final class CodeJudge {
    static final int GOOD = 0, WARN = 1, BAD = 2;

    static final class Rep {
        int tone = WARN;
        String kind = "Kod", code = "", head = "", body = "", url = "";
    }

    /** GS1 prefixes: where the company that owns the barcode is registered (not where the bottle was filled). */
    private static final Object[][] GS1 = {{0, 139, "ABD / Kanada"}, {300, 379, "Fransa"}, {400, 440, "Almanya"}, {450, 459, "Japonya"}, {490, 499, "Japonya"},
            {500, 509, "Birleşik Krallık"}, {540, 549, "Belçika / Lüksemburg"}, {628, 628, "Suudi Arabistan"}, {629, 629, "Birleşik Arap Emirlikleri"}, {690, 699, "Çin"},
            {730, 739, "İsveç"}, {760, 769, "İsviçre"}, {800, 839, "İtalya"}, {840, 849, "İspanya"}, {868, 869, "Türkiye"}, {870, 879, "Hollanda"}, {880, 880, "Güney Kore"}, {890, 890, "Hindistan"}};

    /** Verification addresses I know: host, then the house it belongs to. Same list as the app's. */
    private static final String[][] HOSTS = {{"lattafa.com", "Lattafa"}, {"afnan.com", "Afnan"}, {"xerjoff.com", "Xerjoff"}, {"certilogo.com", "Certilogo (markaların kullandığı doğrulama servisi)"},
            {"armaf.ae", "Armaf"}, {"armaf.com", "Armaf"}, {"rasasi.com", "Rasasi"}, {"ajmal.com", "Ajmal"}, {"ajmalperfume.com", "Ajmal"}, {"swissarabian.com", "Swiss Arabian"},
            {"parfums-de-marly.com", "Parfums de Marly"}, {"creedfragrances.co.uk", "Creed"}, {"creedboutique.com", "Creed"}, {"creedfragrance.com", "Creed"}, {"amouage.com", "Amouage"},
            {"fragranceworld.ae", "Fragrance World"}, {"pariscorner.ae", "Paris Corner"}, {"nishane.com", "Nishane"}, {"alharamainperfumes.com", "Al Haramain"}};

    private CodeJudge() { }

    static Rep judge(String raw, boolean productBarcode) {
        String text = raw == null ? "" : raw.trim();
        if (productBarcode && text.matches("\\d+")) return barcode(text);
        String gs = text.replace("]d2", "").replace("]C1", "").replace("]Q3", "");
        if (gs.startsWith("\u001d")) gs = gs.substring(1);
        if (gs.matches("01\\d{14}.*")) return gs1(gs);
        String low = text.toLowerCase(Locale.ROOT);
        if (low.startsWith("http://") || low.startsWith("https://")) return link(text, low.startsWith("https://"));
        if (text.matches("\\d{8}|\\d{12,13}")) return barcode(text);
        String up = text.toUpperCase(Locale.ROOT);
        if (up.matches("[A-Z0-9]{3,10}")) {
            BatchCodec.Result b = BatchCodec.read(up);
            if (b.ok) {
                Rep r = new Rep();
                r.tone = GOOD;
                r.kind = "Parti kodu";
                r.code = b.code;
                r.head = b.head;
                r.body = b.report;
                return r;
            }
        }
        Rep r = new Rep();
        r.code = text.length() > 60 ? text.substring(0, 60) + "…" : text;
        r.head = "Kod okundu, ama bir adres ya da barkod değil";
        r.body = "İçeriği: " + (text.length() > 200 ? text.substring(0, 200) + "…" : text)
                + "\n\nMarka doğrulama etiketleri genelde bir web adresi açar; bu bir seri numarası ya da ürün kodu olabilir.";
        return r;
    }

    private static Rep barcode(String digits) {
        Rep r = new Rep();
        r.kind = "Barkod";
        r.code = digits;
        String c = digits.length() == 12 ? "0" + digits : digits;
        boolean ok;
        if (c.length() == 13) {
            int sum = 0;
            for (int i = 0; i < 12; i++) sum += (c.charAt(i) - '0') * (i % 2 == 1 ? 3 : 1);
            ok = (10 - sum % 10) % 10 == c.charAt(12) - '0';
        } else if (c.length() == 8) {
            int sum = 0;
            for (int i = 0; i < 7; i++) sum += (c.charAt(i) - '0') * (i % 2 == 1 ? 1 : 3);
            ok = (10 - sum % 10) % 10 == c.charAt(7) - '0';
        } else {
            r.head = "Barkod okundu";
            r.body = "Barkod: " + digits + "\nBu barkod türü ülke ve sağlama bilgisi taşımıyor. Parti kodunu elle yazarak devam et.";
            return r;
        }
        if (!ok) {
            r.tone = BAD;
            r.head = "Barkodun sağlama hanesi yanlış";
            r.body = "Barkod: " + digits + "\nGerçek bir ürün barkodunda son hane hesapla tutar. Tutmuyorsa barkod uydurulmuş olabilir; kamera yanlış okumuş da olabilir, bir kez daha okut.";
            return r;
        }
        String land = "";
        int pre = Integer.parseInt(c.substring(0, 3));
        if (c.length() == 13) for (Object[] g : GS1) if (pre >= (Integer) g[0] && pre <= (Integer) g[1]) land = (String) g[2];
        boolean china = land.equals("Çin");
        r.tone = china ? BAD : GOOD;
        r.head = china ? "Barkod Çin’de kayıtlı bir firmaya ait" : "Barkod geçerli" + (land.isEmpty() ? "" : " · " + land);
        r.body = "Barkod: " + digits
                + "\nSağlama hanesi: tutuyor"
                + (land.isEmpty() ? "" : "\nBarkodu alan firma: " + land + " kayıtlı (önek " + c.substring(0, 3) + ")")
                + (china ? "\n\nTanınmış parfüm evlerinin barkodu Çin önekiyle (690–699) başlamaz; bu güçlü bir sahtelik işaretidir."
                : "\n\nBu, barkodun düzgün olduğunu gösterir. Sahteciler gerçek barkodu kopyalayabildiği için tek başına kanıt değildir; kutudaki parti kodunu da yazıp üretim tarihine bak.");
        return r;
    }

    /** GS1 square code: (01) product number, (10) batch, (17) expiry, (11) production, (21) serial. */
    private static Rep gs1(String s) {
        String gtin = "", lot = "", exp = "", made = "", serial = "";
        int i = 0;
        while (i + 2 <= s.length()) {
            if (s.charAt(i) == '\u001d') {
                i++;
                continue;
            }
            String ai = s.substring(i, i + 2);
            i += 2;
            if (ai.equals("01") && i + 14 <= s.length()) {
                gtin = s.substring(i, i + 14);
                i += 14;
            } else if ((ai.equals("17") || ai.equals("11") || ai.equals("15") || ai.equals("13")) && i + 6 <= s.length()) {
                String d = s.substring(i, i + 6);
                if (ai.equals("17") || ai.equals("15")) exp = d;
                else made = d;
                i += 6;
            } else if (ai.equals("10") || ai.equals("21")) {
                int end = s.indexOf('\u001d', i);
                if (end < 0) end = s.length();
                if (ai.equals("10")) lot = s.substring(i, end);
                else serial = s.substring(i, end);
                i = end;
            } else {
                break;
            }
        }
        Rep bar = barcode(gtin.startsWith("0") ? gtin.substring(1) : gtin);
        Rep r = new Rep();
        r.kind = "Kare kod";
        r.code = lot.isEmpty() ? bar.code : lot;
        r.tone = bar.tone;
        StringBuilder b = new StringBuilder(bar.body.split("\n\n")[0]);
        if (!lot.isEmpty()) b.append("\nParti kodu: ").append(lot);
        if (!made.isEmpty()) b.append("\nÜretim: ").append(date(made));
        if (!exp.isEmpty()) b.append("\nSon kullanma: ").append(date(exp));
        if (!serial.isEmpty()) b.append("\nSeri no: ").append(serial);
        BatchCodec.Result dec = lot.isEmpty() ? null : BatchCodec.read(lot);
        if (dec != null && dec.ok) {
            r.head = dec.head;
            b.append("\n\n").append(dec.report);
        } else {
            r.head = bar.tone == BAD ? bar.head : lot.isEmpty() ? bar.head : "Parti kodu: " + lot;
            b.append("\n\nKare kodun içindeki parti kodu, kutuya ve şişeye basılı kodla aynı olmalı.");
        }
        r.body = b.toString();
        return r;
    }

    private static String date(String yymmdd) {
        try {
            int m = Integer.parseInt(yymmdd.substring(2, 4)), d = Integer.parseInt(yymmdd.substring(4, 6));
            if (m < 1 || m > 12) return yymmdd;
            return (d > 0 ? d + " " : "") + BatchCodec.MONTHS[m - 1] + " 20" + yymmdd.substring(0, 2);
        } catch (Exception e) {
            return yymmdd;
        }
    }

    private static Rep link(String text, boolean https) {
        Rep r = new Rep();
        r.kind = "QR";
        String host = "";
        try {
            host = Uri.parse(text).getHost();
        } catch (Exception ignored) { }
        if (host == null) host = "";
        host = host.toLowerCase(Locale.ROOT);
        if (host.startsWith("www.")) host = host.substring(4);
        r.code = host.isEmpty() ? "adres" : host;
        if (https && !host.isEmpty()) r.url = text;
        String house = "";
        for (String[] h : HOSTS) if (host.equals(h[0]) || host.endsWith("." + h[0])) house = h[1];
        if (!house.isEmpty()) {
            r.tone = GOOD;
            r.head = "QR resmi adrese gidiyor: " + house;
            r.body = "Adres: " + host + "\nBu, " + house + " için bildiğim resmi alan adı."
                    + "\n\nSayfayı açıp doğrulamayı tamamla; sayfa “orijinal” demeden ürünü orijinal sayma.";
        } else {
            r.head = "QR şu adrese gidiyor: " + (host.isEmpty() ? "okunamadı" : host);
            r.body = "Adres: " + (text.length() > 160 ? text.substring(0, 160) + "…" : text)
                    + "\nBu adres, bildiğim resmi doğrulama adresleri arasında yok."
                    + "\n\nMarkanın kendi sitesi mi (marka-adı.com gibi)? Kısaltılmış, alakasız ya da garip bir adresse güvenme: sahte kutularda “orijinal” diyen sahte doğrulama sayfalarına giden QR’lar yaygın."
                    + (https ? "" : "\nAdres güvenli bağlantı (https) kullanmıyor; buradan açmıyorum.");
        }
        return r;
    }
}
