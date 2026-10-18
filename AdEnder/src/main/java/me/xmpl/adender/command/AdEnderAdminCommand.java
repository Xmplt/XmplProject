package me.xmpl.adender.command;

import me.xmpl.adender.AdEnderExtension;
import me.xmpl.adender.model.PortalDestinationType;
import me.xmpl.lib.util.ChatUtil;
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

public class AdEnderAdminCommand implements CommandExecutor, TabCompleter {
    private final AdEnderExtension extension;

    public AdEnderAdminCommand(AdEnderExtension extension) {
        this.extension = extension;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        FileConfiguration cmdCfg = extension.getCustomConfig("commands/adender.yml").get();
        String adminPerm = extension.getConfig().getString("permissions.admin", "adender.admin");

        if (!sender.hasPermission(adminPerm)) {
            ChatUtil.sendMessage(sender, cmdCfg.getString("messages.no-permission"));
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            ChatUtil.sendMessages(sender, cmdCfg.getStringList("messages.help-admin"));
            return true;
        }

        String sub = args[0].toLowerCase();

        // 1. Установка точки к порталу в Ад со взглядом: /adender setad
        if (sub.equals("setad")) {
            if (!(sender instanceof Player)) return true;
            Player player = (Player) sender;
            extension.getLocationStorage().setLocation(PortalDestinationType.AD, player.getLocation());
            ChatUtil.sendMessage(player, cmdCfg.getString("messages.set-ad-success"));
            return true;
        }

        // 2. Установка точки к порталу в Энд со взглядом: /adender setender
        if (sub.equals("setender") || sub.equals("setend")) {
            if (!(sender instanceof Player)) return true;
            Player player = (Player) sender;
            extension.getLocationStorage().setLocation(PortalDestinationType.ENDER, player.getLocation());
            ChatUtil.sendMessage(player, cmdCfg.getString("messages.set-ender-success"));
            return true;
        }

        // 3. Перезагрузка конфигов: /adender reload
        if (sub.equals("reload")) {
            extension.reloadAllConfigs();
            extension.getLocationStorage().reload();
            extension.getStatsStorage().reload();
            ChatUtil.sendMessage(sender, cmdCfg.getString("messages.reload-success"));
            return true;
        }

        ChatUtil.sendMessages(sender, cmdCfg.getStringList("messages.help-admin"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String adminPerm = extension.getConfig().getString("permissions.admin", "adender.admin");
        if (!sender.hasPermission(adminPerm)) return Collections.emptyList();

        if (args.length == 1) {
            return Arrays.asList("setad", "setender", "reload", "help").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}