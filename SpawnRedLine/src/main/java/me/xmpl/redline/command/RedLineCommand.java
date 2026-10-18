package me.xmpl.redline.command;

import me.xmpl.lib.util.ChatUtil;
import me.xmpl.redline.SpawnRedLineExtension;
import me.xmpl.redline.model.PlayerMarkData;
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

public class RedLineCommand implements CommandExecutor, TabCompleter {
    private final SpawnRedLineExtension extension;

    public RedLineCommand(SpawnRedLineExtension extension) {
        this.extension = extension;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        FileConfiguration cmdCfg = extension.getCustomConfig("commands/redline.yml").get();
        String adminPerm = extension.getConfig().getString("permissions.admin", "spawnredline.admin");

        if (!sender.hasPermission(adminPerm)) {
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
            extension.getStatsStorage().reload();
            ChatUtil.sendMessage(sender, cmdCfg.getString("messages.reload-success"));
            return true;
        }

        if (sub.equals("status")) {
            Player target = args.length >= 2 ? Bukkit.getPlayerExact(args[1]) : (sender instanceof Player ? (Player) sender : null);
            if (target == null) {
                ChatUtil.sendMessage(sender, "&cИгрок не в сети!");
                return true;
            }
            PlayerMarkData data = extension.getMarkService().getData(target);
            int defSec = extension.getConfig().getInt("mechanics.vulnerability-seconds", 20);
            boolean vuln = extension.getMarkService().isMarkedAndVulnerable(target);

            ChatUtil.sendMessage(sender, cmdCfg.getString("messages.status-format"),
                    "{player}", target.getName(),
                    "{zone}", data.getLastZone().name(),
                    "{marked}", vuln ? "&cДа" : "&aНет",
                    "{time}", data.getRemainingSeconds(defSec));
            return true;
        }

        if (sub.equals("clear") && args.length >= 2) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                ChatUtil.sendMessage(sender, "&cИгрок не в сети!");
                return true;
            }
            extension.getMarkService().getData(target).clearMark();
            ChatUtil.sendMessage(sender, cmdCfg.getString("messages.cleared-player"), "{player}", target.getName());
            return true;
        }

        ChatUtil.sendMessages(sender, cmdCfg.getStringList("messages.help"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("status", "clear", "reload", "help").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("status") || args[0].equalsIgnoreCase("clear"))) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}