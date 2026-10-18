package me.xmpl.nexuschest.command;

import me.xmpl.lib.hook.WorldEditHook;
import me.xmpl.lib.util.ChatUtil;
import me.xmpl.lib.util.CuboidBox;
import me.xmpl.lib.util.TimeUtil;
import me.xmpl.nexuschest.NexusChestExtension;
import org.bukkit.Material;
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
import java.util.Set;
import java.util.stream.Collectors;

public class NexusAdminCommand implements CommandExecutor, TabCompleter {
    private final NexusChestExtension extension;

    public NexusAdminCommand(NexusChestExtension extension) {
        this.extension = extension;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        FileConfiguration cmdCfg = extension.getCustomConfig("commands/nchest.yml").get();
        String adminPerm = extension.getConfig().getString("permissions.admin", "nexuschest.admin");

        if (!sender.hasPermission(adminPerm)) {
            ChatUtil.sendMessage(sender, cmdCfg.getString("messages.no-permission"));
            return true;
        }

        // Если ввели просто /nchest без аргументов — сразу открываем двойной сундук настройки лута!
        if (args.length == 0) {
            if (sender instanceof Player) {
                extension.getLootPoolStorage().openLootEditorGui((Player) sender);
            } else {
                ChatUtil.sendMessages(sender, cmdCfg.getStringList("messages.help"));
            }
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "loot":
            case "items": {
                if (!(sender instanceof Player)) return true;
                extension.getLootPoolStorage().openLootEditorGui((Player) sender);
                return true;
            }
            case "setchest1":
            case "setchest2": {
                if (!(sender instanceof Player)) return true;
                Player player = (Player) sender;
                int num = sub.endsWith("1") ? 1 : 2;

                Block target = player.getTargetBlock((Set<Material>) null, 6);
                if (target == null || (target.getType() != Material.CHEST && target.getType() != Material.TRAPPED_CHEST)) {
                    ChatUtil.sendMessage(player, cmdCfg.getString("messages.look-at-chest"));
                    return true;
                }

                extension.getArenaStorage().setChestLocation(num, target.getLocation());
                extension.getHologramService().rebuildPackets();
                ChatUtil.sendMessage(player, cmdCfg.getString("messages.chest-set"),
                        "{number}", num,
                        "{x}", target.getX(), "{y}", target.getY(), "{z}", target.getZ());
                return true;
            }
            case "setregion":
            case "setrg": {
                if (!(sender instanceof Player)) return true;
                Player player = (Player) sender;
                CuboidBox box = WorldEditHook.getPlayerSelection(player, "nexus_arena");
                if (box == null) {
                    ChatUtil.sendMessage(player, cmdCfg.getString("messages.no-worldedit"));
                    return true;
                }
                extension.getArenaStorage().saveArenaRegion(box);
                ChatUtil.sendMessage(player, cmdCfg.getString("messages.region-set"));
                return true;
            }
            case "setenter": {
                if (!(sender instanceof Player)) return true;
                Player player = (Player) sender;
                extension.getArenaStorage().setEnterLocation(player.getLocation());
                ChatUtil.sendMessage(player, cmdCfg.getString("messages.enter-set"));
                return true;
            }
            case "setspawn": {
                if (!(sender instanceof Player)) return true;
                Player player = (Player) sender;
                extension.getArenaStorage().setExpelSpawnLocation(player.getLocation());
                ChatUtil.sendMessage(player, cmdCfg.getString("messages.spawn-set"));
                return true;
            }
            case "start": {
                extension.getEventManager().startEvent();
                ChatUtil.sendMessage(sender, cmdCfg.getString("messages.force-started"));
                return true;
            }
            case "stop": {
                extension.getEventManager().stopEvent();
                ChatUtil.sendMessage(sender, cmdCfg.getString("messages.force-stopped"));
                return true;
            }
            case "status": {
                boolean running = extension.getEventManager().isEventRunning();
                if (running) {
                    ChatUtil.sendMessage(sender, "&a[NexusChest] Статус: &2&lИДЕТ СЕЙЧАС &7| Код: &e"
                            + extension.getEventManager().getCurrentPinCode()
                            + " &7| Осталось: &e" + TimeUtil.formatDuration(extension.getEventManager().getActiveSecondsLeft() * 1000L));
                } else {
                    ChatUtil.sendMessage(sender, "&e[NexusChest] Статус: &6Ожидание &7| До старта: &f"
                            + TimeUtil.formatDuration(extension.getEventManager().getSecondsUntilStart() * 1000L));
                }
                return true;
            }
            case "reload": {
                extension.reloadAllConfigs();
                extension.getArenaStorage().reload();
                extension.getLootPoolStorage().reload();
                extension.getHologramService().rebuildPackets();
                ChatUtil.sendMessage(sender, cmdCfg.getString("messages.reload-success"));
                return true;
            }
            default:
                ChatUtil.sendMessages(sender, cmdCfg.getStringList("messages.help"));
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String adminPerm = extension.getConfig().getString("permissions.admin", "nexuschest.admin");
        if (!sender.hasPermission(adminPerm)) return Collections.emptyList();

        if (args.length == 1) {
            return Arrays.asList("loot", "setchest1", "setchest2", "setregion", "setenter", "setspawn", "start", "stop", "status", "reload", "help")
                    .stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}