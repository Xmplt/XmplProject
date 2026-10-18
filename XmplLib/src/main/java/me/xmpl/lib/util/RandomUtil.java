package me.xmpl.lib.util;

import java.util.concurrent.ThreadLocalRandom;

public final class RandomUtil {
    private RandomUtil() {}

    public static int nextInt(int min, int max) {
        if (min >= max) return min;
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    public static double nextDouble(double min, double max) {
        if (min >= max) return min;
        return ThreadLocalRandom.current().nextDouble(min, max);
    }
}