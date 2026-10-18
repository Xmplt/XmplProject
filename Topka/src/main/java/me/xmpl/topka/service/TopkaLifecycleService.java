package me.xmpl.topka.service;

import me.xmpl.lib.util.ChatUtil;
import me.xmpl.lib.util.RandomUtil;
import me.xmpl.topka.TopkaExtension;
import me.xmpl.topka.api.TopkaFirstLootTakeEvent;
import me.xmpl.topka.model.PersistedTopkaState;
import me.xmpl.topka.model.TopkaStage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class TopkaLifecycleService {
    private final TopkaExtension extension;
    private final PersistedTopkaState state;

    public TopkaLifecycleService(TopkaExtension extension) {
        this.extension = extension;
        int defaultCooldown = extension.getConfig().getInt("settings.cooldown-seconds", 3600);
        this.state = extension.getStateStorage().loadState(defaultCooldown);
    }

    public void startMasterClock() {
        extension.runTaskTimer(this::tickSecond, 20L, 20L);
    }

    private void tickSecond() {
        FileConfiguration cfg = extension.getConfig();
        int minOnline = cfg.getInt("settings.min-online-players", 2);
        boolean enoughPlayers = Bukkit.getOnlinePlayers().size() >= minOnline;

        // Обновляем голограмму голосования над изумрудным блоком
        extension.getVotingHologramService().updateVotingHologram();

        switch (state.stage) {
            case TIMER: {
                if (!enoughPlayers) {
                    // Онлайн меньше нужного — таймер стоит на паузе и НЕ сбрасывается!
                    extension.getChestHologramService().updateHolograms(state, true, minOnline);
                    return;
                }

                state.remainingSeconds--;
                extension.getChestHologramService().updateHolograms(state, false, minOnline);

                if (state.remainingSeconds <= 0) {
                    transitionToFillingStage();
                }
                break;
            }
            case FILLING: {
                state.remainingSeconds--;
                extension.getChestHologramService().updateHolograms(state, false, minOnline);

                if (state.remainingSeconds <= 0) {
                    transitionToSpawnedStage();
                }
                break;
            }
            case SPAWNED: {
                state.remainingSeconds--;
                extension.getChestHologramService().updateHolograms(state, false, minOnline);

                if (state.remainingSeconds <= 0) {
                    resetToTimerStage();
                }
                break;
            }
        }

        if (state.remainingSeconds % 5 == 0) {
            extension.getStateStorage().saveStateAsync(state);
        }
    }

    /**
     * Переход к стадии FILLING ("Наполняем сундук..."):
     * - Случайно выбираем время наполнения от 15 до 20 секунд.
     * - Если установлено 2 сундука, случайно выбираем, в каком из двух сундуков появятся все ресурсы.
     * - Игрокам с правом платной подписки отправляем личное сообщение о том, какой сундук наполняется.
     */
    public void transitionToFillingStage() {
        FileConfiguration cfg = extension.getConfig();
        int minFill = cfg.getInt("settings.min-filling-seconds", 15);
        int maxFill = cfg.getInt("settings.max-filling-seconds", 20);

        state.stage = TopkaStage.FILLING;
        state.remainingSeconds = RandomUtil.nextInt(minFill, maxFill);
        state.firstItemTaken = false;

        boolean twoChests = extension.getArenaStorage().hasTwoChests();
        state.chosenChestNumber = twoChests ? (ThreadLocalRandom.current().nextBoolean() ? 1 : 2) : 1;

        clearBothChestsInventory();
        extension.getAntiCheatService().clearCooldowns();
        extension.getStateStorage().saveStateAsync(state);

        notifySubscribersAboutTargetChest(twoChests, state.chosenChestNumber);
    }

    private void notifySubscribersAboutTargetChest(boolean twoChests, int chosenNumber) {
        FileConfiguration cfg = extension.getConfig();
        String subPerm = cfg.getString("permissions.subscriber-notify", "topka.subscriber.notify");

        Location targetLoc = (chosenNumber == 2 && extension.getArenaStorage().getChest2Location() != null)
                ? extension.getArenaStorage().getChest2Location()
                : extension.getArenaStorage().getChest1Location();

        if (targetLoc == null) return;

        String msgTemplate = twoChests
                ? cfg.getString("subscriber-messages.two-chests-filling")
                : cfg.getString("subscriber-messages.single-chest-filling");

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.isOp() && p.hasPermission(subPerm)) {
                ChatUtil.sendMessage(p, msgTemplate,
                        "{number}", chosenNumber,
                        "{x}", targetLoc.getBlockX(),
                        "{y}", targetLoc.getBlockY(),
                        "{z}", targetLoc.getBlockZ());
            }
        }
    }

    /**
     * Переход к стадии SPAWNED ("Сундук заспавнился!"):
     * Помещает все предметы из пула (с лором §fВещь §e§lTOPKA) в выбранный сундук и запускает таймер перезарядки (900 сек).
     */
    public void transitionToSpawnedStage() {
        state.stage = TopkaStage.SPAWNED;
        state.remainingSeconds = extension.getConfig().getInt("settings.restock-timer-seconds", 900);
        state.firstItemTaken = false;

        populateTargetChest(state.chosenChestNumber);
        extension.getStateStorage().saveStateAsync(state);
    }

    public void resetToTimerStage() {
        clearBothChestsInventory();
        extension.getAntiCheatService().clearCooldowns();

        state.stage = TopkaStage.TIMER;
        state.remainingSeconds = extension.getConfig().getInt("settings.cooldown-seconds", 3600);
        state.firstItemTaken = false;
        extension.getStateStorage().saveStateAsync(state);
    }

    private void populateTargetChest(int chestNumber) {
        clearBothChestsInventory();

        Location targetLoc = (chestNumber == 2 && extension.getArenaStorage().getChest2Location() != null)
                ? extension.getArenaStorage().getChest2Location()
                : extension.getArenaStorage().getChest1Location();

        if (targetLoc == null || targetLoc.getWorld() == null) return;
        Block block = targetLoc.getBlock();
        if (!(block.getState() instanceof Chest)) return;

        Inventory inv = ((Chest) block.getState()).getBlockInventory();
        inv.clear();

        List<ItemStack> pool = extension.getLootPoolStorage().getPoolItems();
        if (pool.isEmpty()) return;

        boolean randomize = extension.getCustomConfig("anticheat-loot.yml").get()
                .getBoolean("loot-generation.randomize-slots", true);

        List<Integer> slots = new ArrayList<>(inv.getSize());
        for (int i = 0; i < inv.getSize(); i++) slots.add(i);
        if (randomize) {
            Collections.shuffle(slots);
        }

        int limit = Math.min(inv.getSize(), pool.size());
        for (int i = 0; i < limit; i++) {
            ItemStack item = extension.getLootPoolStorage().applyTopkaLore(pool.get(i).clone());
            inv.setItem(slots.get(i), item);
        }
    }

    public void clearBothChestsInventory() {
        clearChestAt(extension.getArenaStorage().getChest1Location());
        clearChestAt(extension.getArenaStorage().getChest2Location());
    }

    private void clearChestAt(Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        Block b = loc.getBlock();
        if (b.getState() instanceof Chest) {
            ((Chest) b.getState()).getBlockInventory().clear();
        }
    }

    /**
     * Если игрок первым забирает предмет из сундука TOPKA в этом раунде — вызывает ивент для начисления клановых очков.
     */
    public void handleFirstLootTakeIfNotYet(Player player, Location chestLoc) {
        if (!state.firstItemTaken) {
            state.firstItemTaken = true;
            extension.getStateStorage().saveStateAsync(state);

            int clanPoints = extension.getConfig().getInt("clan-integration.points-for-first-item", 5);
            Bukkit.getPluginManager().callEvent(new TopkaFirstLootTakeEvent(player, chestLoc, clanPoints));
        }
    }

    public PersistedTopkaState getState() {
        return state;
    }
}