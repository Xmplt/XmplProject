package me.xmpl.antirelog.command;

import me.xmpl.antirelog.AntiRelogExtension;
import me.xmpl.antirelog.model.CombatSession;
import me.xmpl.antirelog.service.CombatManager;
import me.xmpl.lib.config.CustomConfig;
import me.xmpl.lib.util.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class AntiRelogCommand implements CommandExecutor, TabCompleter {
    private final AntiRelogExtension extension;
    private final CombatManager combatManager;

    public AntiRelogCommand(AntiRelogExtension extension, CombatManager combatManager) {
        this.extension = extension;
        this.combatManager = combatManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        CustomConfig cmdCfg = extension.getCustomConfig("commands/antirelog.yml");
        String adminPerm = extension.getConfig().getString("permissions.admin", "antirelog.admin");

        if (!sender.hasPermission(adminPerm)) {
            ChatUtil.sendMessage(sender, cmdCfg.get().getString("no-permission"));
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            ChatUtil.sendMessages(sender, cmdCfg.get().getStringList("help"));
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("reload")) {
            extension.reloadAllConfigs();
            extension.getLogStorage().reload();
            ChatUtil.sendMessage(sender, cmdCfg.get().getString("reload-success"));
            return true;
        }

        if (sub.equals("status")) {
            Player target = (args.length >= 2) ? Bukkit.getPlayer(args[1]) : (sender instanceof Player ? (Player) sender : null);
            if (target == null) {
                ChatUtil.sendMessage(sender, cmdCfg.get().getString("player-not-found"));
                return true;
            }
            CombatSession session = combatManager.getSession(target);
            boolean inPvp = session != null;
            String statusText = inPvp ? "&cВ ПВП" : "&aСвободен";
            int time = inPvp ? session.getRemainingSeconds() : 0;
            String opp = inPvp ? session.getOpponentName() : "-";

            ChatUtil.sendMessage(sender, cmdCfg.get().getString("status-message"),
                    "%player%", target.getName(),
                    "%status%", statusText,
                    "%time%", time,
                    "%opponent%", opp);
            return true;
        }

        if (sub.equals("tag") && args.length >= 2) {
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                ChatUtil.sendMessage(sender, cmdCfg.get().getString("player-not-found"));
                return true;
            }
            combatManager.tagPlayer(target, sender.getName());
            ChatUtil.sendMessage(sender, cmdCfg.get().getString("tag-forced"), "%player%", target.getName());
            return true;
        }

        if (sub.equals("untag") && args.length >= 2) {
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                ChatUtil.sendMessage(sender, cmdCfg.get().getString("player-not-found"));
                return true;
            }
            combatManager.untagPlayer(target, true);
            ChatUtil.sendMessage(sender, cmdCfg.get().getString("untag-forced"), "%player%", target.getName());
            return true;
        }

        ChatUtil.sendMessages(sender, cmdCfg.get().getStringList("help"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("status", "tag", "untag", "reload", "help").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}