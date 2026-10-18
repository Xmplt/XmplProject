package me.xmpl.enderrub.listener;

import me.xmpl.enderrub.storage.BalanceStorage;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class AccountInitListener implements Listener {
    private final BalanceStorage balanceStorage;

    public AccountInitListener(BalanceStorage balanceStorage) {
        this.balanceStorage = balanceStorage;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        balanceStorage.ensureAccountExists(event.getPlayer().getName());
    }
}