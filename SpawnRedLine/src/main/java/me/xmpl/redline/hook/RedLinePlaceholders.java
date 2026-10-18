package me.xmpl.redline.hook;

import me.xmpl.lib.hook.ExtensionPlaceholder;
import me.xmpl.redline.SpawnRedLineExtension;
import me.xmpl.redline.model.PlayerMarkData;
import org.bukkit.entity.Player;

public class RedLinePlaceholders implements ExtensionPlaceholder {
    private final SpawnRedLineExtension extension;

    public RedLinePlaceholders(SpawnRedLineExtension extension) {
        this.extension = extension;
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (player == null) return "";
        PlayerMarkData data = extension.getMarkService().getData(player);
        int defSec = extension.getConfig().getInt("mechanics.vulnerability-seconds", 20);

        switch (params.toLowerCase()) {
            case "marked":
                return extension.getMarkService().isMarkedAndVulnerable(player) ? "&c&lДа" : "&a&lНет";
            case "time":
                return String.valueOf(data.getRemainingSeconds(defSec));
            case "zone":
                return data.getLastZone().name();
            default:
                return null;
        }
    }
}