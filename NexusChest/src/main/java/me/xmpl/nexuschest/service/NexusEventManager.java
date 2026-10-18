package me.xmpl.nexuschest.service;

import me.xmpl.lib.util.ChatUtil;
import me.xmpl.lib.util.RandomUtil;
import me.xmpl.lib.util.TimeUtil;
import me.xmpl.nexuschest.NexusChestExtension;
import me.xmpl.nexuschest.api.NexusChestPreStartEvent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NexusEventManager {
    private final NexusChestExtension extension;
    private boolean eventRunning = false;
    private int secondsUntilStart;
    private int activeSecondsLeft = 0;
    private String currentPinCode = "";
    private String upcomingPinCode = "";
    private boolean preStartFired = false;

    // Игроки, которые легально вошли по 4-значному коду в текущем раунде
    private final Set<UUID> authorizedParticipants = ConcurrentHashMap.newKeySet();
    // Игроки, которые прямо сейчас физически находятся на арене ивента
    private final Set<UUID> activeInsideArena = ConcurrentHashMap.newKeySet();

    public NexusEventManager(NexusChestExtension extension) {
        this.extension = extension;
        int defaultInterval = extension.getConfig().getInt("schedule.interval-seconds", 7200);
        this.secondsUntilStart = extension.getTimerStorage().loadRemainingSeconds(defaultInterval);
        this.upcomingPinCode = generateFourDigitCode();
    }

    private String generateFourDigitCode() {
        return String.valueOf(RandomUtil.nextInt(1000, 9999));
    }

    public void startMasterScheduler() {
        extension.runTaskTimer(this::tickSecond, 20L, 20L);
    }

    private void tickSecond() {
        FileConfiguration cfg = extension.getConfig();

        // 1. Проверка зоны арены и голограмм для всех онлайн-игроков
        for (Player player : Bukkit.getOnlinePlayers()) {
            extension.getHologramService().updateVisibilityForPlayer(player);
            checkArenaSecurityForPlayer(player);
        }

        // 2. Если ивент сейчас ИДЕТ (5 минут)
        if (eventRunning) {
            activeSecondsLeft--;
            int broadcastInterval = Math.max(5, cfg.getInt("schedule.code-broadcast-interval-seconds", 30));

            if (activeSecondsLeft > 0 && activeSecondsLeft % broadcastInterval == 0) {
                String msg = extension.getCustomConfig("messages.yml").get().getString("event-broadcasts.running-info");
                Bukkit.broadcastMessage(ChatUtil.color(msg.replace("{code}", currentPinCode)));
            }

            if (activeSecondsLeft <= 0) {
                stopEvent();
            }
            return;
        }

        // 3. Если ивент в режиме ожидания (2 часа) — проверяем минимальный онлайн!
        int minOnline = cfg.getInt("schedule.min-online-players", 2);
        if (Bukkit.getOnlinePlayers().size() < minOnline) {
            // Онлайн меньше нужного -> таймер ОСТАНАВЛИВАЕТСЯ (не сбрасываясь!) и ждет игроков
            return;
        }

        secondsUntilStart--;

        // Проверяем, не пора ли вызвать событие для будущей платной подписки (заранее слить код)
        int preNotifySec = cfg.getInt("schedule.pre-start-notify-seconds", 60);
        if (!preStartFired && secondsUntilStart == preNotifySec) {
            preStartFired = true;
            Bukkit.getPluginManager().callEvent(new NexusChestPreStartEvent(upcomingPinCode, secondsUntilStart));
        }

        // Периодическое авто-сохранение таймера в БД на случай внезапного краша
        int saveInterval = Math.max(5, cfg.getInt("schedule.save-timer-state-interval-seconds", 10));
        if (secondsUntilStart > 0 && secondsUntilStart % saveInterval == 0) {
            extension.getTimerStorage().saveRemainingSecondsAsync(secondsUntilStart);
        }

        if (secondsUntilStart <= 0) {
            startEvent();
        }
    }

    public void startEvent() {
        FileConfiguration cfg = extension.getConfig();
        this.eventRunning = true;
        this.activeSecondsLeft = cfg.getInt("schedule.active-duration-seconds", 300);
        this.currentPinCode = upcomingPinCode != null && !upcomingPinCode.isEmpty() ? upcomingPinCode : generateFourDigitCode();
        this.upcomingPinCode = generateFourDigitCode();
        this.preStartFired = false;
        this.authorizedParticipants.clear();

        // Сбрасываем таймер следующего ивента на полные 2 часа и сохраняем в БД
        this.secondsUntilStart = cfg.getInt("schedule.interval-seconds", 7200);
        extension.getTimerStorage().saveRemainingSecondsAsync(this.secondsUntilStart);

        // Заполняем сундуки #1 и #2 рандомным лутом
        extension.getLootService().resetRoundAndPopulateChests();

        String startMsg = extension.getCustomConfig("messages.yml").get().getString("event-broadcasts.started");
        Bukkit.broadcastMessage(ChatUtil.color(startMsg.replace("{code}", currentPinCode)));
    }

    public void stopEvent() {
        this.eventRunning = false;
        this.activeSecondsLeft = 0;
        this.currentPinCode = "";
        this.authorizedParticipants.clear();

        extension.getLootService().clearBothChests();

        // Выкидываем всех оставшихся игроков из арены на спавн
        Location spawnLoc = extension.getArenaStorage().getExpelSpawnLocation();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (extension.getArenaStorage().isInArenaRegion(player.getLocation())) {
                if (spawnLoc != null) player.teleport(spawnLoc);
            }
        }
        activeInsideArena.clear();

        String endMsg = extension.getCustomConfig("messages.yml").get().getString("event-broadcasts.ended");
        Bukkit.broadcastMessage(ChatUtil.color(endMsg));
    }

    public void handlePlayerEnterByCode(Player player, String codeArg) {
        FileConfiguration msgCfg = extension.getCustomConfig("messages.yml").get();
        if (!eventRunning) {
            String timeStr = TimeUtil.formatDuration(secondsUntilStart * 1000L);
            ChatUtil.sendMessage(player, msgCfg.getString("teleport-and-access.event-not-running"), "{time}", timeStr);
            return;
        }

        if (!currentPinCode.equals(codeArg.trim())) {
            ChatUtil.sendMessage(player, msgCfg.getString("teleport-and-access.wrong-code"));
            return;
        }

        Location enterLoc = extension.getArenaStorage().getEnterLocation();
        if (enterLoc == null) {
            ChatUtil.sendMessage(player, "&cТочка входа на арену еще не установлена администратором (/nchest setenter)!");
            return;
        }

        // Разрешаем доступ этому игроку и телепортируем на старт паркура (входить по коду можно сколько угодно раз!)
        authorizedParticipants.add(player.getUniqueId());
        activeInsideArena.add(player.getUniqueId());
        player.teleport(enterLoc);
        extension.getHologramService().updateVisibilityForPlayer(player);

        ChatUtil.sendMessage(player, msgCfg.getString("teleport-and-access.teleported-to-event"));
    }

    /**
     * Проверяет, легально ли игрок находится внутри региона NexusChest.
     * Если залез читом, в нерабочее время или без ввода кода — жестко телепортирует на спавн!
     */
    public void checkArenaSecurityForPlayer(Player player) {
        boolean inRegion = extension.getArenaStorage().isInArenaRegion(player.getLocation());
        if (!inRegion) {
            activeInsideArena.remove(player.getUniqueId());
            return;
        }

        // Админу в креативе разрешаем настраивать арену в любое время
        String adminPerm = extension.getConfig().getString("permissions.admin", "nexuschest.admin");
        if (player.getGameMode() == GameMode.CREATIVE && player.hasPermission(adminPerm)) {
            activeInsideArena.add(player.getUniqueId());
            return;
        }

        if (!extension.getConfig().getBoolean("security.strict-region-kick-to-spawn", true)) {
            activeInsideArena.add(player.getUniqueId());
            return;
        }

        // Если ивент не идет ИЛИ игрок не вводил 4-значный код — мгновенно выкидываем на спавн!
        if (!eventRunning || !authorizedParticipants.contains(player.getUniqueId())) {
            activeInsideArena.remove(player.getUniqueId());
            Location spawnLoc = extension.getArenaStorage().getExpelSpawnLocation();
            if (spawnLoc != null) {
                player.teleport(spawnLoc);
            }
            String kickMsg = extension.getCustomConfig("messages.yml").get().getString("teleport-and-access.illegal-entry-kicked");
            ChatUtil.sendMessage(player, kickMsg);
        } else {
            activeInsideArena.add(player.getUniqueId());
        }
    }

    public boolean isPlayerActivelyOnEvent(Player player) {
        return player != null && activeInsideArena.contains(player.getUniqueId())
                && extension.getArenaStorage().isInArenaRegion(player.getLocation());
    }

    public boolean isEventRunning() { return eventRunning; }
    public int getSecondsUntilStart() { return secondsUntilStart; }
    public int getActiveSecondsLeft() { return activeSecondsLeft; }
    public String getCurrentPinCode() { return currentPinCode; }
}