package me.xmpl.killsystem.command;

import me.xmpl.killsystem.KillSystemExtension;
import me.xmpl.killsystem.model.TopSignEntry;
import me.xmpl.lib.util.ChatUtil;
import me.xmpl.lib.util.NumberDateUtil;
import me.xmpl.lib.util.SignBlockUtil;
import org.bukkit.block.Block;
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

public class KillSystemAdminCommand implements CommandExecutor, TabCompleter {
    private final KillSystemExtension extension;

    public KillSystemAdminCommand(KillSystemExtension extension) {
        this.extension = extension;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        FileConfiguration cmdCfg = extension.getCustomConfig("commands/killsystem.yml").get();
        String adminPerm = extension.getConfig().getString("permissions.admin", "killsystem.admin");

        if (!sender.hasPermission(adminPerm)) {
            ChatUtil.sendMessage(sender, cmdCfg.getString("messages.no-permission"));
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            ChatUtil.sendMessages(sender, cmdCfg.getStringList("messages.help-admin"));
            return true;
        }

        String sub = args[0].toLowerCase();

        // 1. Добавление таблички любого места топа: /killsystem addtab1 .. /killsystem addtab100 (или /killsystem addtab 100)
        if (sub.startsWith("addtab")) {
            if (!(sender instanceof Player)) return true;
            Player player = (Player) sender;

            String numberPart = sub.substring("addtab".length()).trim();
            if (numberPart.isEmpty() && args.length >= 2) {
                numberPart = args[1].trim();
            }

            long rankLong = NumberDateUtil.parseStrictPositiveLong(numberPart);
            if (rankLong == -1L || rankLong > 100000L) {
                ChatUtil.sendMessage(player, cmdCfg.getString("messages.invalid-rank-number"));
                return true;
            }

            Block targetSign = SignBlockUtil.getTargetSign(player, 6);
            if (targetSign == null) {
                ChatUtil.sendMessage(player, cmdCfg.getString("messages.look-at-sign"));
                return true;
            }

            int rank = (int) rankLong;
            TopSignEntry saved = extension.getSignStorage().saveSign(targetSign.getLocation(), rank);
            extension.getSignUpdaterService().updateSingleSign(saved, extension.getDatabaseStorage().getSortedTopList());

            ChatUtil.sendMessage(player, cmdCfg.getString("messages.sign-added"), "{rank}", rank);
            return true;
        }

        // 2. Удаление таблички под взглядом: /killsystem removetab
        if (sub.equals("removetab") || sub.equals("deltab")) {
            if (!(sender instanceof Player)) return true;
            Player player = (Player) sender;

            Block targetSign = SignBlockUtil.getTargetSign(player, 6);
            if (targetSign == null) {
                ChatUtil.sendMessage(player, cmdCfg.getString("messages.look-at-sign"));
                return true;
            }

            TopSignEntry removed = extension.getSignStorage().removeSignAt(targetSign.getLocation());
            if (removed == null) {
                ChatUtil.sendMessage(player, cmdCfg.getString("messages.not-a-top-sign"));
            } else {
                SignBlockUtil.updateSignLines(targetSign.getLocation(), "", "", "", "");
                ChatUtil.sendMessage(player, cmdCfg.getString("messages.sign-removed"), "{rank}", removed.getRank());
            }
            return true;
        }

        // 3. Принудительное обновление всех табличек: /killsystem update
        if (sub.equals("update")) {
            int count = extension.getSignUpdaterService().updateAllSigns();
            ChatUtil.sendMessage(sender, cmdCfg.getString("messages.force-updated"), "{count}", count);
            return true;
        }

        // 4. Перезагрузка конфигов: /killsystem reload
        if (sub.equals("reload")) {
            extension.reloadAllConfigs();
            extension.getSignStorage().reload();
            extension.getSignUpdaterService().updateAllSigns();
            ChatUtil.sendMessage(sender, cmdCfg.getString("messages.reload-success"));
            return true;
        }

        ChatUtil.sendMessages(sender, cmdCfg.getStringList("messages.help-admin"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String adminPerm = extension.getConfig().getString("permissions.admin", "killsystem.admin");
        if (!sender.hasPermission(adminPerm)) return Collections.emptyList();

        if (args.length == 1) {
            return Arrays.asList("addtab1", "addtab2", "addtab3", "addtab10", "addtab100", "removetab", "update", "reload").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}