package me.xmpl.antirelog.listener;

import me.xmpl.antirelog.AntiRelogExtension;
import me.xmpl.antirelog.model.CombatSession;
import me.xmpl.antirelog.service.CombatManager;
import me.xmpl.lib.util.ChatUtil;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.List;

public class CombatCommandListener implements Listener {
    private final AntiRelogExtension extension;
    private final CombatManager combatManager;

    public CombatCommandListener(AntiRelogExtension extension, CombatManager combatManager) {
        this.extension = extension;
        this.combatManager = combatManager;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!combatManager.isInPvp(player)) return;

        FileConfiguration wlConfig = extension.getCustomConfig("whitelist-commands.yml").get();
        if (!wlConfig.getBoolean("block-commands-in-pvp", true)) return;

        String bypassPerm = extension.getConfig().getString("permissions.bypass-commands", "antirelog.bypass.commands");
        if (player.hasPermission(bypassPerm)) return;

        // Извлекаем саму команду (первое слово без слеша, а также без префикса плагина типа essentials:home)
        String rawMessage = event.getMessage().trim();
        String baseCmd = rawMessage.split(" ")[0].toLowerCase();
        if (baseCmd.startsWith("/")) {
            baseCmd = baseCmd.substring(1);
        }
        if (baseCmd.contains(":")) {
            baseCmd = baseCmd.substring(baseCmd.indexOf(':') + 1);
        }

        List<String> allowedList = wlConfig.getStringList("allowed-commands");
        for (String allowed : allowedList) {
            String cleanAllowed = allowed.startsWith("/") ? allowed.substring(1).toLowerCase() : allowed.toLowerCase();
            if (baseCmd.equalsIgnoreCase(cleanAllowed)) {
                return; // Команда в белом списке — разрешаем!
            }
        }

        // Блокируем команду и пишем сообщение
        event.setCancelled(true);
        CombatSession session = combatManager.getSession(player);
        int timeLeft = session != null ? session.getRemainingSeconds() : 0;

        String msg = extension.getCustomConfig("messages.yml").get().getString("command-blocked");
        ChatUtil.sendMessage(player, msg, "%command%", baseCmd, "%time%", timeLeft);
    }
}