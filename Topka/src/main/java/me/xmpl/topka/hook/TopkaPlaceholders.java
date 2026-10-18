package me.xmpl.topka.hook;

import me.xmpl.lib.hook.ExtensionPlaceholder;
import me.xmpl.lib.util.TimeUtil;
import me.xmpl.topka.TopkaExtension;
import me.xmpl.topka.model.PersistedTopkaState;
import org.bukkit.entity.Player;

public class TopkaPlaceholders implements ExtensionPlaceholder {
    private final TopkaExtension extension;

    public TopkaPlaceholders(TopkaExtension extension) {
        this.extension = extension;
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        PersistedTopkaState st = extension.getLifecycleService().getState();
        switch (params.toLowerCase()) {
            case "stage":
                return st.stage.name();
            case "time":
                return TimeUtil.formatDuration(st.remainingSeconds * 1000L);
            case "terrain":
                return st.activeTerrainId != null && !st.activeTerrainId.isEmpty() ? st.activeTerrainId : "Standart";
            default:
                return null;
        }
    }
}