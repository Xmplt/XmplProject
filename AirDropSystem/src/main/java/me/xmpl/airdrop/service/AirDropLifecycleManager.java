package me.xmpl.airdrop.service;

import me.xmpl.airdrop.AirDropExtension;
import me.xmpl.airdrop.api.AirDropFirstActivateEvent;
import me.xmpl.airdrop.api.AirDropPreSpawnWarningEvent;
import me.xmpl.airdrop.model.AirDropStage;
import me.xmpl.airdrop.model.PersistedAirDropState;
import me.xmpl.lib.util.BlockAndGlowUtil;
import me.xmpl.lib.util.ChatUtil;
import me.xmpl.lib.util.RandomUtil;
import me.xmpl.lib.util.TimeUtil;
import me.xmpl.lib.util.TitleUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class AirDropLifecycleManager {
    private final AirDropExtension extension;
    private PersistedAirDropState state;
    private int droppingTaskId = -1;
    private String lastChosenWorld = null;

    public AirDropLifecycleManager(AirDropExtension extension) {
        this.extension = extension;
        int defaultInterval = extension.getConfig().getInt("timers.spawn-interval-seconds", 3600);
        this.state = extension.getStateStorage().loadState(defaultInterval);
        restoreChestIfMidEvent();
    }

    private void restoreChestIfMidEvent() {
        if (state.stage != AirDropStage.IDLE && state.chestLocation != null && state.chestLocation.getWorld() != null) {
            placeChestBlockInWorld(state.chestLocation);
            if (state.stage == AirDropStage.DROPPING) {
                startDroppingItemsTask();
            }
        } else if (state.stage != AirDropStage.IDLE) {
            state.stage = AirDropStage.IDLE;
            state.timerSeconds = extension.getConfig().getInt("timers.spawn-interval-seconds", 3600);
        }
    }

    public void startMasterClock() {
        extension.runTaskTimer(this::tickSecond, 20L, 20L);
    }

    private void tickSecond() {
        FileConfiguration cfg = extension.getConfig();

        switch (state.stage) {
            case IDLE:
                tickIdleCountdown(cfg);
                break;
            case WAITING_ACTIVATION:
                tickWaitingActivation(cfg);
                break;
            case UNLOCKING:
                tickUnlocking();
                break;
            case DROPPING:
                extension.getHologramService().updateHologram(state.chestLocation, AirDropStage.DROPPING, "");
                break;
            case RELOADING:
                tickReloading();
                break;
            case FINISHED:
                tickFinishedDespawn();
                break;
        }

        if (state.timerSeconds % 5 == 0) {
            extension.getStateStorage().saveStateAsync(state);
        }
    }

    private void tickIdleCountdown(FileConfiguration cfg) {
        int minOnline = cfg.getInt("settings.min-online-players", 2);
        if (Bukkit.getOnlinePlayers().size() < minOnline) {
            return;
        }

        if (extension.getLocationFinder().isSearchInProgress()) {
            return;
        }

        state.timerSeconds--;

        int warnSec = cfg.getInt("timers.pre-spawn-warning-seconds", 300);
        if (state.timerSeconds == warnSec) {
            extension.getLocationFinder().findRandomWorldAndLocationNonBlocking(lastChosenWorld, (chosenWorld, safeLoc) -> {
                if (chosenWorld != null && safeLoc != null) {
                    lastChosenWorld = chosenWorld;
                    state.worldKey = chosenWorld;
                    state.chestLocation = safeLoc;
                    extension.getRestrictionRoller().rollRestrictionsForWorld(state, chosenWorld);

                    Bukkit.getPluginManager().callEvent(new AirDropPreSpawnWarningEvent(
                            safeLoc.clone(), state.restrictElytra, state.restrictPearl, state.restrictChorus
                    ));
                    broadcastPreSpawnWarning();
                }
            });
        }

        if (state.timerSeconds <= 0) {
            spawnAirDropChestNow();
        }
    }

    private void tickWaitingActivation(FileConfiguration cfg) {
        state.timerSeconds--;
        extension.getHologramService().updateHologram(state.chestLocation, AirDropStage.WAITING_ACTIVATION, "");

        int reminderInterval = Math.max(10, cfg.getInt("timers.waiting-reminder-interval-seconds", 60));
        if (state.timerSeconds > 0 && state.timerSeconds % reminderInterval == 0) {
            broadcastMessageList("waiting-reminder");
        }

        if (state.timerSeconds <= 0) {
            String msg = extension.getCustomConfig("messages.yml").get().getString("expired-not-activated");
            Bukkit.broadcastMessage(ChatUtil.color(msg));
            despawnAndResetToIdle();
        }
    }

    private void tickUnlocking() {
        state.timerSeconds--;
        String timeStr = TimeUtil.formatDuration(state.timerSeconds * 1000L);
        extension.getHologramService().updateHologram(state.chestLocation, AirDropStage.UNLOCKING, timeStr);

        if (state.timerSeconds <= 0) {
            beginDropPhase(1);
        }
    }

    private void tickReloading() {
        state.timerSeconds--;
        String timeStr = TimeUtil.formatDuration(state.timerSeconds * 1000L);
        extension.getHologramService().updateHologram(state.chestLocation, AirDropStage.RELOADING, timeStr);

        if (state.timerSeconds <= 0) {
            beginDropPhase(state.currentPhase + 1);
        }
    }

    private void tickFinishedDespawn() {
        state.timerSeconds--;
        String timeStr = TimeUtil.formatDuration(state.timerSeconds * 1000L);
        extension.getHologramService().updateHologram(state.chestLocation, AirDropStage.FINISHED, timeStr);

        if (state.timerSeconds <= 0) {
            String msg = extension.getCustomConfig("messages.yml").get().getString("despawned-finished");
            Bukkit.broadcastMessage(ChatUtil.color(msg));
            despawnAndResetToIdle();
        }
    }

    public void spawnAirDropChestNow() {
        cleanupCurrentChestBlockAndRegion();

        if (state.worldKey != null && state.chestLocation != null) {
            finalizeChestSpawnAtPreparedLocation();
            return;
        }

        extension.getLocationFinder().findRandomWorldAndLocationNonBlocking(lastChosenWorld, (chosenWorld, safeLoc) -> {
            if (chosenWorld == null || safeLoc == null) {
                state.stage = AirDropStage.IDLE;
                state.timerSeconds = 15;
                return;
            }
            lastChosenWorld = chosenWorld;
            state.worldKey = chosenWorld;
            state.chestLocation = safeLoc;
            extension.getRestrictionRoller().rollRestrictionsForWorld(state, chosenWorld);
            finalizeChestSpawnAtPreparedLocation();
        });
    }

    private void finalizeChestSpawnAtPreparedLocation() {
        placeChestBlockInWorld(state.chestLocation);
        state.wgRegionId = extension.getRegionService().create5x5Region(state.chestLocation);
        state.stage = AirDropStage.WAITING_ACTIVATION;
        state.timerSeconds = extension.getConfig().getInt("timers.waiting-activation-seconds", 600);

        state.totalPhasesForRound = rollTotalPhasesForWorld(state.worldKey);
        state.currentPhase = 1;

        extension.getHologramService().updateHologram(state.chestLocation, AirDropStage.WAITING_ACTIVATION, "");
        broadcastMessageList("spawned-chat");

        FileConfiguration msgCfg = extension.getCustomConfig("messages.yml").get();
        String mainTitle = msgCfg.getString("spawned-title", "&a&lСУНДУК ПО КООРДИНАТАМ ПОЯВИЛСЯ!");
        String subTitle = msgCfg.getString("spawned-subtitle", "&fКоординаты в чате");
        int fadeIn = msgCfg.getInt("title-fade-in", 5);
        int stay = msgCfg.getInt("title-stay", 65);
        int fadeOut = msgCfg.getInt("title-fade-out", 15);

        for (Player p : Bukkit.getOnlinePlayers()) {
            TitleUtil.sendTitle(p, mainTitle, subTitle, fadeIn, stay, fadeOut);
        }

        extension.getStateStorage().saveStateAsync(state);
    }

    private int rollTotalPhasesForWorld(String worldKey) {
        FileConfiguration wCfg = extension.getCustomConfig("worlds.yml").get();
        String base = "worlds." + worldKey;
        int minPhases = Math.max(1, wCfg.getInt(base + ".phases-min", 2));
        ConfigurationSection phasesSec = wCfg.getConfigurationSection(base + ".phases");
        int maxConfiguredPhase = minPhases;
        if (phasesSec != null && !phasesSec.getKeys(false).isEmpty()) {
            maxConfiguredPhase = Math.max(minPhases, phasesSec.getKeys(false).size());
        }
        return RandomUtil.nextInt(minPhases, maxConfiguredPhase);
    }

    public void activateChestByPlayer(Player activator) {
        if (state.stage != AirDropStage.WAITING_ACTIVATION) return;

        state.stage = AirDropStage.UNLOCKING;
        state.timerSeconds = extension.getConfig().getInt("timers.unlocking-seconds", 60);
        extension.getStateStorage().saveStateAsync(state);

        Bukkit.getPluginManager().callEvent(new AirDropFirstActivateEvent(activator, state.chestLocation.clone()));

        String msg = extension.getCustomConfig("messages.yml").get().getString("activated-chat");
        Bukkit.broadcastMessage(ChatUtil.color(ChatUtil.applyReplacements(msg,
                "{player}", activator.getName(),
                "{time}", TimeUtil.formatDuration(state.timerSeconds * 1000L))));
    }

    private void beginDropPhase(int phaseNumber) {
        state.currentPhase = phaseNumber;
        state.stage = AirDropStage.DROPPING;

        FileConfiguration wCfg = extension.getCustomConfig("worlds.yml").get();
        String pPath = "worlds." + state.worldKey + ".phases." + phaseNumber;
        int minItems = wCfg.getInt(pPath + ".min", 3);
        int maxItems = wCfg.getInt(pPath + ".max", 5);
        state.remainingItemsInCurrentPhase = RandomUtil.nextInt(minItems, maxItems);

        extension.getStateStorage().saveStateAsync(state);
        startDroppingItemsTask();
    }

    private void cancelDroppingTaskIfActive() {
        if (droppingTaskId != -1) {
            Bukkit.getScheduler().cancelTask(droppingTaskId);
            droppingTaskId = -1;
        }
    }

    private void startDroppingItemsTask() {
        cancelDroppingTaskIfActive();

        if (state.chestLocation != null) {
            BlockAndGlowUtil.playChestLidAnimation(state.chestLocation.getBlock(), true);
        }

        long ticksBetweenItems = Math.max(2L, extension.getConfig().getLong("drop-physics.ticks-between-items", 6L));
        droppingTaskId = extension.runTaskTimer(() -> {
            if (state.stage != AirDropStage.DROPPING || state.chestLocation == null) {
                cancelDroppingTaskIfActive();
                return;
            }

            if (state.remainingItemsInCurrentPhase <= 0) {
                cancelDroppingTaskIfActive();
                finishCurrentDropPhase();
                return;
            }

            state.remainingItemsInCurrentPhase--;
            ItemStack itemToDrop = extension.getLootStorage().pickRandomLootItem();
            if (itemToDrop != null) {
                extension.getFountainPhysics().launchSingleItemFromChest(state.chestLocation, state.worldKey, itemToDrop);
            }
        }, 5L, ticksBetweenItems);
    }

    private void finishCurrentDropPhase() {
        if (state.chestLocation != null) {
            BlockAndGlowUtil.playChestLidAnimation(state.chestLocation.getBlock(), false);
        }

        if (state.currentPhase < state.totalPhasesForRound) {
            state.stage = AirDropStage.RELOADING;
            state.timerSeconds = extension.getConfig().getInt("timers.reloading-between-phases-seconds", 60);
        } else {
            state.stage = AirDropStage.FINISHED;
            state.timerSeconds = extension.getConfig().getInt("timers.finished-despawn-seconds", 300);

            // Немедленно снимаем все ограничения мира после выпадения всех предметов
            state.restrictElytra = false;
            state.restrictPearl = false;
            state.restrictChorus = false;

            // Глобальное оповещение о завершении выпадения предметов и снятии ограничений
            String finishedMsg = extension.getCustomConfig("messages.yml").get().getString(
                    "finished",
                    "&aВсе предметы выпали! Ограничения сняты. Сундук исчезнет через {time}"
            );
            if (finishedMsg != null && !finishedMsg.isEmpty()) {
                String timeStr = TimeUtil.formatDuration(state.timerSeconds * 1000L);
                Bukkit.broadcastMessage(ChatUtil.color(finishedMsg.replace("{time}", timeStr)));
            }
        }
        extension.getStateStorage().saveStateAsync(state);
    }

    public void despawnAndResetToIdle() {
        cancelDroppingTaskIfActive();
        cleanupCurrentChestBlockAndRegion();

        state.stage = AirDropStage.IDLE;
        state.timerSeconds = extension.getConfig().getInt("timers.spawn-interval-seconds", 3600);
        state.chestLocation = null;
        state.worldKey = null;
        state.wgRegionId = null;
        state.restrictElytra = false;
        state.restrictPearl = false;
        state.restrictChorus = false;
        state.remainingItemsInCurrentPhase = 0;

        extension.getStateStorage().saveStateAsync(state);
    }

    private void cleanupCurrentChestBlockAndRegion() {
        extension.getHologramService().removeHologramForAll();
        if (state.chestLocation != null && state.chestLocation.getWorld() != null) {
            state.chestLocation.getBlock().setType(Material.AIR);
            extension.getRegionService().removeRegion(state.chestLocation, state.wgRegionId);
        }
    }

    private void placeChestBlockInWorld(Location loc) {
        Material mat = Material.matchMaterial(extension.getConfig().getString("settings.chest-material", "ENDER_CHEST"));
        loc.getBlock().setType(mat != null ? mat : Material.ENDER_CHEST);
    }

    private void broadcastPreSpawnWarning() {
        FileConfiguration msgCfg = extension.getCustomConfig("messages.yml").get();
        List<String> normalLines = msgCfg.getStringList("spawn-warning");
        List<String> subLines = msgCfg.getStringList("spawn-warning-subscriber");
        String subPerm = extension.getConfig().getString("permissions.subscription-reveal-coords", "airdropsystem.subscriber.coords");

        String worldDisplay = getWorldDisplayName(state.worldKey);
        String timeStr = TimeUtil.formatDuration(state.timerSeconds * 1000L);
        String elytraTag = extension.getRestrictionRoller().formatRestrictionTag(state.restrictElytra);
        String pearlTag = extension.getRestrictionRoller().formatRestrictionTag(state.restrictPearl);
        String chorusTag = extension.getRestrictionRoller().formatRestrictionTag(state.restrictChorus);

        int x = state.chestLocation != null ? state.chestLocation.getBlockX() : 0;
        int y = state.chestLocation != null ? state.chestLocation.getBlockY() : 0;
        int z = state.chestLocation != null ? state.chestLocation.getBlockZ() : 0;

        for (Player player : Bukkit.getOnlinePlayers()) {
            List<String> targetList = (!player.isOp() && player.hasPermission(subPerm)) ? subLines : normalLines;
            ChatUtil.sendMessages(player, targetList,
                    "{time}", timeStr,
                    "{world}", worldDisplay,
                    "{x}", x, "{y}", y, "{z}", z,
                    "{elytra}", elytraTag,
                    "{pearl}", pearlTag,
                    "{chorus}", chorusTag);
        }
    }

    private void broadcastMessageList(String key) {
        List<String> lines = extension.getCustomConfig("messages.yml").get().getStringList(key);
        if (lines == null || lines.isEmpty() || state.chestLocation == null) return;

        String worldDisplay = getWorldDisplayName(state.worldKey);
        String elytraTag = extension.getRestrictionRoller().formatRestrictionTag(state.restrictElytra);
        String pearlTag = extension.getRestrictionRoller().formatRestrictionTag(state.restrictPearl);
        String chorusTag = extension.getRestrictionRoller().formatRestrictionTag(state.restrictChorus);

        for (String line : lines) {
            Bukkit.broadcastMessage(ChatUtil.color(ChatUtil.applyReplacements(line,
                    "{world}", worldDisplay,
                    "{x}", state.chestLocation.getBlockX(),
                    "{y}", state.chestLocation.getBlockY(),
                    "{z}", state.chestLocation.getBlockZ(),
                    "{elytra}", elytraTag,
                    "{pearl}", pearlTag,
                    "{chorus}", chorusTag)));
        }
    }

    public String getWorldDisplayName(String worldKey) {
        if (worldKey == null) return "Неизвестно";
        return ChatUtil.color(extension.getCustomConfig("worlds.yml").get()
                .getString("worlds." + worldKey + ".display-name", worldKey));
    }

    public PersistedAirDropState getState() { return state; }
}