package me.xmpl.lib.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class NumberDateUtil {
    private static final DecimalFormat THOUSANDS_FORMAT;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd.MM.yyyy HH:mm");

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setGroupingSeparator(' ');
        THOUSANDS_FORMAT = new DecimalFormat("#,###", symbols);
    }

    private NumberDateUtil() {}

    public static String formatInteger(long value) {
        return THOUSANDS_FORMAT.format(value);
    }

    public static String formatDate(long timestamp) {
        return DATE_FORMAT.format(new Date(timestamp));
    }

    /**
     * Парсит строго целое положительное число. Если ввели дробное (10.5) или буквы — вернет -1.
     */
    public static long parseStrictPositiveLong(String input) {
        if (input == null || input.isEmpty()) return -1L;
        for (int i = 0; i < input.length(); i++) {
            if (!Character.isDigit(input.charAt(i))) {
                return -1L;
            }
        }
        try {
            long val = Long.parseLong(input);
            return val > 0 ? val : -1L;
        } catch (NumberFormatException e) {
            return -1L;
        }
    }
}