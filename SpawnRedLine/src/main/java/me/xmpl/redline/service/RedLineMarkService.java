package me.xmpl.redline.service;

import me.xmpl.lib.util.ActionBarUtil;
import me.xmpl.lib.util.ChatUtil;
import me.xmpl.redline.SpawnRedLineExtension;
import me.xmpl.redline.model.PlayerMarkData;
import me.xmpl.redline.model.RedLineZone;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RedLineMarkService {
    private final SpawnRedLineExtension extension;
    private final ZoneDetectorService zoneDetector;
    private final Map<UUID, PlayerMarkData> playerDataMap = new ConcurrentHashMap<>();

    public RedLineMarkService(SpawnRedLineExtension extension, ZoneDetectorService zoneDetector) {
        this.extension = extension;
        this.zoneDetector = zoneDetector;
    }

    public PlayerMarkData getData(Player player) {
        return playerDataMap.computeIfAbsent(player.getUniqueId(), k -> {
            PlayerMarkData d = new PlayerMarkData();
            d.setLastZone(zoneDetector.detectZone(player.getLocation()));
            return d;
        });
    }

    public void removePlayer(UUID uuid) {
        playerDataMap.remove(uuid);
    }

    public boolean isMarkedAndVulnerable(Player player) {
        if (player == null || !player.isOnline()) return false;
        PlayerMarkData data = getData(player);
        if (data.isMarked() && data.isTimerRunningInSafeSpawn() && !data.isCurrentlyVulnerable()) {
            data.clearMark();
            return false;
        }
        return data.isCurrentlyVulnerable();
    }

    /**
     * Запускает проверку таймеров для игроков, вернувшихся на мирный спавн с меткой
     */
    public void startExpirationTask() {
        extension.runTaskTimer(() -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                PlayerMarkData data = getData(player);
                if (!data.isMarked() || !data.isTimerRunningInSafeSpawn()) continue;

                if (!data.isCurrentlyVulnerable()) {
                    // 20 секунд тихо истекли в фоне — снимаем метку
                    data.clearMark();
                }
            }
        }, 10L, 10L);
    }

    /**
     * Обрабатывает обычное передвижение игрока между зонами
     */
    public void handleZoneTransition(Player player, Location toLocation) {
        if (hasBypass(player)) return;

        PlayerMarkData data = getData(player);
        RedLineZone oldZone = data.getLastZone();
        RedLineZone newZone = zoneDetector.detectZone(toLocation);

        if (oldZone == newZone) return;
        data.setLastZone(newZone);

        int durationSec = extension.getConfig().getInt("mechanics.vulnerability-seconds", 20);

        // СЦЕНАРИЙ 1: Игрок вышел с МИРНОГО СПАВНА (SAFE_SPAWN) за красную линию в БОЕВУЮ ЗОНУ (PVP_RING)
        if (oldZone == RedLineZone.SAFE_SPAWN && newZone == RedLineZone.PVP_RING) {
            boolean wasAlreadyVulnerable = data.isCurrentlyVulnerable();
            data.activateInPvpRing();
            extension.getStatsStorage().incrementCrossings(player.getName());

            // Отправляем сообщение только если на игрока накладывается метка (или он вышел со спавна) + проверяем анти-спам кулдаун!
            if (!wasAlreadyVulnerable || canSendWarningMessage(data)) {
                sendLeaveSpawnWarningWithCooldown(player, data, durationSec);
            }
            return;
        }

        // СЦЕНАРИЙ 2: Игрок с меткой вернулся из БОЕВОЙ ЗОНЫ (PVP_RING) обратно на МИРНЫЙ СПАВН (SAFE_SPAWN)
        if (oldZone == RedLineZone.PVP_RING && newZone == RedLineZone.SAFE_SPAWN) {
            if (data.isMarked()) {
                // Запускаем отсчет 20 секунд, в течение которых его всё еще могут бить на спавне другие помеченные игроки!
                data.startSafeSpawnCountdown(durationSec);
            }
            return;
        }

        // СЦЕНАРИЙ 3: Игрок покинул боевой регион и убежал дальше в открытый мир (WILDERNESS)
        if (newZone == RedLineZone.WILDERNESS) {
            // Метка мгновенно снимается!
            data.clearMark();
            return;
        }

        // СЦЕНАРИЙ 4: Игрок зашел в PVP_RING снаружи (из WILDERNESS -> PVP_RING)
        // По ТЗ: сообщение в чат НЕ пишется, так как он зашел НЕ со спавна!
        // Но чтобы внутри PVP_RING и при забегании на спавн его могли бить, помечаем тихо без сообщения,
        // либо активируем только если он пересечет красную линию со спавна.
        // По твоему условию: "в чат не напишет что игрок уязвим если он еще раз зайдет в этот регион не со спавна"
        if (oldZone == RedLineZone.WILDERNESS && newZone == RedLineZone.PVP_RING) {
            data.activateInPvpRing();
        }
    }

    /**
     * Обрабатывает телепортацию игрока (/spawn, /warp, эндер-перл и т.д.)
     */
    public void handleTeleport(Player player, Location from, Location to) {
        PlayerMarkData data = getData(player);
        RedLineZone targetZone = zoneDetector.detectZone(to);
        data.setLastZone(targetZone);

        if (!data.isCurrentlyVulnerable()) {
            data.clearMark();
            return;
        }

        FileConfiguration tpCfg = extension.getCustomConfig("teleports.yml").get();
        long graceMs = tpCfg.getLong("teleport-rules.spawn-command-grace-ms", 1500L);
        boolean usedSpawnCommandRecently = (System.currentTimeMillis() - data.getLastSpawnCommandTypedMillis()) <= graceMs;

        // 1. Если игрок телепортировался в дикий мир (не на спавн) — метка снимается всегда
        if (targetZone == RedLineZone.WILDERNESS) {
            data.clearMark();
            return;
        }

        // 2. Если игрок телепортировался на МИРНЫЙ СПАВН (SAFE_SPAWN):
        if (targetZone == RedLineZone.SAFE_SPAWN) {
            if (usedSpawnCommandRecently && tpCfg.getBoolean("teleport-rules.clear-mark-on-spawn-command", true)) {
                // Телепортировался именно через /spawn -> метка пропадает!
                data.clearMark();
                data.setLastSpawnCommandTypedMillis(0L);
            } else {
                // Телепортировался на варп (который находится на спавне) или перлом -> метка ОСТАЕТСЯ на 20 секунд!
                int durationSec = extension.getConfig().getInt("mechanics.vulnerability-seconds", 20);
                data.startSafeSpawnCountdown(durationSec);
            }
            return;
        }

        // 3. Если телепортировался внутри боевого кольца PVP_RING — метка остается активной
        if (targetZone == RedLineZone.PVP_RING) {
            data.activateInPvpRing();
        }
    }

    private boolean canSendWarningMessage(PlayerMarkData data) {
        int cooldownSec = extension.getConfig().getInt("mechanics.message-cooldown-seconds", 5);
        long now = System.currentTimeMillis();
        return (now - data.getLastMessageSentMillis()) >= (cooldownSec * 1000L);
    }

    private void sendLeaveSpawnWarningWithCooldown(Player player, PlayerMarkData data, int durationSec) {
        if (!canSendWarningMessage(data)) return;
        data.setLastMessageSentMillis(System.currentTimeMillis());

        String rawMsg = extension.getCustomConfig("messages.yml").get()
                .getString("leave-safe-spawn", "&8Вы покинули зону спавна! Даже если вы вернетесь, вас могут бить еще %time% секунд.");
        ChatUtil.sendMessage(player, rawMsg.replace("%time%", String.valueOf(durationSec)));
    }

    private boolean hasBypass(Player player) {
        if (!extension.getConfig().getBoolean("permissions.enable-bypass-permission", false)) {
            return false;
        }
        String perm = extension.getConfig().getString("permissions.bypass", "spawnredline.bypass");
        return player.hasPermission(perm);
    }
}