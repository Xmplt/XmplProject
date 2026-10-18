package me.xmpl.headdrop.hook;

import me.xmpl.headdrop.storage.HeadStatsStorage;
import me.xmpl.lib.hook.ExtensionPlaceholder;
import org.bukkit.entity.Player;

public class HeadDropPlaceholders implements ExtensionPlaceholder {
    private final HeadStatsStorage statsStorage;

    public HeadDropPlaceholders(HeadStatsStorage statsStorage) {
        this.statsStorage = statsStorage;
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (player == null) return "0";
        int[] stats = statsStorage.getStats(player.getName());

        switch (params.toLowerCase()) {
            case "collected":
                return String.valueOf(stats[0]);
            case "lost":
                return String.valueOf(stats[1]);
            default:
                return null;
        }
    }
}