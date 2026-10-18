package me.xmpl.moneydrop.command;

import me.xmpl.lib.util.ChatUtil;
import me.xmpl.lib.util.NumberDateUtil;
import me.xmpl.moneydrop.MoneyDropExtension;
import me.xmpl.moneydrop.storage.MoneyDropStatsStorage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class MoneyDropCommand implements CommandExecutor, TabCompleter {
    private final MoneyDropExtension extension;
    private final MoneyDropStatsStorage statsStorage;

    public MoneyDropCommand(MoneyDropExtension extension, MoneyDropStatsStorage statsStorage) {
        this.extension = extension;
        this.statsStorage = statsStorage;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        FileConfiguration cmdCfg = extension.getCustomConfig("commands/moneydrop.yml").get();
        String perm = extension.getConfig().getString("permissions.admin", "moneydrop.admin");

        if (!sender.hasPermission(perm)) {
            ChatUtil.sendMessage(sender, cmdCfg.getString("messages.no-permission"));
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            ChatUtil.sendMessages(sender, cmdCfg.getStringList("messages.help"));
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("reload")) {
            extension.reloadAllConfigs();
            statsStorage.reload();
            ChatUtil.sendMessage(sender, cmdCfg.getString("messages.reload-success"));
            return true;
        }

        if (sub.equals("stats")) {
            String target = args.length >= 2 ? args[1] : sender.getName();
            long[] stats = statsStorage.getStats(target);
            ChatUtil.sendMessage(sender, cmdCfg.getString("messages.stats-format"),
                    "{player}", target,
                    "{mob_earned}", NumberDateUtil.formatInteger(stats[0]),
                    "{pvp_stolen}", NumberDateUtil.formatInteger(stats[1]),
                    "{pvp_lost}", NumberDateUtil.formatInteger(stats[2]));
            return true;
        }

        ChatUtil.sendMessages(sender, cmdCfg.getStringList("messages.help"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("stats", "reload", "help").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("stats")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}