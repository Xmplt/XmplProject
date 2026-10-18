package me.xmpl.headdrop.command;

import me.xmpl.headdrop.HeadDropExtension;
import me.xmpl.headdrop.service.HeadFactoryService;
import me.xmpl.headdrop.storage.HeadStatsStorage;
import me.xmpl.lib.util.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class HeadDropCommand implements CommandExecutor, TabCompleter {
    private final HeadDropExtension extension;
    private final HeadFactoryService headFactory;
    private final HeadStatsStorage statsStorage;

    public HeadDropCommand(HeadDropExtension extension, HeadFactoryService headFactory, HeadStatsStorage statsStorage) {
        this.extension = extension;
        this.headFactory = headFactory;
        this.statsStorage = statsStorage;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        FileConfiguration cmdCfg = extension.getCustomConfig("commands/headdrop.yml").get();
        String perm = extension.getConfig().getString("permissions.admin", "headdrop.admin");

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

        if (sub.equals("give") && args.length >= 2) {
            if (!(sender instanceof Player)) return true;
            Player player = (Player) sender;
            String targetName = args[1];

            ItemStack head = headFactory.buildHeadByName(targetName);
            player.getInventory().addItem(head);
            ChatUtil.sendMessage(player, cmdCfg.getString("messages.head-given"), "{player}", targetName);
            return true;
        }

        if (sub.equals("stats")) {
            String targetName = args.length >= 2 ? args[1] : sender.getName();
            int[] stats = statsStorage.getStats(targetName);
            ChatUtil.sendMessage(sender, cmdCfg.getString("messages.stats-format"),
                    "{player}", targetName,
                    "{collected}", stats[0],
                    "{lost}", stats[1]);
            return true;
        }

        ChatUtil.sendMessages(sender, cmdCfg.getStringList("messages.help"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("give", "stats", "reload", "help").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("stats"))) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}