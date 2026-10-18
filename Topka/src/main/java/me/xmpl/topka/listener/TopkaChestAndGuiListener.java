package me.xmpl.topka.listener;

import me.xmpl.lib.util.ChatUtil;
import me.xmpl.topka.TopkaExtension;
import me.xmpl.topka.storage.TopkaLootPoolStorage.TopkaLootEditorHolder;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public class TopkaChestAndGuiListener implements Listener {
    private final TopkaExtension extension;

    public TopkaChestAndGuiListener(TopkaExtension extension) {
        this.extension = extension;
    }

    @EventHandler
    public void onLootEditorClose(InventoryCloseEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top != null && top.getHolder() instanceof TopkaLootEditorHolder) {
            int savedCount = extension.getLootPoolStorage().saveFromEditorInventory(top);
            String msg = extension.getCustomConfig("messages.yml").get().getString(
                    "messages.loot-saved",
                    "&a[Topka] Пул предметов сохранен ({count} стаков)! Всем предметам выдан лор TOPKA."
            );
            ChatUtil.sendMessage(event.getPlayer(), msg, "{count}", savedCount);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreakChest(BlockBreakEvent event) {
        Location loc = event.getBlock().getLocation();
        if (extension.getArenaStorage().getChestNumberAt(loc) != 0) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(b -> extension.getArenaStorage().getChestNumberAt(b.getLocation()) != 0);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTopkaChestClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Inventory top = event.getView().getTopInventory();
        if (top == null) return;

        Location chestLoc = extractChestLocation(top.getHolder());
        if (chestLoc == null) return;

        int chestNumber = extension.getArenaStorage().getChestNumberAt(chestLoc);
        if (chestNumber == 0) return;

        Player player = (Player) event.getWhoClicked();
        int rawSlot = event.getRawSlot();

        if (rawSlot >= top.getSize()) {
            if (event.isShiftClick() || event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
                event.setCancelled(true);
            }
            return;
        }

        ItemStack cursor = event.getCursor();
        if (cursor != null && cursor.getType() != Material.AIR) {
            event.setCancelled(true);
            return;
        }

        ItemStack current = event.getCurrentItem();
        if (current == null || current.getType() == Material.AIR) {
            return;
        }

        // Передаем rawSlot, чтобы красная шерсть появлялась ТОЛЬКО на нажатом предмете!
        if (!extension.getAntiCheatService().checkAndAllowTakeItem(player, top, rawSlot)) {
            event.setCancelled(true);
            return;
        }

        extension.getLifecycleService().handleFirstLootTakeIfNotYet(player, chestLoc);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTopkaChestDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top == null) return;
        Location chestLoc = extractChestLocation(top.getHolder());
        if (chestLoc == null || extension.getArenaStorage().getChestNumberAt(chestLoc) == 0) return;

        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < top.getSize()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private Location extractChestLocation(InventoryHolder holder) {
        if (holder instanceof Chest) {
            Block b = ((Chest) holder).getBlock();
            return b != null ? b.getLocation() : null;
        }
        return null;
    }
}