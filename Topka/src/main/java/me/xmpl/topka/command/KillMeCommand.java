package me.xmpl.topka.command;

import me.xmpl.lib.util.ChatUtil;
import me.xmpl.topka.TopkaExtension;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public class KillMeCommand implements CommandExecutor {
    private final TopkaExtension extension;

    public KillMeCommand(TopkaExtension extension) {
        this.extension = extension;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player player = (Player) sender;
        FileConfiguration msgCfg = extension.getCustomConfig("messages.yml").get();

        if (!extension.getArenaStorage().isInPvpRegion(player.getLocation())) {
            ChatUtil.sendMessage(player, msgCfg.getString(
                    "messages.killme-only-in-region",
                    "&cКоманду /killme можно использовать только внутри PvP зоны Топки!"
            ));
            return true;
        }

        player.setHealth(0.0);
        ChatUtil.sendMessage(player, msgCfg.getString(
                "messages.killme-used",
                "&cВы использовали /killme на PvP арене."
        ));
        return true;
    }
}