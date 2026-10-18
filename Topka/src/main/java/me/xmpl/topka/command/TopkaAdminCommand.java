package me.xmpl.topka.command;

import me.xmpl.lib.hook.WorldEditHook;
import me.xmpl.lib.util.ChatUtil;
import me.xmpl.lib.util.CuboidBox;
import me.xmpl.lib.util.TimeUtil;
import me.xmpl.topka.TopkaExtension;
import me.xmpl.topka.model.PersistedTopkaState;
import me.xmpl.topka.model.TerrainOption;
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

public class TopkaAdminCommand implements CommandExecutor, TabCompleter {
    private static final List<String> DEFAULT_HELP = Arrays.asList(
            "&6&l=== Управление ивентом TOPKA (/topka) ===",
            "&e/topka setchest1 &7(или &e/topka setchest&7) - Выбрать рабочий сундук #1 (глядя на сундук)",
            "&e/topka setchest2 &7- Выбрать второй рабочий сундук #2",
            "&e/topka removechest2 &7- Убрать второй сундук (оставить один)",
            "&e/topka setholo1 / setholo2 &7- Перенести голограмму сундука #1/#2 на свою позицию",
            "&e/topka setvote &7- Поставить изумрудный блок и голограмму голосования за территорию",
            "&e/topka setregion &7- Установить PvP зону по выделению WorldEdit (или впиши регион в region-rules.yml)",
            "&e/topka loot &7- Открыть GUI пула предметов (27 слотов)",
            "&e/topka addterra <Название> &7- Сохранить выделенную WorldEdit территорию в пул",
            "&e/topka setterra <ID> &7- Принудительно поставить территорию",
            "&e/topka start &7- Принудительно запустить наполнение сундука",
            "&e/topka status &7- Статус таймера, стадии и сундуков",
            "&e/topka reload &7- Перезагрузить все конфиги"
    );

    private final TopkaExtension extension;

    public TopkaAdminCommand(TopkaExtension extension) {
        this.extension = extension;
    }

