package com.parfumcarki.app;

import java.util.Calendar;

/**
 * Reads a batch code without needing the brand: the four layouts we know do not overlap, so the code's own shape says which one it is.
 * LVMH: digit + letter + 2 digits. Estée Lauder: letter + month + year digit. Puig / Coty: year digit + day of year.
 */
final class BatchCodec {
    static final String[] MONTHS = {"Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran", "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık"};

    static final class Result {
        boolean ok;
        String code = "", head = "", group = "", read = "", report = "";
    }

    private BatchCodec() { }

    static Result read(String raw) {
        Result r = new Result();
        String code = raw == null ? "" : raw.toUpperCase(java.util.Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        r.code = code;
        if (code.length() < 3 || code.length() > 12) {
            r.head = "Bu bir parti koduna benzemiyor";
            r.report = "Parti kodu genelde 3–10 harf/rakamdır; kutunun altına basılı, şişenin altına kazınmıştır. Barkodun altındaki 13 haneli sayı parti kodu değildir.";
            return r;
        }
        int yDigit, month = 0, doy = 0;
        if (code.matches("\\d[A-HJ-M]\\d{2}")) {
            r.group = "LVMH (Dior, Guerlain, Givenchy, Kenzo)";
            yDigit = code.charAt(0) - '0';
            month = "ABCDEFGHJKLM".indexOf(code.charAt(1)) + 1;
        } else if (code.matches("[A-Z][1-9ABC]\\d")) {
            r.group = "Estée Lauder (Tom Ford, Jo Malone, Kilian, Le Labo)";
            yDigit = code.charAt(2) - '0';
            month = "123456789ABC".indexOf(code.charAt(1)) + 1;
        } else if (code.matches("\\d{4}[A-Z0-9]{0,6}")) {
            r.group = "Puig / Coty (JPG, Rabanne, C. Herrera, Hugo Boss, Gucci, Burberry)";
            yDigit = code.charAt(0) - '0';
            doy = Integer.parseInt(code.substring(1, 4));
            if (doy < 1 || doy > 366) {
                r.head = "Kod geçerli bir tarih vermiyor";
                r.report = "İlk rakam yıl, sonraki üç rakam yılın kaçıncı günü olmalı (001–366). Kodu tekrar kontrol et.";
                return r;
            }
        } else {
            r.head = "Bildiğim düzenlere uymuyor";
            r.report = "Çözebildiğim düzenler: LVMH (ör. 2K01), Estée Lauder (ör. A83), Puig ve Coty (ör. 3123). Başka bir marka olabilir ya da 0/O, 1/I karışmış olabilir. Uygulamada markayı yazıp tekrar deneyebilirsin.";
            return r;
        }
        Calendar now = Calendar.getInstance();
        int cy = now.get(Calendar.YEAR), cm = now.get(Calendar.MONTH) + 1;
        int y = cy - (((cy - yDigit) % 10) + 10) % 10;
        int day = 0;
        if (doy > 0) {
            Calendar c = Calendar.getInstance();
            c.set(y, Calendar.JANUARY, 1);
            c.set(Calendar.DAY_OF_YEAR, doy);
            month = c.get(Calendar.MONTH) + 1;
            day = c.get(Calendar.DAY_OF_MONTH);
        }
        if (y == cy && month > cm) y -= 10;
        int age = (cy - y) * 12 + (cm - month);
        String when = (day > 0 ? day + " " : "") + MONTHS[month - 1] + " " + y;
        String ageTxt = (age >= 12 ? (age / 12) + " yıl " : "") + (age % 12) + " ay";
        String state = age < 6 ? "çok yeni üretim" : age <= 36 ? "taze" : age <= 60 ? "olgun" : "eski stok";
        int left = 60 - age;
        r.ok = true;
        r.head = "Üretim: " + MONTHS[month - 1] + " " + y + " · " + (age < 12 ? age + " aylık" : (age / 12) + " yıllık");
        r.read = doy > 0 ? code.charAt(0) + " → " + y + " · " + code.substring(1, 4) + " → yılın " + doy + ". günü"
                : r.group.startsWith("Estée") ? code.charAt(1) + " → " + MONTHS[month - 1] + " · " + code.charAt(2) + " → " + y
                : code.charAt(0) + " → " + y + " · " + code.charAt(1) + " → " + MONTHS[month - 1];
        r.report = "Üretim tarihi: " + when
                + "\nŞişenin yaşı: " + ageTxt + " · " + state
                + "\nKodun okunuşu: " + r.read
                + "\nDüzen: " + r.group
                + "\nDiğer olası yıl: " + MONTHS[month - 1] + " " + (y - 10) + " (son hane 10 yılda bir tekrar eder)"
                + "\nEn iyi hali: " + (left > 0 ? "yaklaşık " + MONTHS[month - 1] + " " + (y + 5) + "’e kadar · " + (left >= 12 ? (left / 12) + " yıl " : "") + (left % 12) + " ay var"
                        : "5 yılı geçmiş; iyi saklandıysa hâlâ kullanılır")
                + "\n\nKutudaki ve şişedeki kod aynı mı, ona da bak. Çıkış yılıyla karşılaştırma için uygulamada parfümü seç.";
        return r;
    }
}
