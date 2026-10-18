package me.xmpl.fishing.command;

import me.xmpl.fishing.FishingSystemExtension;
import me.xmpl.lib.util.ChatUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SellFishCommand implements CommandExecutor {
    private final FishingSystemExtension extension;

    public SellFishCommand(FishingSystemExtension extension) {
        this.extension = extension;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player player = (Player) sender;

        if (extension.getConfig().getBoolean("permissions.require-sell-permission", false)) {
            String perm = extension.getConfig().getString("permissions.sell", "fishingsystem.sell");
            if (!player.hasPermission(perm)) {
                String msg = extension.getCustomConfig("commands/fishing.yml").get().getString("messages.no-permission");
                ChatUtil.sendMessage(player, msg);
                return true;
            }
        }

        extension.getSellService().sellAllPlayerFish(player);
        return true;
    }
}