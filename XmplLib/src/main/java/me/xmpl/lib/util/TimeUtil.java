package me.xmpl.lib.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TimeUtil {
    private static final Pattern TIME_PATTERN = Pattern.compile("^(\\d+)(y|г|лет|mo|мес|d|д|дн|h|ч|m|м|мин|s|с|сек)$", Pattern.CASE_INSENSITIVE);

    private TimeUtil() {}

    /**
     * Проверяет, является ли аргумент куском времени (например "1d", "2h", "30m", "1y", "2mo")
     */
    public static boolean isTimeToken(String token) {
        if (token == null) return false;
        return TIME_PATTERN.matcher(token.trim()).matches();
    }

    /**
     * Парсит один токен времени в миллисекунды
     */
    public static long parseTokenMillis(String token) {
        Matcher m = TIME_PATTERN.matcher(token.trim());
        if (!m.matches()) return 0L;
        long val = Long.parseLong(m.group(1));
        String unit = m.group(2).toLowerCase();

        switch (unit) {
            case "y": case "г": case "лет":
                return val * 365L * 24L * 3600L * 1000L;
            case "mo": case "мес":
                return val * 30L * 24L * 3600L * 1000L;
            case "d": case "д": case "дн":
                return val * 24L * 3600L * 1000L;
            case "h": case "ч":
                return val * 3600L * 1000L;
            case "m": case "м": case "мин":
                return val * 60L * 1000L;
            case "s": case "с": case "сек":
                return val * 1000L;
            default:
                return 0L;
        }
    }

    /**
     * Форматирует длительность в миллисекундах в вид: "X лет X мес. X дн. X ч. X мин. X сек."
     */
    public static String formatDuration(long millis) {
        if (millis <= 0) return "0 сек.";
        long totalSeconds = millis / 1000L;

        long years = totalSeconds / (365L * 86400L);
        totalSeconds %= (365L * 86400L);

        long months = totalSeconds / (30L * 86400L);
        totalSeconds %= (30L * 86400L);

        long days = totalSeconds / 86400L;
        totalSeconds %= 86400L;

        long hours = totalSeconds / 3600L;
        totalSeconds %= 3600L;

        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;

        StringBuilder sb = new StringBuilder();
        if (years > 0) sb.append(years).append(" г. ");
        if (months > 0) sb.append(months).append(" мес. ");
        if (days > 0) sb.append(days).append(" дн. ");
        if (hours > 0) sb.append(hours).append(" ч. ");
        if (minutes > 0) sb.append(minutes).append(" мин. ");
        if (seconds > 0 || sb.length() == 0) sb.append(seconds).append(" сек.");

        return sb.toString().trim();
    }
}