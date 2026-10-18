package me.xmpl.redline.listener;

import me.xmpl.redline.SpawnRedLineExtension;
import me.xmpl.redline.model.PlayerMarkData;
import me.xmpl.redline.service.RedLineMarkService;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;

import java.util.List;

public class RedLineMovementListener implements Listener {
    private final SpawnRedLineExtension extension;
    private final RedLineMarkService markService;

    public RedLineMovementListener(SpawnRedLineExtension extension, RedLineMarkService markService) {
        this.extension = extension;
        this.markService = markService;
    }

    // 1. Оптимизированная проверка при переходе с одного блока на другой
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        markService.handleZoneTransition(event.getPlayer(), to);
    }

    // 2. Отслеживаем ввод именно команды /spawn (чтобы отличить /spawn от /warp)
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        String cmd = event.getMessage().trim().split(" ")[0].toLowerCase();
        List<String> spawnCmds = extension.getCustomConfig("teleports.yml").get().getStringList("teleport-rules.spawn-commands");

        for (String s : spawnCmds) {
            if (cmd.equalsIgnoreCase(s)) {
                PlayerMarkData data = markService.getData(event.getPlayer());
                data.setLastSpawnCommandTypedMillis(System.currentTimeMillis());
                break;
            }
        }
    }

    // 3. Обработка самой телепортации (разделяет /spawn, /warp на спавне и /warp вне спавна)
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null) return;
        markService.handleTeleport(event.getPlayer(), event.getFrom(), event.getTo());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        markService.getData(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        markService.removePlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer != null && markService.isMarkedAndVulnerable(victim)) {
            extension.getStatsStorage().incrementSpawnMarkedKills(killer.getName());
        }
        markService.getData(victim).clearMark();
    }
}