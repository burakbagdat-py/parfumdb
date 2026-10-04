package com.parfumcarki.app;

import java.util.Calendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a batch code without needing the brand: the layouts we know do not overlap, so the code's own shape says which one it is.
 * LVMH: digit + month letter + 2 digits. L'Oréal: factory + year letter + month. Estée Lauder: letter + month + year digit.
 * EuroItalia: two-digit year + day of year. Puig / Coty / Shiseido / Hermès: year digit + day of year.
 * A date printed on the box (05/2023, 28.02.2020) can be typed as it is. Same rules as the page's decodeBatch.
 */
final class BatchCodec {
    static final String[] MONTHS = {"Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran", "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık"};
    private static final String LOREAL_Y = "ABCDEFGHJKLMNPRSTUVWXYZ";   // A = 2004 … T = 2020, U = 2021 … (I, O and Q are not used)
    private static final Pattern DMY = Pattern.compile("(?:^|\\D)(\\d{1,2})[./-](\\d{1,2})[./-](\\d{4}|\\d{2})(?:\\D|$)");
    private static final Pattern MY = Pattern.compile("(?:^|\\D)(\\d{1,2})[./-](\\d{4})(?:\\D|$)");
    private static final Pattern YM = Pattern.compile("(?:^|\\D)(\\d{4})[./-](\\d{1,2})(?:\\D|$)");

    static final class Result {
        boolean ok;
        String code = "", head = "", group = "", read = "", report = "";
    }

    private BatchCodec() { }

