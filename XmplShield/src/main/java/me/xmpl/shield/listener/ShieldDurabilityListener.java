package me.xmpl.shield.listener;

import me.xmpl.shield.XmplShieldExtension;
import me.xmpl.shield.service.ShieldBroadcastService;
import me.xmpl.shield.storage.ShieldStatsStorage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemBreakEvent;

public class ShieldDurabilityListener implements Listener {
    private final XmplShieldExtension extension;
    private final ShieldBroadcastService broadcastService;
    private final ShieldStatsStorage statsStorage;

    public ShieldDurabilityListener(XmplShieldExtension extension,
                                    ShieldBroadcastService broadcastService,
                                    ShieldStatsStorage statsStorage) {
        this.extension = extension;
        this.broadcastService = broadcastService;
        this.statsStorage = statsStorage;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onShieldItemBreak(PlayerItemBreakEvent event) {
        if (!extension.getConfig().getBoolean("mechanics.trigger-on-item-durability-break", true)) {
            return;
        }
        if (event.getBrokenItem().getType() != Material.SHIELD) {
            return;
        }

        Player player = event.getPlayer();
        broadcastService.broadcastShieldDestroyed(player);
        statsStorage.recordShieldBreak(null, player.getName());
    }
}