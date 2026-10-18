package me.xmpl.nexuschest.hook;

import me.xmpl.lib.hook.ExtensionPlaceholder;
import me.xmpl.lib.util.TimeUtil;
import me.xmpl.nexuschest.NexusChestExtension;
import org.bukkit.entity.Player;

public class NexusChestPlaceholders implements ExtensionPlaceholder {
    private final NexusChestExtension extension;

    public NexusChestPlaceholders(NexusChestExtension extension) {
        this.extension = extension;
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        boolean running = extension.getEventManager().isEventRunning();
        switch (params.toLowerCase()) {
            case "status":
                return running ? "&a&lИдет!" : "&eОжидание";
            case "code":
                return running ? extension.getEventManager().getCurrentPinCode() : "----";
            case "time_left": {
                int sec = running
                        ? extension.getEventManager().getActiveSecondsLeft()
                        : extension.getEventManager().getSecondsUntilStart();
                return TimeUtil.formatDuration(sec * 1000L);
            }
            default:
                return null;
        }
    }
}