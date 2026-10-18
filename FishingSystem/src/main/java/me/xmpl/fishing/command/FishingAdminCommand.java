package me.xmpl.fishing.command;

import me.xmpl.fishing.FishingSystemExtension;
import me.xmpl.lib.hook.WorldEditHook;
import me.xmpl.lib.util.ChatUtil;
import me.xmpl.lib.util.CuboidBox;
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

public class FishingAdminCommand implements CommandExecutor, TabCompleter {
    private final FishingSystemExtension extension;

    public FishingAdminCommand(FishingSystemExtension extension) {
        this.extension = extension;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        FileConfiguration cmdCfg = extension.getCustomConfig("commands/fishing.yml").get();
        String adminPerm = extension.getConfig().getString("permissions.admin", "fishingsystem.admin");

        if (!sender.hasPermission(adminPerm)) {
            ChatUtil.sendMessage(sender, cmdCfg.getString("messages.no-permission"));
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            ChatUtil.sendMessages(sender, cmdCfg.getStringList("messages.help-admin"));
            return true;
        }

        String sub = args[0].toLowerCase();

        // 1. Добавление бонусного региона x2: /fishingsystem addregion <Название>
        if ((sub.equals("addregion") || sub.equals("addrg")) && args.length >= 2) {
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

        // 2. Удаление бонусного региона: /fishingsystem removeregion <Название>
        if ((sub.equals("removeregion") || sub.equals("removerg")) && args.length >= 2) {
            String rgName = args[1];
            if (extension.getRegionStorage().removeRegion(rgName)) {
                ChatUtil.sendMessage(sender, cmdCfg.getString("messages.region-removed"), "{region}", rgName);
            } else {
                ChatUtil.sendMessage(sender, cmdCfg.getString("messages.region-not-found"), "{region}", rgName);
            }
            return true;
        }

        // 3. Список регионов: /fishingsystem listregions
        if (sub.equals("listregions") || sub.equals("listrg")) {
            ChatUtil.sendMessage(sender, "&3Бонусные регионы рыбалки x2 (&b" + extension.getRegionStorage().getRegions().size() + "&3):");
            for (CuboidBox box : extension.getRegionStorage().getRegions()) {
                ChatUtil.sendMessage(sender, "&8- &b" + box.getName() + " &7(Мир: " + box.getWorldName() + ")");
            }
            return true;
        }

        // 4. Перезагрузка: /fishingsystem reload
        if (sub.equals("reload")) {
            extension.reloadAllConfigs();
            extension.getRegionStorage().reload();
            extension.getGeneratorService().reload();
            extension.getStatsStorage().reload();
            ChatUtil.sendMessage(sender, cmdCfg.getString("messages.reload-success"));
            return true;
        }

        ChatUtil.sendMessages(sender, cmdCfg.getStringList("messages.help-admin"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String adminPerm = extension.getConfig().getString("permissions.admin", "fishingsystem.admin");
        if (!sender.hasPermission(adminPerm)) return Collections.emptyList();

        if (args.length == 1) {
            return Arrays.asList("addregion", "removeregion", "listregions", "reload", "help").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("removeregion") || args[0].equalsIgnoreCase("removerg"))) {
            return extension.getRegionStorage().getRegions().stream()
                    .map(CuboidBox::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}