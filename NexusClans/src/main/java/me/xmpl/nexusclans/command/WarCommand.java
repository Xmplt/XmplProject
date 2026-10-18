package me.xmpl.nexusclans.command;

import me.xmpl.lib.util.ChatUtil;
import me.xmpl.nexusclans.NexusClansExtension;
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

public class WarCommand implements CommandExecutor, TabCompleter {
    private final NexusClansExtension extension;

    public WarCommand(NexusClansExtension extension) {
        this.extension = extension;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player player = (Player) sender;

        if (args.length == 0) {
            ChatUtil.sendMessage(player, "&6=== Команды клановой войны (/war) ===");
            ChatUtil.sendMessage(player, "&e/war spectate &7- Наблюдать за активной клановой битвой");
            ChatUtil.sendMessage(player, "&e/war leave &7- Покинуть режим наблюдения");
            ChatUtil.sendMessage(player, "&e/war membersedit <СтарыйНик> <НовыйНик> &7- Заменить бойца во время подготовки");
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "spectate":
            case "spec":
            case "watch":
                extension.getWarService().joinSpectatorMode(player);
                return true;
            case "leave":
                extension.getWarService().leaveSpectatorMode(player);
                return true;
            case "membersedit": {
                if (args.length < 3) {
                    ChatUtil.sendMessage(player, "&cИспользование: &e/war membersedit <СтарыйНик> <НовыйНик>");
                    return true;
                }
                extension.getWarService().requestMemberReplacement(player, args[1], args[2]);
                return true;
            }
            case "accept":
                extension.getWarService().respondToWarInvite(player, true);
                return true;
            case "deny":
                extension.getWarService().respondToWarInvite(player, false);
                return true;
            default:
                ChatUtil.sendMessage(player, "&cНеизвестная подкоманда! Используйте &e/war spectate &cили &e/war leave&c.");
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("spectate", "leave", "membersedit", "accept", "deny").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("membersedit")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}