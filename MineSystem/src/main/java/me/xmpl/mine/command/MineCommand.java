package me.xmpl.mine.command;

import me.xmpl.lib.hook.WorldEditHook;
import me.xmpl.lib.util.ChatUtil;
import me.xmpl.lib.util.CuboidBox;
import me.xmpl.mine.MineSystemExtension;
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

public class MineCommand implements CommandExecutor, TabCompleter {
    private final MineSystemExtension extension;

    public MineCommand(MineSystemExtension extension) {
        this.extension = extension;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        FileConfiguration cmdCfg = extension.getCustomConfig("commands/mine.yml").get();
        String adminPerm = extension.getConfig().getString("permissions.admin", "minesystem.admin");
        boolean isAdmin = sender.hasPermission(adminPerm);

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            ChatUtil.sendMessages(sender, cmdCfg.getStringList(isAdmin ? "messages.help-admin" : "messages.help-player"));
            return true;
        }

        String sub = args[0].toLowerCase();

        // 1. Продажа руды: /mine sell
        if (sub.equals("sell")) {
            if (!(sender instanceof Player)) return true;
            Player player = (Player) sender;

            if (extension.getConfig().getBoolean("permissions.require-sell-permission", false)) {
                String sellPerm = extension.getConfig().getString("permissions.sell", "minesystem.sell");
                if (!player.hasPermission(sellPerm)) {
                    ChatUtil.sendMessage(player, cmdCfg.getString("messages.no-permission"));
                    return true;
                }
            }

            extension.getEconomyService().sellAllOres(player);
            return true;
        }

        // Остальные подкоманды — только для администраторов
        if (!isAdmin) {
            ChatUtil.sendMessage(sender, cmdCfg.getString("messages.no-permission"));
            return true;
        }

        // 2. Создание формального региона шахты из WorldEdit: /mine addrg <Название>
        if (sub.equals("addrg") && args.length >= 2) {
            if (!(sender instanceof Player)) return true;
            Player player = (Player) sender;
            String rgName = args[1];

            CuboidBox box = WorldEditHook.getPlayerSelection(player, rgName);
            if (box == null) {
                ChatUtil.sendMessage(player, cmdCfg.getString("messages.no-worldedit-selection"));
                return true;
            }

            extension.getRegionStorage().saveRegion(box);
            ChatUtil.sendMessage(player, cmdCfg.getString("messages.region-added"), "{region}", rgName);
            return true;
        }

        // 3. Удаление региона шахты: /mine removerg <Название>
        if (sub.equals("removerg") && args.length >= 2) {
            String rgName = args[1];
            if (extension.getRegionStorage().removeRegion(rgName)) {
                ChatUtil.sendMessage(sender, cmdCfg.getString("messages.region-removed"), "{region}", rgName);
            } else {
                ChatUtil.sendMessage(sender, cmdCfg.getString("messages.region-not-found"), "{region}", rgName);
            }
            return true;
        }

        // 4. Список регионов: /mine listrg
        if (sub.equals("listrg")) {
            ChatUtil.sendMessage(sender, "&6Активные регионы шахты (&e" + extension.getRegionStorage().getRegions().size() + "&6):");
            for (CuboidBox box : extension.getRegionStorage().getRegions()) {
                ChatUtil.sendMessage(sender, "&8- &e" + box.getName() + " &7(Мир: " + box.getWorldName() + ")");
            }
            return true;
        }

        // 5. Перезагрузка: /mine reload
        if (sub.equals("reload")) {
            extension.reloadAllConfigs();
            extension.getRegionStorage().reload();
            ChatUtil.sendMessage(sender, cmdCfg.getString("messages.reload-success"));
            return true;
        }

        ChatUtil.sendMessages(sender, cmdCfg.getStringList("messages.help-admin"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String adminPerm = extension.getConfig().getString("permissions.admin", "minesystem.admin");
        boolean isAdmin = sender.hasPermission(adminPerm);

        if (args.length == 1) {
            List<String> base = isAdmin
                    ? Arrays.asList("sell", "addrg", "removerg", "listrg", "reload")
                    : Collections.singletonList("sell");
            return base.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && isAdmin && args[0].equalsIgnoreCase("removerg")) {
            return extension.getRegionStorage().getRegions().stream()
                    .map(CuboidBox::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}