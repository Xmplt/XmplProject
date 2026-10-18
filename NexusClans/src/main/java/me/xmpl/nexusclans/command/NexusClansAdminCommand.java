package me.xmpl.nexusclans.command;

import me.xmpl.lib.hook.WorldEditHook;
import me.xmpl.lib.util.ChatUtil;
import me.xmpl.lib.util.CuboidBox;
import me.xmpl.lib.util.SignBlockUtil;
import me.xmpl.nexusclans.NexusClansExtension;
import me.xmpl.nexusclans.model.ClanData;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class NexusClansAdminCommand implements CommandExecutor, TabCompleter {
    private final NexusClansExtension extension;

    public NexusClansAdminCommand(NexusClansExtension extension) {
        this.extension = extension;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        FileConfiguration admCfg = extension.getCustomConfig("commands/admin-messages.yml").get();
        String perm = extension.getConfig().getString("permissions.admin", "nexusclans.admin");

        if (!sender.hasPermission(perm)) {
            ChatUtil.sendMessage(sender, admCfg.getString("messages.no-permission"));
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            ChatUtil.sendMessages(sender, admCfg.getStringList("messages.help"));
            return true;
        }

        String sub = args[0].toLowerCase();

        // 1. Установка табличек Топ-1 / Топ-2 / Топ-3: /nexusclans settab1, settab2, settab3
        if (sub.equals("settab1") || sub.equals("settab2") || sub.equals("settab3")) {
            if (!(sender instanceof Player)) return true;
            Player player = (Player) sender;
            Block signBlock = SignBlockUtil.getTargetSign(player, 6);
            if (signBlock == null) {
                ChatUtil.sendMessage(player, admCfg.getString("messages.look-at-sign"));
                return true;
            }
            int rank = Integer.parseInt(sub.substring("settab".length()));
            extension.getTopSignAndBoardService().setTopSign(rank, signBlock.getLocation());
            ChatUtil.sendMessage(player, admCfg.getString("messages.sign-set"), "{rank}", rank);
            return true;
        }

        // 2. Добавление и удаление региона скорборда Топ-10 кланов: /nexusclans addrg <название>, removerg <название>
        if (sub.equals("addrg") && args.length >= 2) {
            if (!(sender instanceof Player)) return true;
            Player player = (Player) sender;
            CuboidBox box = WorldEditHook.getPlayerSelection(player, args[1]);
            if (box == null) {
                ChatUtil.sendMessage(player, admCfg.getString("messages.no-worldedit"));
                return true;
            }
            extension.getTopSignAndBoardService().saveBoardRegion(box);
            ChatUtil.sendMessage(player, admCfg.getString("messages.region-added"), "{region}", args[1]);
            return true;
        }

        if (sub.equals("removerg") && args.length >= 2) {
            if (extension.getTopSignAndBoardService().removeBoardRegion(args[1])) {
                ChatUtil.sendMessage(sender, admCfg.getString("messages.region-removed"), "{region}", args[1]);
            } else {
                ChatUtil.sendMessage(sender, "&cРегион &e" + args[1] + " &cне найден!");
            }
            return true;
        }

        // 3. Настройка боевой арены 5x5: /nexusclans setwarregion, setspectatorpoint, addclan1point, addclan2point, clearpoints
        if (sub.equals("setwarregion")) {
            if (!(sender instanceof Player)) return true;
            Player player = (Player) sender;
            CuboidBox box = WorldEditHook.getPlayerSelection(player, "war_arena");
            if (box == null) {
                ChatUtil.sendMessage(player, admCfg.getString("messages.no-worldedit"));
                return true;
            }
            extension.getWarArenaStorage().saveArenaRegionAndSnapshot(box);
            ChatUtil.sendMessage(player, admCfg.getString("messages.war-arena-region-set"));
            return true;
        }

        if (sub.equals("setspectatorpoint")) {
            if (!(sender instanceof Player)) return true;
            Player player = (Player) sender;
            extension.getWarArenaStorage().setSpectatorPoint(player.getLocation());
            ChatUtil.sendMessage(player, admCfg.getString("messages.spectator-point-set"));
            return true;
        }

        if (sub.equals("addclan1point") || sub.equals("addclan2point")) {
            if (!(sender instanceof Player)) return true;
            Player player = (Player) sender;
            int team = sub.contains("1") ? 1 : 2;
            int idx = extension.getWarArenaStorage().addClanSpawnPoint(team, player.getLocation());
            if (idx == -1) {
                ChatUtil.sendMessage(player, admCfg.getString("messages.clan-spawn-limit"), "{team}", team);
            } else {
                ChatUtil.sendMessage(player, admCfg.getString("messages.clan-spawn-added"), "{index}", idx, "{team}", team);
            }
            return true;
        }

        if (sub.equals("clearpoints")) {
            extension.getWarArenaStorage().clearAllSpawnPoints();
            ChatUtil.sendMessage(sender, admCfg.getString("messages.points-cleared"));
            return true;
        }

        // 4. Включение / выключение любых модулей плагина (включая money-points-system и smart-points-system)
        if (sub.equals("module") && args.length >= 3) {
            String modName = args[1].toLowerCase();
            boolean enable = args[2].equalsIgnoreCase("on") || args[2].equalsIgnoreCase("true");
            if (!extension.getModuleService().setModuleEnabled(modName, enable)) {
                ChatUtil.sendMessage(sender, "&cНеизвестный модуль! Доступные: &e"
                        + String.join(", ", extension.getModuleService().getAvailableModules()));
                return true;
            }
            ChatUtil.sendMessage(sender, admCfg.getString("messages.module-toggled"),
                    "{module}", modName, "{state}", enable ? "&aВКЛЮЧЕН" : "&cВЫКЛЮЧЕН");
            extension.getGlowAndRideService().refreshAllOnlineGlows();
            return true;
        }

        // 5. Админ-управление очками, казной, кулдаунами и удалением кланов
        if (sub.equals("givepoints") && args.length >= 3) {
            ClanData clan = extension.getMainStorage().getClanByNameOrTag(args[1]);
            if (clan == null) {
                ChatUtil.sendMessage(sender, "&cКлан не найден!");
                return true;
            }
            try {
                int delta = Integer.parseInt(args[2]);
                extension.getSmartPointsService().addPointsAndLog(clan, null, delta,
                        "&fАдминистратор изменил очки клана на &e" + (delta >= 0 ? "+" + delta : delta));
                ChatUtil.sendMessage(sender, admCfg.getString("messages.admin-points-changed"),
                        "{clan}", clan.getRawColoredTag(), "{points}", clan.getTotalPoints());
            } catch (NumberFormatException e) {
                ChatUtil.sendMessage(sender, "&cУкажите целое число очков (например 50 или -25)!");
            }
            return true;
        }

        if (sub.equals("setbalance") && args.length >= 3) {
            ClanData clan = extension.getMainStorage().getClanByNameOrTag(args[1]);
            if (clan == null) {
                ChatUtil.sendMessage(sender, "&cКлан не найден!");
                return true;
            }
            try {
                double bal = Double.parseDouble(args[2]);
                clan.setBalance(bal);
                extension.getMainStorage().saveClanAsync(clan);
                ChatUtil.sendMessage(sender, admCfg.getString("messages.admin-balance-changed"),
                        "{clan}", clan.getRawColoredTag(), "{balance}", (long) clan.getBalance());
            } catch (NumberFormatException e) {
                ChatUtil.sendMessage(sender, "&cУкажите корректную сумму!");
            }
            return true;
        }

        if (sub.equals("deleteclan") && args.length >= 2) {
            ClanData clan = extension.getMainStorage().getClanByNameOrTag(args[1]);
            if (clan == null) {
                ChatUtil.sendMessage(sender, "&cКлан не найден!");
                return true;
            }
            String tag = clan.getRawColoredTag();
            extension.getClanCoreService().deleteClanFullCleanup(clan.getClanKey());
            ChatUtil.sendMessage(sender, admCfg.getString("messages.admin-clan-deleted"), "{clan}", tag);
            return true;
        }

        if (sub.equals("clearwarcd") && args.length >= 2) {
            ClanData clan = extension.getMainStorage().getClanByNameOrTag(args[1]);
            if (clan == null) {
                ChatUtil.sendMessage(sender, "&cКлан не найден!");
                return true;
            }
            clan.setWarCooldownUntilMillis(0L);
            extension.getMainStorage().saveClanAsync(clan);
            ChatUtil.sendMessage(sender, "&aКулдаун клановой войны для клана " + clan.getRawColoredTag() + " &aсброшен!");
            return true;
        }

        if (sub.equals("reload")) {
            extension.reloadAllConfigs();
            extension.getMainStorage().loadAll();
            extension.getPointsStorage().loadAll();
            extension.getAlliancesStorage().loadAll();
            extension.getChestStorage().loadAll();
            extension.getTopSignAndBoardService().reload();
            extension.getWarArenaStorage().reload();
            extension.getTopSignAndBoardService().updateAllTopSigns();
            ChatUtil.sendMessage(sender, admCfg.getString("messages.reload-success"));
            return true;
        }

        ChatUtil.sendMessages(sender, admCfg.getStringList("messages.help"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String perm = extension.getConfig().getString("permissions.admin", "nexusclans.admin");
        if (!sender.hasPermission(perm)) return Collections.emptyList();

        if (args.length == 1) {
            return Arrays.asList(
                    "settab1", "settab2", "settab3", "addrg", "removerg",
                    "setwarregion", "setspectatorpoint", "addclan1point", "addclan2point", "clearpoints",
                    "module", "givepoints", "setbalance", "deleteclan", "clearwarcd", "reload", "help"
            ).stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("module")) {
                return new ArrayList<>(extension.getModuleService().getAvailableModules()).stream()
                        .filter(m -> m.startsWith(args[1].toLowerCase())).collect(Collectors.toList());
            }
            if (sub.equals("removerg")) {
                return extension.getTopSignAndBoardService().getBoardRegions().stream()
                        .map(CuboidBox::getName)
                        .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
            if (sub.equals("givepoints") || sub.equals("setbalance") || sub.equals("deleteclan") || sub.equals("clearwarcd")) {
                return extension.getMainStorage().getAllClans().stream()
                        .map(ClanData::getClanKey)
                        .filter(k -> k.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("module")) {
            return Arrays.asList("on", "off");
        }
        return Collections.emptyList();
    }
}