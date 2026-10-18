package me.xmpl.nexuschest.service;

import me.xmpl.lib.util.ItemBuilder;
import me.xmpl.lib.util.PacketSlotUtil;
import me.xmpl.nexuschest.NexusChestExtension;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class ChestLootAndAntiCheatService {
    private final NexusChestExtension extension;
    private final Map<UUID, Long> lastItemTakeTimestamp = new ConcurrentHashMap<>();
    private final Map<UUID, Long> penaltyActiveUntil = new ConcurrentHashMap<>();
    private boolean firstItemTakenInRound = false;

    public ChestLootAndAntiCheatService(NexusChestExtension extension) {
        this.extension = extension;
    }

    public void resetRoundAndPopulateChests() {
        firstItemTakenInRound = false;
        lastItemTakeTimestamp.clear();
        penaltyActiveUntil.clear();

        Inventory inv1 = getChestInventoryAt(extension.getArenaStorage().getChest1Location());
        Inventory inv2 = getChestInventoryAt(extension.getArenaStorage().getChest2Location());

        if (inv1 != null) inv1.clear();
        if (inv2 != null) inv2.clear();

        List<ItemStack> pool = extension.getLootPoolStorage().getPoolItems();
        if (pool.isEmpty()) return;

        List<ItemStack> allPoolItems = new ArrayList<>(pool.size());
        for (ItemStack item : pool) {
            if (item != null && item.getType() != Material.AIR) {
                allPoolItems.add(item.clone());
            }
        }
        if (allPoolItems.isEmpty()) return;

        Collections.shuffle(allPoolItems);

        FileConfiguration acCfg = extension.getCustomConfig("anticheat-loot.yml").get();
        boolean randomizeSlots = acCfg.getBoolean("loot-generation.randomize-slots", true);

        if (inv1 != null && inv2 != null) {
            int total = allPoolItems.size();
            int half = total / 2;
            int countForChest1 = (total % 2 == 0)
                    ? half
                    : (ThreadLocalRandom.current().nextBoolean() ? half + 1 : half);

            List<ItemStack> chest1Items = allPoolItems.subList(0, countForChest1);
            List<ItemStack> chest2Items = allPoolItems.subList(countForChest1, total);

            placeItemsIntoInventory(inv1, chest1Items, randomizeSlots);
            placeItemsIntoInventory(inv2, chest2Items, randomizeSlots);
        } else if (inv1 != null) {
            placeItemsIntoInventory(inv1, allPoolItems, randomizeSlots);
        } else if (inv2 != null) {
            placeItemsIntoInventory(inv2, allPoolItems, randomizeSlots);
        }
    }

    private Inventory getChestInventoryAt(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        Block block = loc.getBlock();
        if (block.getState() instanceof Chest) {
            return ((Chest) block.getState()).getBlockInventory();
        }
        return null;
    }

    private void placeItemsIntoInventory(Inventory inv, List<ItemStack> itemsToPlace, boolean randomizeSlots) {
        if (inv == null || itemsToPlace == null || itemsToPlace.isEmpty()) return;

        int invSize = inv.getSize();
        List<Integer> slots = new ArrayList<>(invSize);
        for (int i = 0; i < invSize; i++) {
            slots.add(i);
        }
        if (randomizeSlots) {
            Collections.shuffle(slots);
        }

        int limit = Math.min(invSize, itemsToPlace.size());
        for (int i = 0; i < limit; i++) {
            inv.setItem(slots.get(i), itemsToPlace.get(i));
        }

        for (int i = limit; i < itemsToPlace.size(); i++) {
            inv.addItem(itemsToPlace.get(i));
        }
    }

    public void clearBothChests() {
        clearChestAt(extension.getArenaStorage().getChest1Location());
        clearChestAt(extension.getArenaStorage().getChest2Location());
    }

    private void clearChestAt(Location loc) {
        Inventory inv = getChestInventoryAt(loc);
        if (inv != null) {
            inv.clear();
        }
    }

    /**
     * Проверяет, может ли игрок взять предмет из слота clickedRawSlot.
     * Если у игрока кулдаун — ТОЛЬКО нажатый предмет (clickedRawSlot) превращается в красную шерсть!
     */
    public boolean checkAndAllowTakeItem(Player player, Inventory topChestInventory, int clickedRawSlot) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        FileConfiguration acCfg = extension.getCustomConfig("anticheat-loot.yml").get();
        long minIntervalMs = acCfg.getLong("smart-cooldown.min-interval-between-takes-ms", 650L);
        long penaltyTicks = acCfg.getLong("smart-cooldown.red-wool-penalty-ticks", 20L);

        // 1. Если игрок уже на кулдауне — заменяем ТОЛЬКО нажатый слот на красную шерсть
        Long penUntil = penaltyActiveUntil.get(uuid);
        if (penUntil != null && now < penUntil) {
            long remTicks = Math.max(2L, (penUntil - now) / 50L);
            ItemStack redWool = buildRedWoolPenaltyItem(acCfg);
            PacketSlotUtil.sendTemporaryFakeSlot(player, clickedRawSlot, redWool, remTicks);
            return false;
        }

        // 2. Если игрок кликнул слишком быстро — включаем кулдаун и заменяем ТОЛЬКО нажатый слот на красную шерсть
        Long lastTake = lastItemTakeTimestamp.get(uuid);
        if (lastTake != null && (now - lastTake) < minIntervalMs) {
            penaltyActiveUntil.put(uuid, now + (penaltyTicks * 50L));
            ItemStack redWool = buildRedWoolPenaltyItem(acCfg);
            PacketSlotUtil.sendTemporaryFakeSlot(player, clickedRawSlot, redWool, penaltyTicks);
            return false;
        }

        lastItemTakeTimestamp.put(uuid, now);
        return true;
    }

    public boolean checkAndAllowTakeItem(Player player, Inventory topChestInventory) {
        return checkAndAllowTakeItem(player, topChestInventory, 0);
    }

    private ItemStack buildRedWoolPenaltyItem(FileConfiguration acCfg) {
        String name = acCfg.getString("smart-cooldown.penalty-item.name", "&cПодождите немного...");
        List<String> lore = acCfg.getStringList("smart-cooldown.penalty-item.lore");
        return new ItemBuilder(Material.WOOL, 1, (short) 14)
                .setName(name)
                .setLore(lore)
                .build();
    }

    public boolean markFirstItemTakenIfNotYet() {
        if (!firstItemTakenInRound) {
            firstItemTakenInRound = true;
            return true;
        }
        return false;
    }
}