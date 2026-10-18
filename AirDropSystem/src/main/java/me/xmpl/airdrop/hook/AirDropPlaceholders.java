package me.xmpl.airdrop.hook;

import me.xmpl.airdrop.AirDropExtension;
import me.xmpl.airdrop.model.AirDropStage;
import me.xmpl.airdrop.model.PersistedAirDropState;
import me.xmpl.lib.hook.ExtensionPlaceholder;
import me.xmpl.lib.util.TimeUtil;
import org.bukkit.entity.Player;

public class AirDropPlaceholders implements ExtensionPlaceholder {
    private final AirDropExtension extension;

    public AirDropPlaceholders(AirDropExtension extension) {
        this.extension = extension;
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        PersistedAirDropState st = extension.getLifecycleManager().getState();
        switch (params.toLowerCase()) {
            case "stage":
                return st.stage.name();
            case "time":
                return TimeUtil.formatDuration(st.timerSeconds * 1000L);
            case "world":
                return st.worldKey != null ? extension.getLifecycleManager().getWorldDisplayName(st.worldKey) : "-";
            case "coords": {
                if (st.stage == AirDropStage.IDLE || st.chestLocation == null) return "Ожидание";
                return st.chestLocation.getBlockX() + ", " + st.chestLocation.getBlockY() + ", " + st.chestLocation.getBlockZ();
            }
            default:
                return null;
        }
    }
}