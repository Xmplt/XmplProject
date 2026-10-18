package me.xmpl.nexuschest.listener;

import com.sk89q.worldguard.protection.events.DisallowedPVPEvent;
import me.xmpl.lib.util.ChatUtil;
import me.xmpl.nexuschest.NexusChestExtension;
import me.xmpl.nexuschest.storage.LootPoolStorage.LootEditorHolder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class NexusArenaAndChestListener implements Listener {
    private final NexusChestExtension extension;

    public NexusArenaAndChestListener(NexusChestExtension extension) {
        this.extension = extension;
    }

    @EventHandler
    public void onLootEditorClose(InventoryCloseEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top != null && top.getHolder() instanceof LootEditorHolder) {
            int count = extension.getLootPoolStorage().saveFromEditorInventory(top);
            String msg = extension.getCustomConfig("commands/nchest.yml").get().getString("messages.loot-saved");
            ChatUtil.sendMessage(event.getPlayer(), msg, "{count}", count);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChestClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Inventory top = event.getView().getTopInventory();
        if (top == null) return;

        Location chestLoc = extractChestLocation(top.getHolder());
        if (chestLoc == null) return;

        int chestNum = extension.getArenaStorage().getChestNumberAt(chestLoc);
        if (chestNum == 0) return;

        Player player = (Player) event.getWhoClicked();
        FileConfiguration msgCfg = extension.getCustomConfig("messages.yml").get();
        int rawSlot = event.getRawSlot();

        if (rawSlot >= top.getSize()) {
            if (event.isShiftClick() || event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
                event.setCancelled(true);
                ChatUtil.sendMessage(player, msgCfg.getString("loot-restriction.cannot-put-items"));
            }
            return;
        }

        ItemStack cursor = event.getCursor();
        if (cursor != null && cursor.getType() != Material.AIR) {
            event.setCancelled(true);
            ChatUtil.sendMessage(player, msgCfg.getString("loot-restriction.cannot-put-items"));
            return;
        }

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }

        // Передаем rawSlot, чтобы красная шерсть появлялась ТОЛЬКО на нажатом предмете!
        if (!extension.getLootService().checkAndAllowTakeItem(player, top, rawSlot)) {
            event.setCancelled(true);
            return;
        }

        extension.getLootService().markFirstItemTakenIfNotYet();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChestDrag(InventoryDragEvent event) {
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreakChest(BlockBreakEvent event) {
        if (extension.getArenaStorage().getChestNumberAt(event.getBlock().getLocation()) != 0) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        extension.getEventManager().checkArenaSecurityForPlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onWgDisallowedPvp(DisallowedPVPEvent event) {
        if (!extension.getConfig().getBoolean("security.force-enable-pvp-in-region", true)) return;
        Player defender = event.getDefender();
        if (defender != null && extension.getArenaStorage().isInArenaRegion(defender.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onArenaPvpDamage(EntityDamageByEntityEvent event) {
        if (!extension.getConfig().getBoolean("security.force-enable-pvp-in-region", true)) return;
        if (event.getEntity() instanceof Player && extension.getArenaStorage().isInArenaRegion(event.getEntity().getLocation())) {
            event.setCancelled(false);
        }
    }

    @EventHandler
    public void onPlayerDeathInArena(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        if (!extension.getEventManager().isEventRunning()) return;
        if (!extension.getArenaStorage().isInArenaRegion(victim.getLocation())) return;

        String code = extension.getEventManager().getCurrentPinCode();
        FileConfiguration msgCfg = extension.getCustomConfig("messages.yml").get();
        Player killer = victim.getKiller();

        if (killer != null && !killer.equals(victim)) {
            ChatUtil.sendMessage(victim, msgCfg.getString("combat-and-death.death-by-player"),
                    "{killer}", killer.getName(), "{code}", code);
        } else {
            ChatUtil.sendMessage(victim, msgCfg.getString("combat-and-death.death-generic"),
                    "{code}", code);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String msg = event.getMessage().trim();
        String[] parts = msg.split(" ");
        String cmd = parts[0].toLowerCase();
        if (cmd.contains(":")) {
            cmd = "/" + cmd.substring(cmd.indexOf(':') + 1);
        }

        FileConfiguration cfg = extension.getConfig();
        FileConfiguration msgCfg = extension.getCustomConfig("messages.yml").get();

        if (extension.getEventManager().isPlayerActivelyOnEvent(player)) {
            String adminPerm = cfg.getString("permissions.admin", "nexuschest.admin");
            if (!player.hasPermission(adminPerm)) {
                List<String> allowed = cfg.getStringList("security.allowed-commands-for-participants");
                boolean ok = false;
                for (String a : allowed) {
                    if (cmd.equalsIgnoreCase(a.trim())) {
                        ok = true;
                        break;
                    }
                }
                if (!ok) {
                    event.setCancelled(true);
                    ChatUtil.sendMessage(player, msgCfg.getString("commands-restriction.command-blocked-self"));
                    return;
                }
            }
        }

        List<String> blockedTpCmds = cfg.getStringList("security.blocked-tp-commands-to-participant");
        for (String bCmd : blockedTpCmds) {
            if (cmd.equalsIgnoreCase(bCmd.trim())) {
                if (parts.length >= 2) {
                    Player target = Bukkit.getPlayerExact(parts[1]);
                    if (target != null && extension.getEventManager().isPlayerActivelyOnEvent(target)) {
                        event.setCancelled(true);
                        ChatUtil.sendMessage(player, msgCfg.getString("combat-and-death.tp-target-on-nexus",
                                        msgCfg.getString("commands-restriction.tp-target-on-nexus")),
                                "{target}", target.getName());
                        return;
                    }
                }
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