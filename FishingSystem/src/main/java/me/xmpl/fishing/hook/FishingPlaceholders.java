package me.xmpl.fishing.hook;

import me.xmpl.fishing.storage.FishingStatsStorage;
import me.xmpl.fishing.storage.FishingStatsStorage.PlayerFishStats;
import me.xmpl.lib.hook.ExtensionPlaceholder;
import me.xmpl.lib.util.NumberDateUtil;
import org.bukkit.entity.Player;

import java.util.Locale;

public class FishingPlaceholders implements ExtensionPlaceholder {
    private final FishingStatsStorage statsStorage;

    public FishingPlaceholders(FishingStatsStorage statsStorage) {
        this.statsStorage = statsStorage;
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (player == null) return "0";
        PlayerFishStats st = statsStorage.getStats(player.getName());

        switch (params.toLowerCase()) {
            case "sold":
                return String.valueOf(st.fishSold);
            case "earned":
                return NumberDateUtil.formatInteger(st.moneyEarned);
            case "max_weight":
                return String.format(Locale.US, "%.2f", st.maxWeightRecord);
            default:
                return null;
        }
    }
}