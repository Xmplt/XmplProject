package me.xmpl.nexuschest.command;

import me.xmpl.lib.util.ChatUtil;
import me.xmpl.lib.util.TimeUtil;
import me.xmpl.nexuschest.NexusChestExtension;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public class NexusPlayerCommand implements CommandExecutor {
    private final NexusChestExtension extension;

    public NexusPlayerCommand(NexusChestExtension extension) {
        this.extension = extension;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player player = (Player) sender;
        FileConfiguration msgCfg = extension.getCustomConfig("messages.yml").get();

        if (args.length < 1) {
            if (extension.getEventManager().isEventRunning()) {
                ChatUtil.sendMessage(player, msgCfg.getString("event-broadcasts.running-info")
                        .replace("{code}", extension.getEventManager().getCurrentPinCode()));
            } else {
                String timeStr = TimeUtil.formatDuration(extension.getEventManager().getSecondsUntilStart() * 1000L);
                ChatUtil.sendMessage(player, msgCfg.getString("teleport-and-access.event-not-running"), "{time}", timeStr);
            }
            return true;
        }

        extension.getEventManager().handlePlayerEnterByCode(player, args[0]);
        return true;
    }
}