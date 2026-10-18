package me.xmpl.nexusclans.listener;

import me.xmpl.lib.util.SignBlockUtil;
import me.xmpl.nexusclans.NexusClansExtension;
import me.xmpl.nexusclans.service.ClanTopSignAndBoardService.TopClansHopperHolder;
import me.xmpl.nexusclans.service.ClanVirtualChestService.ClanChestHolder;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;

public class ClanChestAndSignListener implements Listener {
    private final NexusClansExtension extension;

    public ClanChestAndSignListener(NexusClansExtension extension) {
        this.extension = extension;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Inventory top = event.getView().getTopInventory();
        if (top == null) return;

        // 1. Защита воронки Топ-3 кланов
        if (top.getHolder() instanceof TopClansHopperHolder) {
            event.setCancelled(true);
            return;
        }

        // 2. Живая синхронизация и подпись предметов в виртуальном сундуке /c chest
        if (top.getHolder() instanceof ClanChestHolder) {
            ClanChestHolder holder = (ClanChestHolder) top.getHolder();
            Player actor = (Player) event.getWhoClicked();
            extension.runTask(() -> extension.getVirtualChestService().syncAndSaveChestAfterClick(top, holder.clanKey, actor));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Inventory top = event.getView().getTopInventory();
        if (top == null) return;

        if (top.getHolder() instanceof TopClansHopperHolder) {
            event.setCancelled(true);
            return;
        }

        if (top.getHolder() instanceof ClanChestHolder) {
            ClanChestHolder holder = (ClanChestHolder) top.getHolder();
            Player actor = (Player) event.getWhoClicked();
            extension.runTask(() -> extension.getVirtualChestService().syncAndSaveChestAfterClick(top, holder.clanKey, actor));
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Inventory top = event.getView().getTopInventory();
        if (top != null && top.getHolder() instanceof ClanChestHolder) {
            ClanChestHolder holder = (ClanChestHolder) top.getHolder();
            extension.getVirtualChestService().syncAndSaveChestAfterClick(top, holder.clanKey, (Player) event.getPlayer());
        }
    }

    // 3. Клик ПКМ по табличке Топ-1/2/3 клана -> открывает меню Воронки с 3 головами владельцев кланов!
    @EventHandler(priority = EventPriority.HIGH)
    public void onRightClickTopSign(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block b = event.getClickedBlock();
        if (!SignBlockUtil.isSign(b)) return;

        if (extension.getTopSignAndBoardService().isTopSign(b.getLocation())) {
            event.setCancelled(true);
            extension.getTopSignAndBoardService().openTopHopperGui(event.getPlayer());
        }
    }
}