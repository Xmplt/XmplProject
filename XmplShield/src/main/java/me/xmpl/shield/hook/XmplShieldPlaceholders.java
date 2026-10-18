package me.xmpl.shield.hook;

import me.xmpl.lib.hook.ExtensionPlaceholder;
import me.xmpl.shield.storage.ShieldStatsStorage;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class XmplShieldPlaceholders implements ExtensionPlaceholder {
    private final ShieldStatsStorage statsStorage;

    public XmplShieldPlaceholders(ShieldStatsStorage statsStorage) {
        this.statsStorage = statsStorage;
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (player == null) return "0";

        switch (params.toLowerCase()) {
            case "broken_others":
                return String.valueOf(statsStorage.getBrokenOthers(player.getName()));
            case "lost_own":
                return String.valueOf(statsStorage.getLostOwn(player.getName()));
            case "cooldown":
                int ticks = player.getCooldown(Material.SHIELD);
                return String.valueOf((int) Math.ceil(ticks / 20.0));
            default:
                return null;
        }
    }
}