package me.xmpl.reputation.listener;

import me.xmpl.lib.util.ChatUtil;
import me.xmpl.reputation.ReputationSystemExtension;
import me.xmpl.reputation.storage.ReputationScoreStorage.PlayerRepEntry;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class ReputationJoinListener implements Listener {
    private final ReputationSystemExtension extension;

    public ReputationJoinListener(ReputationSystemExtension extension) {
        this.extension = extension;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        extension.getScoreStorage().ensurePlayerRegistered(player.getName());

        PlayerRepEntry entry = extension.getScoreStorage().getOrCreate(player.getName());
        if (entry.pendingOfflineNotice != null && !entry.pendingOfflineNotice.isEmpty()) {
            ChatUtil.sendMessage(player, entry.pendingOfflineNotice);
            extension.getScoreStorage().clearOfflineNotice(player.getName());
        }
    }
}