package me.xmpl.antirelog.service;

import me.xmpl.antirelog.AntiRelogExtension;
import me.xmpl.antirelog.model.CombatSession;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CombatManager {
    private final AntiRelogExtension extension;
    private final CombatBossBarService bossBarService;
    private final CombatNotificationService notificationService;
    private final Map<UUID, CombatSession> activeSessions = new ConcurrentHashMap<>();

    public CombatManager(AntiRelogExtension extension, CombatBossBarService bossBarService, CombatNotificationService notificationService) {
        this.extension = extension;
        this.bossBarService = bossBarService;
        this.notificationService = notificationService;
    }

    public void startTimerTask() {
        // Запускаем ежесекундный таймер (20 тиков = 1 секунда)
        extension.runTaskTimer(this::tickAllSessions, 20L, 20L);
    }

    public void tagPlayer(Player player, String opponentName) {
        if (player == null || !player.isOnline()) return;

        // 1. Проверка права обхода входа в ПвП режим
        String bypassPerm = extension.getConfig().getString("permissions.bypass-pvp-tag", "antirelog.bypass.tag");
        if (player.hasPermission(bypassPerm)) {
            return;
        }

        int duration = extension.getConfig().getInt("pvp.duration-seconds", 15);

        // 2. Отключение флая и креатива при необходимости
        if (extension.getConfig().getBoolean("pvp.disable-fly-and-gamemode", true)) {
            if (player.isFlying() || player.getAllowFlight()) {
                player.setFlying(false);
                player.setAllowFlight(false);
            }
            if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
                player.setGameMode(GameMode.SURVIVAL);
            }
        }

        CombatSession existing = activeSessions.get(player.getUniqueId());
        if (existing != null) {
            // Игрок уже в ПвП — просто обновляем таймер и полоску до максимума
            existing.resetTimer(opponentName, duration);
            bossBarService.updateBar(existing);
        } else {
            // Игрок впервые вошел в ПвП — создаем полоску босса и шлем большое уведомление
            BossBar bar = bossBarService.createForPlayer(player, opponentName, duration);
            CombatSession session = new CombatSession(player.getUniqueId(), opponentName, duration, bar);
            activeSessions.put(player.getUniqueId(), session);
            notificationService.notifyEnterPvp(player, opponentName, duration);
        }
    }

    public void untagPlayer(Player player, boolean sendNotifications) {
        if (player == null) return;
        CombatSession removed = activeSessions.remove(player.getUniqueId());
        if (removed != null) {
            bossBarService.removeBar(removed);
            if (sendNotifications) {
                notificationService.notifyLeavePvp(player);
            }
        }
    }

    private void tickAllSessions() {
        Iterator<Map.Entry<UUID, CombatSession>> iterator = activeSessions.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, CombatSession> entry = iterator.next();
            CombatSession session = entry.getValue();
            Player player = Bukkit.getPlayer(session.getPlayerUuid());

            if (player == null || !player.isOnline()) {
                bossBarService.removeBar(session);
                iterator.remove();
                continue;
            }

            boolean expired = session.tickSecond();
            if (expired) {
                bossBarService.removeBar(session);
                iterator.remove();
                notificationService.notifyLeavePvp(player);
            } else {
                bossBarService.updateBar(session);
            }
        }
    }

    public boolean isInPvp(Player player) {
        return player != null && activeSessions.containsKey(player.getUniqueId());
    }

    public CombatSession getSession(Player player) {
        return player != null ? activeSessions.get(player.getUniqueId()) : null;
    }

    public void clearAll() {
        for (CombatSession session : activeSessions.values()) {
            bossBarService.removeBar(session);
        }
        activeSessions.clear();
    }
}