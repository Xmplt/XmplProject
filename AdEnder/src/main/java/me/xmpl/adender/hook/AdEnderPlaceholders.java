package me.xmpl.adender.hook;

import me.xmpl.adender.storage.PortalStatsDatabaseStorage;
import me.xmpl.lib.hook.ExtensionPlaceholder;
import org.bukkit.entity.Player;

public class AdEnderPlaceholders implements ExtensionPlaceholder {
    private final PortalStatsDatabaseStorage statsStorage;

    public AdEnderPlaceholders(PortalStatsDatabaseStorage statsStorage) {
        this.statsStorage = statsStorage;
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (player == null) return "0";
        int[] st = statsStorage.getStats(player.getName());

        switch (params.toLowerCase()) {
            case "ad_teleports":
                return String.valueOf(st[0]);
            case "ender_teleports":
                return String.valueOf(st[1]);
            default:
                return null;
        }
    }
}