package me.xmpl.shield.command;

import me.xmpl.lib.util.ChatUtil;
import me.xmpl.shield.XmplShieldExtension;
import me.xmpl.shield.service.ShieldBroadcastService;
import me.xmpl.shield.storage.ShieldStatsStorage;
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

public class ShieldCommand implements CommandExecutor, TabCompleter {
    private final XmplShieldExtension extension;
    private final ShieldBroadcastService broadcastService;
    private final ShieldStatsStorage statsStorage;

    public ShieldCommand(XmplShieldExtension extension,
                         ShieldBroadcastService broadcastService,
                         ShieldStatsStorage statsStorage) {
        this.extension = extension;
        this.broadcastService = broadcastService;
        this.statsStorage = statsStorage;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        FileConfiguration cmdCfg = extension.getCustomConfig("commands/shield.yml").get();
        String perm = cmdCfg.getString("permission", "xmplshield.admin");

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

        if (sub.equals("test")) {
            if (!(sender instanceof Player)) return true;
            Player player = (Player) sender;
            broadcastService.broadcastShieldDisabled(player, player);
            double radius = extension.getConfig().getDouble("mechanics.broadcast-radius", 22.0);
            ChatUtil.sendMessage(player, cmdCfg.getString("messages.test-sound-played"), "{radius}", radius);
            return true;
        }

        if (sub.equals("stats")) {
            String targetName = args.length >= 2 ? args[1] : sender.getName();
            int broken = statsStorage.getBrokenOthers(targetName);
            int lost = statsStorage.getLostOwn(targetName);
            ChatUtil.sendMessage(sender, cmdCfg.getString("messages.stats-format"),
                    "{player}", targetName,
                    "{broken_others}", broken,
                    "{lost_own}", lost);
            return true;
        }

        ChatUtil.sendMessages(sender, cmdCfg.getStringList("messages.help"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("test", "stats", "reload", "help").stream()
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