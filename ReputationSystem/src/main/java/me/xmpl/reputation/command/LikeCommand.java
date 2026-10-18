package me.xmpl.reputation.command;

import me.xmpl.lib.util.ChatUtil;
import me.xmpl.reputation.ReputationSystemExtension;
import me.xmpl.reputation.model.VoteType;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class LikeCommand implements CommandExecutor, TabCompleter {
    private final ReputationSystemExtension extension;

    public LikeCommand(ReputationSystemExtension extension) {
        this.extension = extension;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player player = (Player) sender;

        if (args.length < 1) {
            String usage = extension.getCustomConfig("commands/like.yml").get().getString("usage", "&cИспользование: &e/like <Ник>");
            ChatUtil.sendMessage(player, usage);
            return true;
        }

        extension.getReputationService().handleVoteCommand(player, args[0], VoteType.LIKE);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> !n.equalsIgnoreCase(sender.getName()))
                    .filter(n -> n.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}