package me.xmpl.airdrop.command;

import me.xmpl.airdrop.AirDropExtension;
import me.xmpl.airdrop.model.AirDropStage;
import me.xmpl.airdrop.model.PersistedAirDropState;
import me.xmpl.lib.util.ChatUtil;
import me.xmpl.lib.util.NumberDateUtil;
import me.xmpl.lib.util.TimeUtil;
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

public class AirDropAdminCommand implements CommandExecutor, TabCompleter {
    private final AirDropExtension extension;

    public AirDropAdminCommand(AirDropExtension extension) {
        this.extension = extension;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        FileConfiguration cmdCfg = extension.getCustomConfig("commands/airdrop.yml").get();
        String adminPerm = extension.getConfig().getString("permissions.admin", "airdropsystem.admin");

        if (!sender.hasPermission(adminPerm)) {
            ChatUtil.sendMessage(sender, cmdCfg.getString("messages.no-permission"));
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            ChatUtil.sendMessages(sender, cmdCfg.getStringList("messages.help"));
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "loot":
            case "edit":
            case "items": {
                if (!(sender instanceof Player)) return true;
                int page = 1;
                if (args.length >= 2) {
                    long parsed = NumberDateUtil.parseStrictPositiveLong(args[1]);
                    if (parsed != -1L) page = (int) parsed;
                }
                extension.getGuiService().openStoragePage((Player) sender, page);
                return true;
            }
            case "start":
            case "spawn": {
                extension.getLifecycleManager().spawnAirDropChestNow();
                ChatUtil.sendMessage(sender, cmdCfg.getString("messages.force-spawned"));
                return true;
            }
            case "stop":
            case "despawn": {
                extension.getLifecycleManager().despawnAndResetToIdle();
                ChatUtil.sendMessage(sender, cmdCfg.getString("messages.force-despawned"));
                return true;
            }
            case "tp":
            case "teleport": {
                if (!(sender instanceof Player)) return true;
                Player player = (Player) sender;
                PersistedAirDropState st = extension.getLifecycleManager().getState();
                if (st.stage == AirDropStage.IDLE || st.chestLocation == null) {
                    ChatUtil.sendMessage(player, cmdCfg.getString("messages.no-active-chest"));
                    return true;
                }
                player.teleport(st.chestLocation.clone().add(0.5, 1.0, 0.5));
                ChatUtil.sendMessage(player, cmdCfg.getString("messages.teleported-to-chest"));
                return true;
            }
            case "status":
            case "info": {
                PersistedAirDropState st = extension.getLifecycleManager().getState();
                ChatUtil.sendMessage(sender, "&2&l=== Статус AirDropSystem ===");
                ChatUtil.sendMessage(sender, "&7Стадия: &a" + st.stage.name()
                        + " &8| &7Таймер: &e" + TimeUtil.formatDuration(st.timerSeconds * 1000L));
                if (st.chestLocation != null) {
                    ChatUtil.sendMessage(sender, "&7Координаты: &f" + st.chestLocation.getBlockX() + ", "
                            + st.chestLocation.getBlockY() + ", " + st.chestLocation.getBlockZ()
                            + " &7(Мир: " + extension.getLifecycleManager().getWorldDisplayName(st.worldKey) + "&7)");
                    ChatUtil.sendMessage(sender, "&7Фаза: &e" + st.currentPhase + "/" + st.totalPhasesForRound);
                }
                return true;
            }
            case "reload": {
                extension.reloadAllConfigs();
                extension.getLootStorage().reload();
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
        String adminPerm = extension.getConfig().getString("permissions.admin", "airdropsystem.admin");
        if (!sender.hasPermission(adminPerm)) return Collections.emptyList();

        if (args.length == 1) {
            return Arrays.asList("loot", "start", "stop", "tp", "status", "reload", "help").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}