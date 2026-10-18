package me.xmpl.ess.listener;

import me.xmpl.ess.XmplEssExtension;
import me.xmpl.ess.service.InvSeeService;
import me.xmpl.ess.service.InvSeeService.InvSeeHolder;
import me.xmpl.ess.service.RtpService.RtpGuiHolder;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

public class GuiListener implements Listener {
    private final XmplEssExtension extension;

    public GuiListener(XmplEssExtension extension) {
        this.extension = extension;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onGuiClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        Inventory top = event.getView().getTopInventory();
        if (top == null) return;

        // 1. Обработка меню /rtp
        if (top.getHolder() instanceof RtpGuiHolder) {
            event.setCancelled(true);
            if (event.getClickedInventory() == null || !event.getClickedInventory().equals(top)) return;

            FileConfiguration rtpCfg = extension.getCustomConfig("commands/rtp.yml").get();
            int slot = event.getSlot();
            if (slot == rtpCfg.getInt("gui.normal-button.slot", 11)) {
                player.closeInventory();
                extension.getRtpService().performNormalRtp(player);
            } else if (slot == rtpCfg.getInt("gui.player-button.slot", 15)) {
                player.closeInventory();
                extension.getRtpService().performPlayerRtp(player);
            }
            return;
        }

        // 2. Обработка меню /invsee (защита тех. слотов от дюпов + проверка права на редактирование)
        if (top.getHolder() instanceof InvSeeHolder) {
            InvSeeHolder holder = (InvSeeHolder) top.getHolder();
            InvSeeService service = extension.getInvSeeService();
            String editPerm = extension.getCustomConfig("commands/invsee.yml").get()
                    .getString("permissions.edit", "xmpless.invsee.edit");

            // Если нет права на редактирование — блокируем любые клики!
            if (!player.hasPermission(editPerm)) {
                event.setCancelled(true);
                return;
            }

            // Если клик внутри верхнего инвентаря по техническим слотам (здоровье, пвп, зелья, стекло) — отменяем!
            if (event.getRawSlot() < top.getSize() && !service.isEditableItemSlot(event.getRawSlot())) {
                event.setCancelled(true);
                return;
            }

            // Запрещаем Shift-клик из нижнего инвентаря, если он может попасть в технические слоты 36..44 или 50..53
            if (event.isShiftClick() && event.getRawSlot() >= top.getSize()) {
                event.setCancelled(true);
                return;
            }

            // Синхронизируем изменения на следующем тике
            extension.runTask(() -> service.applyChangesFromGuiToTarget(top, holder));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onGuiDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top == null) return;

        if (top.getHolder() instanceof RtpGuiHolder) {
            event.setCancelled(true);
            return;
        }

        if (top.getHolder() instanceof InvSeeHolder) {
            Player player = (Player) event.getWhoClicked();
            String editPerm = extension.getCustomConfig("commands/invsee.yml").get()
                    .getString("permissions.edit", "xmpless.invsee.edit");
            if (!player.hasPermission(editPerm)) {
                event.setCancelled(true);
                return;
            }

            for (int rawSlot : event.getRawSlots()) {
                if (rawSlot < top.getSize() && !extension.getInvSeeService().isEditableItemSlot(rawSlot)) {
                    event.setCancelled(true);
                    return;
                }
            }

            InvSeeHolder holder = (InvSeeHolder) top.getHolder();
            extension.runTask(() -> extension.getInvSeeService().applyChangesFromGuiToTarget(top, holder));
        }
    }

    @EventHandler
    public void onGuiClose(InventoryCloseEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top != null && top.getHolder() instanceof InvSeeHolder) {
            Player player = (Player) event.getPlayer();
            String editPerm = extension.getCustomConfig("commands/invsee.yml").get()
                    .getString("permissions.edit", "xmpless.invsee.edit");
            if (player.hasPermission(editPerm)) {
                extension.getInvSeeService().applyChangesFromGuiToTarget(top, (InvSeeHolder) top.getHolder());
            }
        }
    }
}