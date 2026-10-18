package me.xmpl.funeffects.service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BurstCooldownTracker {
    private static class BurstState {
        int shotsUsed = 0;
        long cooldownUntilMillis = 0L;
    }

    private final Map<String, BurstState> map = new ConcurrentHashMap<>();

    private String key(UUID uuid, String action) {
        return uuid.toString() + ":" + action;
    }

    /**
     * Проверяет, может ли игрок использовать действие сейчас.
     * Если возвращает > 0 — значит действие на кулдауне (возвращает оставшиеся секунды).
     * Если возвращает 0 — действие успешно использовано!
     */
    public int tryUse(UUID uuid, String action, int maxBurstShots, int cooldownSeconds) {
        String k = key(uuid, action);
        BurstState state = map.computeIfAbsent(k, unused -> new BurstState());
        long now = System.currentTimeMillis();

        if (now < state.cooldownUntilMillis) {
            return (int) Math.max(1L, (long) Math.ceil((state.cooldownUntilMillis - now) / 1000.0));
        }

        state.shotsUsed++;
        if (state.shotsUsed >= Math.max(1, maxBurstShots)) {
            state.shotsUsed = 0;
            state.cooldownUntilMillis = now + (cooldownSeconds * 1000L);
        }
        return 0;
    }
}