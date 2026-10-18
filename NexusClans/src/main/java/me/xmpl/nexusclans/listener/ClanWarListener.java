package me.xmpl.nexusclans.listener;

import me.xmpl.lib.util.ChatUtil;
import me.xmpl.lib.util.CuboidBox;
import me.xmpl.nexusclans.NexusClansExtension;
import me.xmpl.nexusclans.model.ClanWarSession;
import me.xmpl.nexusclans.model.ClanWarSession.WarStage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

import java.util.List;

public class ClanWarListener implements Listener {
    private final NexusClansExtension extension;

    public ClanWarListener(NexusClansExtension extension) {
        this.extension = extension;
    }

    private boolean isInventoryLockedFor(Player player) {
        ClanWarSession session = extension.getWarService().getActiveSession();
        return session != null
                && session.getStage() == WarStage.INVENTORY_LOCK_3MIN
                && session.isParticipant(player.getName());
    }

    // 1. Полная блокировка инвентаря на 3-минутном этапе перед телепортацией на арену
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInvClickLock(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player && isInventoryLockedFor((Player) event.getWhoClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInvDragLock(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player && isInventoryLockedFor((Player) event.getWhoClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInvOpenLock(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player && isInventoryLockedFor((Player) event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDropLock(PlayerDropItemEvent event) {
        if (isInventoryLockedFor(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPickupLock(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player && isInventoryLockedFor((Player) event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSwapHandLock(PlayerSwapHandItemsEvent event) {
        if (isInventoryLockedFor(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    // 2. Контроль передвижения в барьерных капсулах (анти-чит возврат на точку) и границ арены для спектаторов
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMoveInWar(PlayerMoveEvent event) {
        ClanWarSession session = extension.getWarService().getActiveSession();
        if (session == null) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();

        // Удержание бойцов внутри стартового барьера во время 1-минутного отсчета
        if (session.getStage() == WarStage.CAGE_COUNTDOWN_1MIN && session.isAliveFighter(player.getName())) {
            Location assigned = session.getAssignedSpawnPoints().get(player.getName().toLowerCase());
            if (assigned != null) {
                if (!to.getWorld().getName().equalsIgnoreCase(assigned.getWorld().getName())
                        || to.getBlockX() != assigned.getBlockX()
                        || to.getBlockZ() != assigned.getBlockZ()
                        || Math.abs(to.getY() - assigned.getY()) > 1.5) {
                    player.teleport(assigned);
                    ChatUtil.sendMessage(player, extension.getWarService().getWarMsgCfg().getString("messages.cage-escape-blocked"));
                    return;
                }
            }
        }

        // Запрет наблюдателям (/war spectate) вылетать за пределы региона боевой арены
        if (session.getSpectators().containsKey(player.getUniqueId())) {
            CuboidBox arenaBox = extension.getWarArenaStorage().getArenaRegion();
            if (arenaBox != null && !arenaBox.contains(to)) {
                Location specPoint = extension.getWarArenaStorage().getSpectatorPoint();
                if (specPoint != null) {
                    player.teleport(specPoint);
                } else {
                    event.setTo(from);
                }
            }
        }
    }

    // 3. Защита бойцов от любого урона, пока идет 1-минутный отсчет в барьерах
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamageInCage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        ClanWarSession session = extension.getWarService().getActiveSession();
        if (session != null && session.getStage() == WarStage.CAGE_COUNTDOWN_1MIN) {
            Player p = (Player) event.getEntity();
            if (session.isAliveFighter(p.getName())) {
                event.setCancelled(true);
            }
        }
    }

    // 4. Разрешение ломать и ставить блоки на арене во время активного боя (кроме барьеров)
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onArenaBlockBreak(BlockBreakEvent event) {
        if (!extension.getWarArenaStorage().isInWarArena(event.getBlock().getLocation())) return;
        if (event.getBlock().getType() == Material.BARRIER) {
            event.setCancelled(true);
            return;
        }
        ClanWarSession session = extension.getWarService().getActiveSession();
        if (session != null && session.getStage() == WarStage.ACTIVE_BATTLE
                && session.isAliveFighter(event.getPlayer().getName())) {
            event.setCancelled(false);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onArenaBlockPlace(BlockPlaceEvent event) {
        if (!extension.getWarArenaStorage().isInWarArena(event.getBlock().getLocation())) return;
        ClanWarSession session = extension.getWarService().getActiveSession();
        if (session != null && session.getStage() == WarStage.ACTIVE_BATTLE
                && session.isAliveFighter(event.getPlayer().getName())) {
            event.setCancelled(false);
        }
    }

    // 5. Смерть бойца на арене или выход с сервера во время капсулы/боя (авто-смерть)
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFighterDeath(PlayerDeathEvent event) {
        ClanWarSession session = extension.getWarService().getActiveSession();
        if (session == null) return;
        Player victim = event.getEntity();
        if (session.isAliveFighter(victim.getName())) {
            extension.getWarService().handleFighterDeathOrQuit(victim, victim.getKiller(), false);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuitDuringWar(PlayerQuitEvent event) {
        ClanWarSession session = extension.getWarService().getActiveSession();
        if (session == null) return;
        Player player = event.getPlayer();

        if (session.getSpectators().containsKey(player.getUniqueId())) {
            extension.getWarService().leaveSpectatorMode(player);
            return;
        }

        if ((session.getStage() == WarStage.CAGE_COUNTDOWN_1MIN || session.getStage() == WarStage.ACTIVE_BATTLE)
                && session.isAliveFighter(player.getName())) {
            player.setHealth(0.0);
            extension.getWarService().handleFighterDeathOrQuit(player, null, true);
        }
    }

    // 6. Ограничение команд во время боя и во время 2-минутного сбора лута победителями
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onWarCommandPreprocess(PlayerCommandPreprocessEvent event) {
        ClanWarSession session = extension.getWarService().getActiveSession();
        if (session == null) return;

        Player player = event.getPlayer();
        String rawCmd = event.getMessage().trim().split(" ")[0].toLowerCase();
        if (rawCmd.contains(":")) {
            rawCmd = "/" + rawCmd.substring(rawCmd.indexOf(':') + 1);
        }

        // Ограничение команд для наблюдателей (разрешен только /war leave)
        if (session.getSpectators().containsKey(player.getUniqueId())) {
            if (!event.getMessage().toLowerCase().startsWith("/war leave")) {
                event.setCancelled(true);
                ChatUtil.sendMessage(player, "&cВ режиме наблюдения доступна только команда &e/war leave&c!");
            }
            return;
        }

        if (!session.isAliveFighter(player.getName())) return;

        if (session.getStage() == WarStage.CAGE_COUNTDOWN_1MIN || session.getStage() == WarStage.ACTIVE_BATTLE) {
            List<String> allowed = extension.getWarService().getWarCfg().getStringList("commands-whitelist.during-battle");
            if (!isCommandInList(rawCmd, allowed)) {
                event.setCancelled(true);
                ChatUtil.sendMessage(player, "&cКоманды заблокированы во время клановой битвы!");
            }
        } else if (session.getStage() == WarStage.WINNER_LOOT_2MIN) {
            List<String> allowed = extension.getWarService().getWarCfg().getStringList("commands-whitelist.during-winner-loot");
            if (!isCommandInList(rawCmd, allowed)) {
                event.setCancelled(true);
                ChatUtil.sendMessage(player, "&cЭта команда недоступна на арене!");
            }
        }
    }

    private boolean isCommandInList(String rawCmd, List<String> list) {
        if (list == null) return false;
        for (String item : list) {
            String clean = item.trim().toLowerCase();
            if (!clean.startsWith("/")) clean = "/" + clean;
            if (rawCmd.equalsIgnoreCase(clean)) return true;
        }
        return false;
    }
}