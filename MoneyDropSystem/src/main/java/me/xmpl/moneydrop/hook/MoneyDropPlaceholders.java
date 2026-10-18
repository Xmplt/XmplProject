package me.xmpl.moneydrop.hook;

import me.xmpl.lib.hook.ExtensionPlaceholder;
import me.xmpl.lib.util.NumberDateUtil;
import me.xmpl.moneydrop.storage.MoneyDropStatsStorage;
import org.bukkit.entity.Player;

public class MoneyDropPlaceholders implements ExtensionPlaceholder {
    private final MoneyDropStatsStorage statsStorage;

    public MoneyDropPlaceholders(MoneyDropStatsStorage statsStorage) {
        this.statsStorage = statsStorage;
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (player == null) return "0";
        long[] stats = statsStorage.getStats(player.getName());

        switch (params.toLowerCase()) {
            case "mob_earned":
                return NumberDateUtil.formatInteger(stats[0]);
            case "pvp_stolen":
                return NumberDateUtil.formatInteger(stats[1]);
            case "pvp_lost":
                return NumberDateUtil.formatInteger(stats[2]);
            default:
                return null;
        }
    }
}