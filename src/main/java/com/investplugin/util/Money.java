package com.investplugin.util;

import java.math.BigDecimal;
import java.util.Locale;

/** Money / number helpers. All amounts are rounded to 2 decimal places. */
public final class Money {

    private Money() {}

    public static double round2(double v) {
        return Math.round(v * 100.0) / 100.0 + 0.0; // + 0.0 turns -0.0 into 0.0
    }

    public static double floor2(double v) {
        return Math.floor(v * 100.0) / 100.0 + 0.0;
    }

    public static String format(double v, String symbol) {
        return symbol + String.format(Locale.US, "%,.2f", v);
    }

    public static String signed(double v, String symbol) {
        String sign = v < 0 ? "-" : "+";
        return sign + symbol + String.format(Locale.US, "%,.2f", Math.abs(v));
    }

    public static String percent(double p) {
        return String.format(Locale.US, "%+.2f%%", p);
    }

    /** 5.0 -> "5", 2.5 -> "2.5" */
    public static String plain(double v) {
        return BigDecimal.valueOf(v).stripTrailingZeros().toPlainString();
    }

    public static String duration(long millis) {
        long minutes = millis / 60_000L;
        if (minutes < 1) return "<1m";
        long h = minutes / 60;
        long m = minutes % 60;
        return h > 0 ? h + "h " + m + "m" : m + "m";
    }

    /** Parses "1500", "1,500.50", "10k", "2.5m", "1b". Returns NaN when invalid. */
    public static double parse(String input) {
        if (input == null) return Double.NaN;
        String s = input.trim().toLowerCase(Locale.ROOT).replace(",", "").replace("_", "");
        if (s.isEmpty()) return Double.NaN;
        double mult = 1;
        char last = s.charAt(s.length() - 1);
        if (last == 'k') mult = 1_000;
        else if (last == 'm') mult = 1_000_000;
        else if (last == 'b') mult = 1_000_000_000;
        if (mult != 1) s = s.substring(0, s.length() - 1);
        if (!s.matches("\\d+(\\.\\d+)?|\\.\\d+")) return Double.NaN;
        double v = Double.parseDouble(s) * mult;
        return Double.isFinite(v) ? v : Double.NaN;
    }
}
