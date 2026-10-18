package me.xmpl.enderrub.command;

import me.xmpl.enderrub.EnderRubExtension;
import me.xmpl.enderrub.service.EnderRubService;
import me.xmpl.lib.util.ChatUtil;
import me.xmpl.lib.util.NumberDateUtil;
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

public class EnderRubCommand implements CommandExecutor, TabCompleter {
    private final EnderRubExtension extension;
    private final EnderRubService service;

    public EnderRubCommand(EnderRubExtension extension, EnderRubService service) {
        this.extension = extension;
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        FileConfiguration playerCfg = extension.getCustomConfig("commands/player-command.yml").get();
        FileConfiguration adminCfg = extension.getCustomConfig("commands/admin-command.yml").get();

        // 1. Ввод без аргументов: /endrub или /enderrub -> показываем баланс и 1 страницу истории
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                ChatUtil.sendMessages(sender, adminCfg.getStringList("messages.help"));
                return true;
            }
            if (playerCfg.getBoolean("require-permission", false)
                    && !sender.hasPermission(playerCfg.getString("permission", "enderrub.use"))) {
                ChatUtil.sendMessage(sender, playerCfg.getString("messages.no-permission"));
                return true;
            }
            service.sendBalanceAndHistoryPage(sender, sender.getName(), 1);
            return true;
        }

        String first = args[0].toLowerCase();

        // 2. Если первый аргумент — число (например /endrub 4 или /enderrub 2) -> открываем нужную страницу истории!
        long pageAsNum = NumberDateUtil.parseStrictPositiveLong(first);
        if (pageAsNum != -1L) {
            if (!(sender instanceof Player)) return true;
            if (playerCfg.getBoolean("require-permission", false)
                    && !sender.hasPermission(playerCfg.getString("permission", "enderrub.use"))) {
                ChatUtil.sendMessage(sender, playerCfg.getString("messages.no-permission"));
                return true;
            }
            service.sendBalanceAndHistoryPage(sender, sender.getName(), (int) pageAsNum);
            return true;
        }

        // 3. Защита от попыток передать валюту другому игроку (/endrub pay, send, transfer)
        if (first.equals("pay") || first.equals("send") || first.equals("transfer")) {
            ChatUtil.sendMessage(sender, playerCfg.getString("messages.transfer-forbidden"));
            return true;
        }

        // 4. Админские подкоманды: give, take, set, history, reload, help
        switch (first) {
            case "give":
            case "add":
                return handleAdminGive(sender, args, adminCfg);
            case "take":
            case "remove":
                return handleAdminTake(sender, args, adminCfg);
            case "set":
                return handleAdminSet(sender, args, adminCfg);
            case "history":
            case "check":
            case "info":
                return handleAdminInspect(sender, args, adminCfg);
            case "reload":
                return handleAdminReload(sender, adminCfg);
            default:
                ChatUtil.sendMessages(sender, adminCfg.getStringList("messages.help"));
                return true;
        }
    }

    private boolean handleAdminGive(CommandSender sender, String[] args, FileConfiguration adminCfg) {
        if (!sender.hasPermission(adminCfg.getString("permissions.give", "enderrub.admin.give"))) {
            ChatUtil.sendMessage(sender, adminCfg.getString("messages.no-permission"));
            return true;
        }
        if (args.length < 3) {
            ChatUtil.sendMessages(sender, adminCfg.getStringList("messages.help"));
            return true;
        }

        String targetName = args[1];
        long amount = NumberDateUtil.parseStrictPositiveLong(args[2]);
        if (amount == -1L) {
            ChatUtil.sendMessage(sender, adminCfg.getString("messages.only-integers"));
            return true;
        }

        String reason = args.length >= 4 ? joinArgs(args, 3) : adminCfg.getString("default-reasons.admin-give", "Выдача администратором");
        service.deposit(targetName, amount, sender.getName(), reason);

        long newBal = service.getBalance(targetName);
        ChatUtil.sendMessage(sender, adminCfg.getString("messages.give-success-admin"),
                "{amount}", NumberDateUtil.formatInteger(amount),
                "{player}", targetName,
                "{new_balance}", NumberDateUtil.formatInteger(newBal));
        return true;
    }

    private boolean handleAdminTake(CommandSender sender, String[] args, FileConfiguration adminCfg) {
        if (!sender.hasPermission(adminCfg.getString("permissions.take", "enderrub.admin.take"))) {
            ChatUtil.sendMessage(sender, adminCfg.getString("messages.no-permission"));
            return true;
        }
        if (args.length < 3) {
            ChatUtil.sendMessages(sender, adminCfg.getStringList("messages.help"));
            return true;
        }

        String targetName = args[1];
        long amount = NumberDateUtil.parseStrictPositiveLong(args[2]);
        if (amount == -1L) {
            ChatUtil.sendMessage(sender, adminCfg.getString("messages.only-integers"));
            return true;
        }

        long current = service.getBalance(targetName);
        if (current < amount) {
            ChatUtil.sendMessage(sender, adminCfg.getString("messages.not-enough-to-take"),
                    "{player}", targetName, "{balance}", NumberDateUtil.formatInteger(current));
            return true;
        }

        String reason = args.length >= 4 ? joinArgs(args, 3) : adminCfg.getString("default-reasons.admin-take", "Изъятие администратором");
        service.withdraw(targetName, amount, sender.getName(), reason);

        long newBal = service.getBalance(targetName);
        ChatUtil.sendMessage(sender, adminCfg.getString("messages.take-success-admin"),
                "{amount}", NumberDateUtil.formatInteger(amount),
                "{player}", targetName,
                "{new_balance}", NumberDateUtil.formatInteger(newBal));
        return true;
    }

    private boolean handleAdminSet(CommandSender sender, String[] args, FileConfiguration adminCfg) {
        if (!sender.hasPermission(adminCfg.getString("permissions.set", "enderrub.admin.set"))) {
            ChatUtil.sendMessage(sender, adminCfg.getString("messages.no-permission"));
            return true;
        }
        if (args.length < 3) {
            ChatUtil.sendMessages(sender, adminCfg.getStringList("messages.help"));
            return true;
        }

        String targetName = args[1];
        long amount = args[2].equals("0") ? 0L : NumberDateUtil.parseStrictPositiveLong(args[2]);
        if (amount == -1L) {
            ChatUtil.sendMessage(sender, adminCfg.getString("messages.only-integers"));
            return true;
        }

        String reason = args.length >= 4 ? joinArgs(args, 3) : adminCfg.getString("default-reasons.admin-set", "Установка баланса");
        service.setBalance(targetName, amount, sender.getName(), reason);

        ChatUtil.sendMessage(sender, adminCfg.getString("messages.set-success-admin"),
                "{amount}", NumberDateUtil.formatInteger(amount),
                "{player}", targetName);
        return true;
    }

    private boolean handleAdminInspect(CommandSender sender, String[] args, FileConfiguration adminCfg) {
        if (!sender.hasPermission(adminCfg.getString("permissions.inspect", "enderrub.admin.inspect"))) {
            ChatUtil.sendMessage(sender, adminCfg.getString("messages.no-permission"));
            return true;
        }
        if (args.length < 2) {
            ChatUtil.sendMessages(sender, adminCfg.getStringList("messages.help"));
            return true;
        }

        String targetName = args[1];
        int page = 1;
        if (args.length >= 3) {
            long parsed = NumberDateUtil.parseStrictPositiveLong(args[2]);
            if (parsed != -1L) page = (int) parsed;
        }

        service.sendBalanceAndHistoryPage(sender, targetName, page);
        return true;
    }

    private boolean handleAdminReload(CommandSender sender, FileConfiguration adminCfg) {
        if (!sender.hasPermission(adminCfg.getString("permissions.reload", "enderrub.admin.reload"))) {
            ChatUtil.sendMessage(sender, adminCfg.getString("messages.no-permission"));
            return true;
        }
        extension.reloadAllConfigs();
        ChatUtil.sendMessage(sender, adminCfg.getString("messages.reload-success"));
        return true;
    }

    private String joinArgs(String[] args, int start) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            sb.append(args[i]).append(i + 1 < args.length ? " " : "");
        }
        return sb.toString();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        FileConfiguration adminCfg = extension.getCustomConfig("commands/admin-command.yml").get();
        boolean isAdmin = sender.hasPermission(adminCfg.getString("permissions.give", "enderrub.admin.give"));

        if (args.length == 1) {
            if (!isAdmin) return Arrays.asList("1", "2", "3");
            return Arrays.asList("give", "take", "set", "history", "reload", "1", "2").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && isAdmin) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 3 && isAdmin && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("take") || args[0].equalsIgnoreCase("set"))) {
            return Arrays.asList("10", "50", "100", "500", "1000");
        }
        return Collections.emptyList();
    }
}