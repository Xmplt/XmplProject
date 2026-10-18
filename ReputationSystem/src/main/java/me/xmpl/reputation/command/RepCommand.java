package me.xmpl.reputation.command;

import me.xmpl.lib.util.ChatUtil;
import me.xmpl.reputation.ReputationSystemExtension;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class RepCommand implements CommandExecutor, TabCompleter {
    private final ReputationSystemExtension extension;

    public RepCommand(ReputationSystemExtension extension) {
        this.extension = extension;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        FileConfiguration msgCfg = extension.getCustomConfig("commands/rep.yml").get();
        String adminPerm = extension.getConfig().getString("permissions.admin", "reputationsystem.admin");

        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission(adminPerm)) {
                ChatUtil.sendMessage(sender, msgCfg.getString("messages.no-permission"));
                return true;
            }
            extension.reloadAllConfigs();
            ChatUtil.sendMessage(sender, msgCfg.getString("messages.reload-success"));
            return true;
        }

        if (extension.getConfig().getBoolean("permissions.require-view-permission", false)) {
            String viewPerm = extension.getConfig().getString("permissions.view", "reputationsystem.view");
            if (!sender.hasPermission(viewPerm)) {
                ChatUtil.sendMessage(sender, msgCfg.getString("messages.no-permission"));
                return true;
            }
        }

        String targetName;
        if (args.length == 0) {
            if (!(sender instanceof Player)) return true;
            targetName = sender.getName();
        } else {
            targetName = extension.getReputationService().resolveTargetName(args[0]);
            if (targetName == null) {
                ChatUtil.sendMessage(sender, msgCfg.getString("messages.player-not-found", "&cИгрок не найден."));
                return true;
            }
        }

        int rep = extension.getScoreStorage().getReputation(targetName);
        String badge = extension.getReputationService().formatBadge(rep);
        String balanceMsg = msgCfg.getString("messages.balance", "&fРепутация игрока &e%player%: &6%amount%");

        ChatUtil.sendMessage(sender, balanceMsg
                .replace("%player%", targetName)
                .replace("%amount%", String.valueOf(rep))
                .replace("%badge%", badge));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}