    static Result read(String raw) {
        Result r = new Result();
        String text = raw == null ? "" : raw.trim();
        Calendar now = Calendar.getInstance();
        int cy = now.get(Calendar.YEAR), cm = now.get(Calendar.MONTH) + 1;

        // a date printed on the box and typed as it is
        int ty = 0, tm = 0, tday = 0;
        Matcher m = DMY.matcher(text);
        if (m.find()) {
            tday = Integer.parseInt(m.group(1));
            tm = Integer.parseInt(m.group(2));
            ty = Integer.parseInt(m.group(3));
            if (ty < 100) ty += 2000;
        } else if ((m = MY.matcher(text)).find()) {
            tm = Integer.parseInt(m.group(1));
            ty = Integer.parseInt(m.group(2));
        } else if ((m = YM.matcher(text)).find()) {
            ty = Integer.parseInt(m.group(1));
            tm = Integer.parseInt(m.group(2));
        }
        if (ty >= 1990 && ty <= cy && tm >= 1 && tm <= 12 && tday <= 31) {
            r.code = text;
            r.group = "Kutuda yazan tarih";
            return finish(r, ty, tm, tday, "", 0, true, cy, cm);
        }

        String code = text.toUpperCase(java.util.Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        r.code = code;
        if (code.length() < 3 || code.length() > 12) {
            r.head = "Bu bir parti koduna benzemiyor";
            r.report = "Parti kodu genelde 3–10 harf/rakamdır; kutunun altına basılı, şişenin altına kazınmıştır. Barkodun altındaki 13 haneli sayı parti kodu değildir. Kutuda tarih yazıyorsa onu da yazabilirsin (ör. 05/2023).";
            return r;
        }
        int y = 0, month = 0, doy = 0, cycle = 0;
        boolean sure = true, digitYear = false;
        if (code.matches("\\d[A-HJ-NP-Z]\\d{2}")) {
            r.group = "LVMH (Dior, Guerlain, Givenchy, Kenzo)";
            y = code.charAt(0) - '0';
            digitYear = true;
            month = "ABCDEFGHJKLM".indexOf(code.charAt(1)) + 1;
            if (month == 0) month = "NPQRSTUVWXYZ".indexOf(code.charAt(1)) + 1;
            r.read = code.charAt(0) + " → yıl · " + code.charAt(1) + " → " + MONTHS[month - 1];
        } else if (code.matches("[A-Z0-9]{2}[A-HJ-NPR-Z][1-9ONDABC][A-Z0-9]{0,5}")) {
            r.group = "L’Oréal (Armani, YSL, Lancôme, Valentino, Prada, Azzaro, Mugler)";
            y = 2004 + LOREAL_Y.indexOf(code.charAt(2));
            cycle = LOREAL_Y.length();
            char mc = code.charAt(3);
            month = mc >= '1' && mc <= '9' ? mc - '0' : mc == 'O' || mc == 'A' ? 10 : mc == 'N' || mc == 'B' ? 11 : 12;
            while (y + cycle <= cy) y += cycle;
            r.read = code.substring(0, 2) + " → fabrika · " + code.charAt(2) + " → yıl · " + mc + " → " + MONTHS[month - 1];
        } else if (code.matches("[A-Z][1-9ABC]\\d")) {
            r.group = "Estée Lauder (Tom Ford, Jo Malone, Kilian, Le Labo)";
            y = code.charAt(2) - '0';
            digitYear = true;
            month = "123456789ABC".indexOf(code.charAt(1)) + 1;
            r.read = code.charAt(1) + " → " + MONTHS[month - 1] + " · " + code.charAt(2) + " → yıl";
        } else if (code.matches("\\d{7}") && Integer.parseInt(code.substring(0, 2)) >= 10 && 2000 + Integer.parseInt(code.substring(0, 2)) <= cy
                && Integer.parseInt(code.substring(2, 5)) >= 1 && Integer.parseInt(code.substring(2, 5)) <= 366) {
            r.group = "EuroItalia (Versace, Moschino, Missoni)";
            y = 2000 + Integer.parseInt(code.substring(0, 2));
            doy = Integer.parseInt(code.substring(2, 5));
            sure = false;
            r.read = code.substring(0, 2) + " → yıl · " + code.substring(2, 5) + " → yılın " + doy + ". günü";
        } else if (code.matches("\\d{4}[A-Z0-9]{0,6}")) {
            r.group = "Puig, Coty, Shiseido ya da Hermès (JPG, Rabanne, C. Herrera, Hugo Boss, Gucci, Burberry, Issey Miyake…)";
            y = code.charAt(0) - '0';
            digitYear = true;
            doy = Integer.parseInt(code.substring(1, 4));
            if (doy < 1 || doy > 366) {
                r.head = "Kod geçerli bir tarih vermiyor";
                r.report = "İlk rakam yıl, sonraki üç rakam yılın kaçıncı günü olmalı (001–366). Kodu tekrar kontrol et; kutuda tarih yazıyorsa onu yaz (ör. 05/2023).";
                return r;
            }
            r.read = code.charAt(0) + " → yıl · " + code.substring(1, 4) + " → yılın " + doy + ". günü";
        } else {
            r.head = "Bildiğim düzenlere uymuyor";
            r.report = "Çözebildiğim düzenler: LVMH (2K01), L’Oréal (38U60OG), Estée Lauder (A83), Puig / Coty / Shiseido / Hermès (3123…), EuroItalia (2111304). "
                    + "Chanel kodu tarih vermez. Kutuda üretim tarihi yazıyorsa onu yaz (ör. 05/2023). 0/O ve 1/I karışmış olabilir; uygulamada markayı seçip de deneyebilirsin.";
            return r;
        }
        if (digitYear) y = cy - (((cy - y) % 10) + 10) % 10;
        int day = 0;
        if (doy > 0) {
            Calendar c = Calendar.getInstance();
            c.set(y, Calendar.JANUARY, 1);
            c.set(Calendar.DAY_OF_YEAR, doy);
            month = c.get(Calendar.MONTH) + 1;
            day = c.get(Calendar.DAY_OF_MONTH);
        }
        if (y == cy && month > cm) y -= digitYear ? 10 : cycle;
        if (y > cy || (y == cy && month > cm)) {
            r.head = "Kod gelecekte bir tarih veriyor";
            r.report = "Kodu tekrar kontrol et; 0/O ve 1/I karışmış olabilir.";
            return r;
        }
        return finish(r, y, month, day, r.read, digitYear ? y - 10 : 0, sure, cy, cm);
    }

    private static Result finish(Result r, int y, int month, int day, String read, int prevY, boolean sure, int cy, int cm) {
        int age = (cy - y) * 12 + (cm - month);
        String when = (day > 0 ? day + " " : "") + MONTHS[month - 1] + " " + y;
        String ageTxt = (age >= 12 ? (age / 12) + " yıl " : "") + (age % 12) + " ay";
        String state = age < 6 ? "çok yeni üretim" : age <= 36 ? "taze" : age <= 60 ? "olgun" : "eski stok";
        int left = 60 - age;
        r.ok = true;
        r.head = "Üretim: " + MONTHS[month - 1] + " " + y + " · " + (age < 12 ? age + " aylık" : (age / 12) + " yıllık");
        r.report = "Üretim tarihi: " + when
                + "\nŞişenin yaşı: " + ageTxt + " · " + state
                + (read.isEmpty() ? "" : "\nKodun okunuşu: " + read)
                + "\nDüzen: " + r.group
                + (sure ? "" : "\nGüven: orta (düzen örnek kodlardan çıkarıldı)")
                + (prevY > 0 ? "\nDiğer olası yıl: " + MONTHS[month - 1] + " " + prevY + " (son hane 10 yılda bir tekrar eder)" : "")
                + "\nEn iyi hali: " + (left > 0 ? "yaklaşık " + MONTHS[month - 1] + " " + (y + 5) + "’e kadar · " + (left >= 12 ? (left / 12) + " yıl " : "") + (left % 12) + " ay var"
                        : "5 yılı geçmiş; iyi saklandıysa hâlâ kullanılır")
                + "\n\nKutudaki ve şişedeki kod aynı mı, ona da bak. Çıkış yılıyla karşılaştırma için uygulamada parfümü seç.";
        return r;
    }
}
