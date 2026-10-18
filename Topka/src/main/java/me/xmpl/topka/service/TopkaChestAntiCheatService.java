package me.xmpl.topka.service;

import me.xmpl.lib.util.ItemBuilder;
import me.xmpl.lib.util.PacketSlotUtil;
import me.xmpl.topka.TopkaExtension;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TopkaChestAntiCheatService {
    private final TopkaExtension extension;
    private final Map<UUID, Long> lastTakeTimestamp = new ConcurrentHashMap<>();
    private final Map<UUID, Long> penaltyActiveUntil = new ConcurrentHashMap<>();

    public TopkaChestAntiCheatService(TopkaExtension extension) {
        this.extension = extension;
    }

    public void clearCooldowns() {
        lastTakeTimestamp.clear();
        penaltyActiveUntil.clear();
    }

    /**
     * Если у игрока кулдаун (1.5 секунды = 30 тиков) — красная шерсть появляется ТОЛЬКО на нажатом предмете (clickedRawSlot)!
     */
    public boolean checkAndAllowTakeItem(Player player, Inventory topChestInventory, int clickedRawSlot) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        FileConfiguration acCfg = extension.getCustomConfig("anticheat-loot.yml").get();
        long minIntervalMs = acCfg.getLong("smart-cooldown.min-interval-between-takes-ms", 650L);
        long penaltyTicks = acCfg.getLong("smart-cooldown.red-wool-penalty-ticks", 30L);

        Long penUntil = penaltyActiveUntil.get(uuid);
        if (penUntil != null && now < penUntil) {
            long remTicks = Math.max(2L, (penUntil - now) / 50L);
            ItemStack redWool = buildRedWoolPenaltyItem(acCfg);
            PacketSlotUtil.sendTemporaryFakeSlot(player, clickedRawSlot, redWool, remTicks);
            return false;
        }

        Long lastTake = lastTakeTimestamp.get(uuid);
        if (lastTake != null && (now - lastTake) < minIntervalMs) {
            penaltyActiveUntil.put(uuid, now + (penaltyTicks * 50L));
            ItemStack redWool = buildRedWoolPenaltyItem(acCfg);
            PacketSlotUtil.sendTemporaryFakeSlot(player, clickedRawSlot, redWool, penaltyTicks);
            return false;
        }

        lastTakeTimestamp.put(uuid, now);
        return true;
    }

    private ItemStack buildRedWoolPenaltyItem(FileConfiguration acCfg) {
        String name = acCfg.getString("smart-cooldown.penalty-item.name", "&cПодождите немного...");
        List<String> lore = acCfg.getStringList("smart-cooldown.penalty-item.lore");
        return new ItemBuilder(Material.WOOL, 1, (short) 14)
                .setName(name)
                .setLore(lore)
                .build();
    }
}