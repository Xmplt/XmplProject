package me.xmpl.killsystem.listener;

import me.xmpl.killsystem.KillSystemExtension;
import me.xmpl.killsystem.model.TopSignEntry;
import me.xmpl.killsystem.service.TopHopperGuiService.TopGuiHolder;
import me.xmpl.lib.util.ChatUtil;
import me.xmpl.lib.util.SignBlockUtil;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;

public class KillAndSignListener implements Listener {
    private final KillSystemExtension extension;

    public KillAndSignListener(KillSystemExtension extension) {
        this.extension = extension;
    }

    // 1. Запись убийств и смертей в базу данных
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        boolean countAllDeaths = extension.getConfig().getBoolean("settings.count-all-deaths", true);
        if (countAllDeaths || (killer != null && !killer.equals(victim))) {
            extension.getDatabaseStorage().addDeath(victim.getName());
        }

        if (killer != null && !killer.equals(victim)) {
            if (extension.getConfig().getBoolean("settings.prevent-same-ip-boost", true)) {
                if (killer.getAddress() != null && victim.getAddress() != null
                        && killer.getAddress().getAddress().equals(victim.getAddress().getAddress())) {
                    return;
                }
            }
            extension.getDatabaseStorage().addKill(killer.getName());
        }
    }

    // 2. Правый клик по табличке топа -> Открываем меню Воронки с головами топ-игроков!
    @EventHandler(priority = EventPriority.HIGH)
    public void onSignRightClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block clicked = event.getClickedBlock();
        if (!SignBlockUtil.isSign(clicked)) return;

        TopSignEntry signEntry = extension.getSignStorage().getSignAt(clicked.getLocation());
        if (signEntry == null) return;

        event.setCancelled(true);
        extension.getHopperGuiService().openTopGui(event.getPlayer());
    }

    // 3. Защита табличек топа от случайного разрушения игроками
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSignBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!SignBlockUtil.isSign(block)) return;

        TopSignEntry entry = extension.getSignStorage().getSignAt(block.getLocation());
        if (entry == null) return;

        Player player = event.getPlayer();
        String adminPerm = extension.getConfig().getString("permissions.admin", "killsystem.admin");
        if (!player.hasPermission(adminPerm) || !player.isSneaking()) {
            event.setCancelled(true);
            ChatUtil.sendMessage(player, "&c[KillSystem] Это табличка Топ-#" + entry.getRank() + "! Чтобы удалить её, используйте /killsystem removetab.");
            return;
        }

        extension.getSignStorage().removeSignAt(block.getLocation());
        ChatUtil.sendMessage(player, "&e[KillSystem] Табличка Топ-#" + entry.getRank() + " удалена.");
    }

    // 4. Запрет забирать головы из меню Воронки
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onGuiClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top != null && top.getHolder() instanceof TopGuiHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onGuiDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top != null && top.getHolder() instanceof TopGuiHolder) {
            event.setCancelled(true);
        }
    }
}