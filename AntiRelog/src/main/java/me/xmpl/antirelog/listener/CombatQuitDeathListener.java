package me.xmpl.antirelog.listener;

import me.xmpl.antirelog.AntiRelogExtension;
import me.xmpl.antirelog.model.CombatSession;
import me.xmpl.antirelog.service.CombatManager;
import me.xmpl.antirelog.storage.CombatLogStorage;
import me.xmpl.lib.util.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class CombatQuitDeathListener implements Listener {
    private final AntiRelogExtension extension;
    private final CombatManager combatManager;
    private final CombatLogStorage logStorage;

    public CombatQuitDeathListener(AntiRelogExtension extension, CombatManager combatManager, CombatLogStorage logStorage) {
        this.extension = extension;
        this.combatManager = combatManager;
        this.logStorage = logStorage;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (!combatManager.isInPvp(player)) return;

        CombatSession session = combatManager.getSession(player);
        String opponent = session != null ? session.getOpponentName() : "Неизвестно";

        combatManager.untagPlayer(player, false);

        String bypassQuitPerm = extension.getConfig().getString("permissions.bypass-quit-punish", "antirelog.bypass.quit");
        if (player.hasPermission(bypassQuitPerm)) return;

        if (extension.getConfig().getBoolean("pvp.kill-on-quit", true)) {
            player.setHealth(0.0); // Мгновенно убивает игрока перед выходом, дропая инвентарь
        }

        logStorage.logCombatQuitAsync(player.getUniqueId(), player.getName());

        String broadcastMsg = extension.getCustomConfig("messages.yml").get().getString("quit-in-pvp-broadcast");
        if (broadcastMsg != null && !broadcastMsg.isEmpty()) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                ChatUtil.sendMessage(online, broadcastMsg, "%player%", player.getName(), "%opponent%", opponent);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (combatManager.isInPvp(player)) {
            combatManager.untagPlayer(player, true);
        }
    }
}