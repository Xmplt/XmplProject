package me.xmpl.topka.listener;

import me.xmpl.lib.util.ChatUtil;
import me.xmpl.topka.TopkaExtension;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TopkaRegionAndCommandListener implements Listener {
    private final TopkaExtension extension;
    private final Map<UUID, Long> entryMessageCooldowns = new ConcurrentHashMap<>();

    public TopkaRegionAndCommandListener(TopkaExtension extension) {
        this.extension = extension;
    }

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

        checkRegionEntryTransition(event.getPlayer(), from, to);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null) return;
        checkRegionEntryTransition(event.getPlayer(), event.getFrom(), event.getTo());
    }

    private void checkRegionEntryTransition(Player player, Location from, Location to) {
        boolean wasIn = extension.getArenaStorage().isInPvpRegion(from);
        boolean nowIn = extension.getArenaStorage().isInPvpRegion(to);

        // Игрок только что зашел в PvP регион Топки
        if (!wasIn && nowIn) {
            FileConfiguration rgCfg = extension.getCustomConfig("region-rules.yml").get();
            if (!rgCfg.getBoolean("entry-notification.enabled", true)) return;

            long cooldownMs = rgCfg.getLong("entry-notification.cooldown-seconds", 15L) * 1000L;
            long now = System.currentTimeMillis();
            Long lastSent = entryMessageCooldowns.get(player.getUniqueId());

            if (lastSent == null || (now - lastSent) >= cooldownMs) {
                entryMessageCooldowns.put(player.getUniqueId(), now);
                String msg = rgCfg.getString("entry-notification.message", "&cВы вошли в PvP зону! Команды заблокированы.");
                ChatUtil.sendMessage(player, msg);
            }
        }
    }

    /**
     * Блокировка всех команд внутри PvP региона Топки, кроме списка allowcmds (/l, /login, /reg, /register, /killme, /leave).
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!extension.getArenaStorage().isInPvpRegion(player.getLocation())) {
            return;
        }

        FileConfiguration rgCfg = extension.getCustomConfig("region-rules.yml").get();
        if (!rgCfg.getBoolean("command-restrictions.enabled", true)) return;

        if (rgCfg.getBoolean("command-restrictions.bypass-for-admins", false)) {
            String adminPerm = extension.getConfig().getString("permissions.admin", "topka.admin");
            if (player.hasPermission(adminPerm)) return;
        }

        String rawCmd = event.getMessage().trim().split(" ")[0].toLowerCase();
        // Очищаем namespace плагина (например /authme:login -> /login)
        if (rawCmd.contains(":")) {
            rawCmd = "/" + rawCmd.substring(rawCmd.indexOf(':') + 1);
        }

        List<String> allowedList = rgCfg.getStringList("command-restrictions.allowcmds");
        for (String allowed : allowedList) {
            String normalizedAllowed = allowed.trim().toLowerCase();
            if (!normalizedAllowed.startsWith("/")) {
                normalizedAllowed = "/" + normalizedAllowed;
            }
            if (rawCmd.equalsIgnoreCase(normalizedAllowed)) {
                return;
            }
        }

        event.setCancelled(true);
        String blockedMsg = rgCfg.getString("command-restrictions.blocked-message",
                "&cВы не можете использовать команды на PvP &cарене. Если вы застряли, то напишите - &c/killme");
        ChatUtil.sendMessage(player, blockedMsg);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        entryMessageCooldowns.remove(event.getPlayer().getUniqueId());
    }
}