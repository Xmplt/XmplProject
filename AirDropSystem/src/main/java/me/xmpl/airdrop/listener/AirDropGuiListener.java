package me.xmpl.airdrop.listener;

import me.xmpl.airdrop.AirDropExtension;
import me.xmpl.airdrop.model.AirDropLootItem;
import me.xmpl.airdrop.service.AirDropLootEditorGuiService.ChanceEditorHolder;
import me.xmpl.airdrop.service.AirDropLootEditorGuiService.StoragePageHolder;
import me.xmpl.lib.util.ChatUtil;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class AirDropGuiListener implements Listener {
    private final AirDropExtension extension;

    public AirDropGuiListener(AirDropExtension extension) {
        this.extension = extension;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onGuiClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player admin = (Player) event.getWhoClicked();
        Inventory top = event.getView().getTopInventory();
        if (top == null) return;

        FileConfiguration guiCfg = extension.getCustomConfig("gui-editor.yml").get();

        // 1. Клики в многостраничном двойном сундуке лута
        if (top.getHolder() instanceof StoragePageHolder) {
            StoragePageHolder holder = (StoragePageHolder) top.getHolder();
            int rawSlot = event.getRawSlot();

            // Нижняя строка навигации (слоты 45..53)
            if (rawSlot >= 45 && rawSlot < 54) {
                event.setCancelled(true);
                if (rawSlot == guiCfg.getInt("storage-gui.prev-page-slot", 45) && holder.page > 1) {
                    extension.getGuiService().saveStoragePageContents(top, holder.page);
                    extension.getGuiService().openStoragePage(admin, holder.page - 1);
                } else if (rawSlot == guiCfg.getInt("storage-gui.next-page-slot", 53)) {
                    extension.getGuiService().saveStoragePageContents(top, holder.page);
                    extension.getGuiService().openStoragePage(admin, holder.page + 1);
                }
                return;
            }

            // Клик ПКМ по предмету в слотах 0..44 -> открываем меню настройки шанса (-25/-10/-1/+1/+10/+25)!
            if (rawSlot >= 0 && rawSlot < 45 && event.getClick() == ClickType.RIGHT) {
                ItemStack clicked = event.getCurrentItem();
                if (clicked != null && clicked.getType() != Material.AIR) {
                    event.setCancelled(true);
                    extension.getGuiService().saveStoragePageContents(top, holder.page);
                    int globalIdx = (holder.page - 1) * 45 + rawSlot;
                    extension.getGuiService().openChanceEditor(admin, holder.page, globalIdx);
                }
            }
            return;
        }

        // 2. Клики в меню настройки шансов предмета (-25, -10, -1, +1, +10, +25)
        if (top.getHolder() instanceof ChanceEditorHolder) {
            event.setCancelled(true);
            if (event.getClickedInventory() == null || !event.getClickedInventory().equals(top)) return;

            ChanceEditorHolder holder = (ChanceEditorHolder) top.getHolder();
            List<AirDropLootItem> allItems = extension.getLootStorage().getItems();
            if (holder.itemGlobalIndex < 0 || holder.itemGlobalIndex >= allItems.size()) {
                admin.closeInventory();
                return;
            }

            AirDropLootItem target = allItems.get(holder.itemGlobalIndex);
            int slot = event.getSlot();

            if (slot == guiCfg.getInt("chance-gui.buttons.back-save.slot", 3)) {
                extension.getLootStorage().saveAllToDisk();
                extension.getGuiService().openStoragePage(admin, holder.returnPage);
                return;
            }

            double delta = 0.0;
            if (slot == guiCfg.getInt("chance-gui.buttons.minus-25.slot", 0)) delta = -25.0;
            else if (slot == guiCfg.getInt("chance-gui.buttons.minus-10.slot", 1)) delta = -10.0;
            else if (slot == guiCfg.getInt("chance-gui.buttons.minus-1.slot", 2)) delta = -1.0;
            else if (slot == guiCfg.getInt("chance-gui.buttons.plus-1.slot", 5)) delta = 1.0;
            else if (slot == guiCfg.getInt("chance-gui.buttons.plus-10.slot", 6)) delta = 10.0;
            else if (slot == guiCfg.getInt("chance-gui.buttons.plus-25.slot", 7)) delta = 25.0;

            if (delta != 0.0) {
                target.setChance(target.getChance() + delta);
                extension.getLootStorage().saveAllToDisk();
                extension.getGuiService().openChanceEditor(admin, holder.returnPage, holder.itemGlobalIndex);
            }
        }
    }

    @EventHandler
    public void onGuiClose(InventoryCloseEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top != null && top.getHolder() instanceof StoragePageHolder) {
            StoragePageHolder holder = (StoragePageHolder) top.getHolder();
            extension.getGuiService().saveStoragePageContents(top, holder.page);
            String msg = extension.getCustomConfig("commands/airdrop.yml").get().getString("messages.loot-saved");
            ChatUtil.sendMessage(event.getPlayer(), msg);
        }
    }
}