    private void sendHelp(CommandSender sender, FileConfiguration msgCfg) {
        List<String> help = msgCfg.getStringList("messages.help");
        if (help == null || help.isEmpty()) {
            help = DEFAULT_HELP;
        }
        ChatUtil.sendMessages(sender, help);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        FileConfiguration msgCfg = extension.getCustomConfig("messages.yml").get();
        String adminPerm = extension.getConfig().getString("permissions.admin", "topka.admin");

        if (!sender.hasPermission(adminPerm)) {
            ChatUtil.sendMessage(sender, msgCfg.getString("messages.no-permission", "&c[Topka] У вас нет прав администратора!"));
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender, msgCfg);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "setchest":
            case "setchest1": {
                if (!(sender instanceof Player)) return true;
                return handleSetChest((Player) sender, 1, msgCfg);
            }
            case "setchest2": {
                if (!(sender instanceof Player)) return true;
                return handleSetChest((Player) sender, 2, msgCfg);
            }
            case "removechest2":
            case "delchest2": {
                extension.getArenaStorage().removeChest2();
                ChatUtil.sendMessage(sender, msgCfg.getString(
                        "messages.chest2-removed",
                        "&e[Topka] Второй сундук отключен. Теперь работает только 1 сундук."
                ));
                return true;
            }
            case "setholo":
            case "setholo1": {
                if (!(sender instanceof Player)) return true;
                Player player = (Player) sender;
                extension.getArenaStorage().setCustomHoloLocation(1, player.getLocation());
                ChatUtil.sendMessage(player, msgCfg.getString(
                        "messages.holo-set",
                        "&a[Topka] Точка голограммы &e#{number} &aустановлена на вашей позиции!"
                ), "{number}", 1);
                return true;
            }
            case "setholo2": {
                if (!(sender instanceof Player)) return true;
                Player player = (Player) sender;
                extension.getArenaStorage().setCustomHoloLocation(2, player.getLocation());
                ChatUtil.sendMessage(player, msgCfg.getString(
                        "messages.holo-set",
                        "&a[Topka] Точка голограммы &e#{number} &aустановлена на вашей позиции!"
                ), "{number}", 2);
                return true;
            }
            case "setregion":
            case "setrg": {
                if (!(sender instanceof Player)) return true;
                Player player = (Player) sender;
                CuboidBox box = WorldEditHook.getPlayerSelection(player, "topka_pvp");
                if (box == null) {
                    ChatUtil.sendMessage(player, msgCfg.getString(
                            "messages.no-worldedit",
                            "&c[Topka] Сначала выделите территорию топориком WorldEdit (//wand)!"
                    ));
                    return true;
                }
                extension.getArenaStorage().savePvpRegion(box);
                ChatUtil.sendMessage(player, msgCfg.getString(
                        "messages.region-set",
                        "&a[Topka] PvP-регион Топки успешно сохранен!"
                ));
                return true;
            }
            case "loot":
            case "items": {
                if (!(sender instanceof Player)) return true;
                extension.getLootPoolStorage().openLootEditorGui((Player) sender);
                return true;
            }
            case "addterra": {
                if (!(sender instanceof Player)) return true;
                Player player = (Player) sender;
                if (args.length < 2) {
                    ChatUtil.sendMessage(player, "&cИспользование: &e/topka addterra <Название (например &fStandart)>");
                    return true;
                }
                CuboidBox selection = WorldEditHook.getPlayerSelection(player, "temp_terra");
                if (selection == null) {
                    ChatUtil.sendMessage(player, msgCfg.getString(
                            "messages.no-worldedit",
                            "&c[Topka] Сначала выделите территорию топориком WorldEdit (//wand)!"
                    ));
                    return true;
                }
                String displayName = joinArgs(args, 1);
                TerrainOption created = extension.getTerrainStorage().saveNewTerrainFromSelection(displayName, selection);
                if (created != null) {
                    int blockCount = (selection.getMaxX() - selection.getMinX() + 1)
                            * (selection.getMaxY() - selection.getMinY() + 1)
                            * (selection.getMaxZ() - selection.getMinZ() + 1);
                    extension.getVotingService().resetVotingRoundAndRollNew();
                    ChatUtil.sendMessage(player, msgCfg.getString(
                            "messages.terra-added",
                            "&a[Topka] Территория {display} &7(ID: &e{id}&7, блоков: &f{blocks}&7) &aуспешно сохранена!"
                    ), "{display}", created.getDisplayName(), "{id}", created.getId(), "{blocks}", blockCount);
                }
                return true;
            }
            case "setterra": {
                if (args.length < 2) {
                    ChatUtil.sendMessage(sender, "&cИспользование: &e/topka setterra <ID>");
                    return true;
                }
                TerrainOption opt = extension.getTerrainStorage().getTerrain(args[1]);
                if (opt == null || !extension.getTerrainStorage().pasteTerrain(opt.getId())) {
                    ChatUtil.sendMessage(sender, msgCfg.getString(
                            "messages.terra-not-found",
                            "&c[Topka] Территория &e{id} &cне найдена!"
                    ), "{id}", args[1]);
                    return true;
                }
                ChatUtil.sendMessage(sender, msgCfg.getString(
                        "messages.terra-pasted",
                        "&a[Topka] Территория {display} &aпринудительно установлена на арену!"
                ), "{display}", opt.getDisplayName());
                return true;
            }
            case "setvote": {
                if (!(sender instanceof Player)) return true;
                Player player = (Player) sender;
                extension.getArenaStorage().setVoteEmeraldBlockLocation(player.getLocation());
                extension.getVotingHologramService().updateVotingHologram();
                ChatUtil.sendMessage(player, msgCfg.getString(
                        "messages.vote-block-set",
                        "&a[Topka] Изумрудный блок и голограмма голосования установлены на вашей позиции!"
                ));
                return true;
            }
            case "start": {
                extension.getLifecycleService().transitionToFillingStage();
                ChatUtil.sendMessage(sender, msgCfg.getString(
                        "messages.force-started",
                        "&a[Topka] Принудительно запущено наполнение сундука!"
                ));
                return true;
            }
            case "status": {
                PersistedTopkaState st = extension.getLifecycleService().getState();
                String wgRg = extension.getCustomConfig("region-rules.yml").get().getString("region.worldguard-region", "topka");
                ChatUtil.sendMessage(sender, "&6&l=== Статус TOPKA ===");
                ChatUtil.sendMessage(sender, "&7Стадия: &e" + st.stage.name()
                        + " &8| &7Таймер: &f" + TimeUtil.formatDuration(st.remainingSeconds * 1000L));
                ChatUtil.sendMessage(sender, "&7Режим сундуков: &a" + (extension.getArenaStorage().hasTwoChests() ? "2 сундука (#1 и #2)" : "1 сундук")
                        + " &8| &7WG Регион: &e" + wgRg
                        + " &8| &7Территория: &e" + (st.activeTerrainId.isEmpty() ? "Standart" : st.activeTerrainId));
                return true;
            }
            case "reload": {
                extension.reloadAllConfigs();
                extension.getArenaStorage().reload();
                extension.getLootPoolStorage().reload();
                extension.getTerrainStorage().reload();
                extension.getVotingService().resetVotingRoundAndRollNew();
                ChatUtil.sendMessage(sender, msgCfg.getString(
                        "messages.reload-success",
                        "&a[Topka] Все конфигурации Topka успешно перезагружены!"
                ));
                return true;
            }
            default:
                sendHelp(sender, msgCfg);
                return true;
        }
    }

    private boolean handleSetChest(Player player, int number, FileConfiguration msgCfg) {
        Block target = player.getTargetBlock((Set<Material>) null, 6);
        if (target == null || (target.getType() != Material.CHEST && target.getType() != Material.TRAPPED_CHEST)) {
            ChatUtil.sendMessage(player, msgCfg.getString(
                    "messages.look-at-chest",
                    "&c[Topka] Посмотрите вплотную на сундук (CHEST или TRAPPED_CHEST)!"
            ));
            return true;
        }
        extension.getArenaStorage().setChestLocation(number, target.getLocation());
        ChatUtil.sendMessage(player, msgCfg.getString(
                "messages.chest-set",
                "&a[Topka] Рабочий сундук &e#{number} &aустановлен на &f{x}, {y}, {z}&a! Голограмма привязана к сундуку."
        ), "{number}", number, "{x}", target.getX(), "{y}", target.getY(), "{z}", target.getZ());
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
        String adminPerm = extension.getConfig().getString("permissions.admin", "topka.admin");
        if (!sender.hasPermission(adminPerm)) return Collections.emptyList();

        if (args.length == 1) {
            return Arrays.asList("setchest1", "setchest2", "removechest2", "setholo1", "setholo2", "setregion",
                            "setvote", "loot", "addterra", "setterra", "start", "status", "reload", "help").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("setterra")) {
            return extension.getTerrainStorage().getAllTerrains().stream()
                    .map(TerrainOption::getId)
                    .filter(id -> id.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}