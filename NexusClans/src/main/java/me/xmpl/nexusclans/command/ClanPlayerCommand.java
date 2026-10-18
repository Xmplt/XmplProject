package me.xmpl.nexusclans.command;

import me.xmpl.lib.util.ChatUtil;
import me.xmpl.lib.util.NumberDateUtil;
import me.xmpl.nexusclans.NexusClansExtension;
import me.xmpl.nexusclans.model.ClanData;
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

public class ClanPlayerCommand implements CommandExecutor, TabCompleter {
    private final NexusClansExtension extension;

    public ClanPlayerCommand(NexusClansExtension extension) {
        this.extension = extension;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            extension.getClanCoreService().sendClanTopPage(sender, 1);
            return true;
        }
        Player player = (Player) sender;

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            extension.getClanCoreService().sendDynamicHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "create": {
                if (args.length < 2) {
                    ChatUtil.sendMessage(player, "&cИспользование: &e/clan create <Название>");
                    return true;
                }
                extension.getClanCoreService().createClan(player, joinArgs(args, 1));
                return true;
            }
            case "showtag": {
                extension.getClanCoreService().enableShowTag(player);
                return true;
            }
            case "rename": {
                if (args.length < 2) {
                    ChatUtil.sendMessage(player, "&cИспользование: &e/c rename <НовоеНазвание>");
                    return true;
                }
                extension.getClanCoreService().renameClan(player, joinArgs(args, 1));
                return true;
            }
            case "balance":
            case "bal": {
                extension.getClanCoreService().viewBalance(player);
                return true;
            }
            case "donate":
            case "deposit": {
                if (args.length < 2) {
                    ChatUtil.sendMessage(player, "&cИспользование: &e/clan donate <Сумма>");
                    return true;
                }
                long amount = NumberDateUtil.parseStrictPositiveLong(args[1]);
                if (amount <= 0) {
                    ChatUtil.sendMessage(player, "&cУкажите корректную положительную сумму!");
                    return true;
                }
                extension.getClanCoreService().donateToClan(player, amount);
                return true;
            }
            case "take":
            case "withdraw": {
                if (args.length < 2) {
                    ChatUtil.sendMessage(player, "&cИспользование: &e/c take <Сумма>");
                    return true;
                }
                long amount = NumberDateUtil.parseStrictPositiveLong(args[1]);
                if (amount <= 0) {
                    ChatUtil.sendMessage(player, "&cУкажите корректную положительную сумму!");
                    return true;
                }
                extension.getClanCoreService().takeFromClan(player, amount);
                return true;
            }
            case "pvpon": {
                extension.getClanCoreService().setClanPvp(player, true);
                return true;
            }
            case "pvpoff": {
                extension.getClanCoreService().setClanPvp(player, false);
                return true;
            }
            case "sitallow": {
                extension.getClanCoreService().toggleClanSitAllow(player);
                return true;
            }
            case "tpallow": {
                extension.getClanCoreService().toggleClanTpAllow(player);
                return true;
            }
            case "sethome": {
                extension.getClanCoreService().setClanHome(player);
                return true;
            }
            case "delhome": {
                extension.getClanCoreService().deleteClanHome(player);
                return true;
            }
            case "home": {
                extension.getClanCoreService().teleportToClanHome(player);
                return true;
            }
            case "bc":
            case "broadcast": {
                extension.getClanCoreService().broadcastCallToHome(player);
                return true;
            }
            case "tp": {
                if (args.length < 2) {
                    ChatUtil.sendMessage(player, "&cИспользование: &e/c tp <НикСоклановца>");
                    return true;
                }
                extension.getClanCoreService().teleportToClanmate(player, args[1]);
                return true;
            }
            case "teamtp": {
                if (args.length < 2) {
                    ChatUtil.sendMessage(player, "&cИспользование: &e/c teamtp <НикСоюзника>");
                    return true;
                }
                extension.getClanCoreService().teleportToAlly(player, args[1]);
                return true;
            }
            case "invite": {
                if (args.length < 2) {
                    ChatUtil.sendMessage(player, "&cИспользование: &e/c invite <Ник> &7(или &aaccept &7/ &cdeny&7)");
                    return true;
                }
                if (args[1].equalsIgnoreCase("accept")) {
                    extension.getClanCoreService().respondToInvite(player, true);
                } else if (args[1].equalsIgnoreCase("deny")) {
                    extension.getClanCoreService().respondToInvite(player, false);
                } else {
                    extension.getClanCoreService().sendInvite(player, args[1]);
                }
                return true;
            }
            case "addmoder": {
                if (args.length < 2) {
                    ChatUtil.sendMessage(player, "&cИспользование: &e/clan addmoder <Ник>");
                    return true;
                }
                extension.getClanCoreService().setModeratorRole(player, args[1], true);
                return true;
            }
            case "removemoder": {
                if (args.length < 2) {
                    ChatUtil.sendMessage(player, "&cИспользование: &e/clan removemoder <Ник>");
                    return true;
                }
                extension.getClanCoreService().setModeratorRole(player, args[1], false);
                return true;
            }
            case "leader": {
                if (args.length < 2) {
                    ChatUtil.sendMessage(player, "&cИспользование: &e/c leader <НикМодератора>");
                    return true;
                }
                extension.getClanCoreService().initiateLeaderTransfer(player, args[1]);
                return true;
            }
            case "leaderaccept": {
                if (args.length < 2) {
                    ChatUtil.sendMessage(player, "&cИспользование: &e/c leaderaccept <НикМодератора>");
                    return true;
                }
                extension.getClanCoreService().confirmLeaderTransfer(player, args[1]);
                return true;
            }
            case "remove": {
                boolean confirm = args.length >= 2 && args[1].equalsIgnoreCase("confirm");
                extension.getClanCoreService().handleRemoveClanCommand(player, confirm);
                return true;
            }
            case "kick": {
                if (args.length < 3) {
                    ChatUtil.sendMessage(player, extension.getClanCoreService().getMsgCfg().getString("messages.kick-usage"));
                    return true;
                }
                extension.getClanCoreService().kickMember(player, args[1], joinArgs(args, 2));
                return true;
            }
            case "leave": {
                extension.getClanCoreService().leaveClan(player);
                return true;
            }
            case "chest": {
                extension.getVirtualChestService().openClanChest(player);
                return true;
            }
            case "info": {
                String clanArg = args.length >= 2 ? joinArgs(args, 1) : null;
                extension.getClanCoreService().sendClanInfo(player, clanArg);
                return true;
            }
            case "online": {
                String clanArg = args.length >= 2 ? joinArgs(args, 1) : null;
                extension.getClanCoreService().sendClanOnlineList(player, clanArg);
                return true;
            }
            case "top": {
                int page = 1;
                if (args.length >= 2) {
                    long parsed = NumberDateUtil.parseStrictPositiveLong(args[1]);
                    if (parsed != -1L) page = (int) parsed;
                }
                extension.getClanCoreService().sendClanTopPage(player, page);
                return true;
            }
            case "log": {
                if (args.length == 1) {
                    extension.getClanCoreService().sendClanLogs(player, null, 1);
                    return true;
                }
                long pageIfNumber = NumberDateUtil.parseStrictPositiveLong(args[1]);
                if (pageIfNumber != -1L && args.length == 2) {
                    extension.getClanCoreService().sendClanLogs(player, null, (int) pageIfNumber);
                    return true;
                }
                String targetNick = args[1];
                int page = 1;
                if (args.length >= 3) {
                    long p = NumberDateUtil.parseStrictPositiveLong(args[2]);
                    if (p != -1L) page = (int) p;
                }
                extension.getClanCoreService().sendClanLogs(player, targetNick, page);
                return true;
            }
            case "glowon": {
                extension.getGlowAndRideService().setPersonalClanGlow(player, true);
                return true;
            }
            case "glowoff": {
                extension.getGlowAndRideService().setPersonalClanGlow(player, false);
                return true;
            }
            case "teamglowon": {
                extension.getGlowAndRideService().setPersonalAllyGlow(player, true);
                return true;
            }
            case "teamglowoff": {
                extension.getGlowAndRideService().setPersonalAllyGlow(player, false);
                return true;
            }
            case "team": {
                if (args.length < 2) {
                    ChatUtil.sendMessage(player, "&cИспользование: &e/c team <НазваниеКлана>");
                    return true;
                }
                extension.getAllianceService().sendAllianceRequest(player, joinArgs(args, 1));
                return true;
            }
            case "teamaccept": {
                extension.getAllianceService().respondToAllianceRequest(player, true);
                return true;
            }
            case "teamdeny": {
                extension.getAllianceService().respondToAllianceRequest(player, false);
                return true;
            }
            case "unteam": {
                if (args.length < 2) {
                    ChatUtil.sendMessage(player, "&cИспользование: &e/c unteam <НазваниеКлана>");
                    return true;
                }
                extension.getAllianceService().breakAlliance(player, joinArgs(args, 1));
                return true;
            }
            case "teampvpon": {
                extension.getAllianceService().setTeamPvp(player, true);
                return true;
            }
            case "teampvpoff": {
                extension.getAllianceService().setTeamPvp(player, false);
                return true;
            }
            case "teamsitallow": {
                extension.getAllianceService().toggleTeamSit(player);
                return true;
            }
            case "teamtpallow": {
                extension.getAllianceService().toggleTeamTp(player);
                return true;
            }
            // Подкоманды клановой войны 5 на 5
            case "war":
            case "warmembers":
            case "pvpready":
            case "warjoin": {
                extension.getWarService().handleClanWarSubcommand(player, sub, Arrays.copyOfRange(args, 1, args.length));
                return true;
            }
            default:
                extension.getClanCoreService().sendDynamicHelp(player);
                return true;
        }
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
        if (args.length == 1) {
            return Arrays.asList(
                    "help", "create", "info", "online", "top", "log", "balance", "donate", "take",
                    "chest", "home", "sethome", "delhome", "bc", "tp", "teamtp", "invite", "kick",
                    "leave", "showtag", "rename", "addmoder", "removemoder", "leader", "remove",
                    "pvpon", "pvpoff", "sitallow", "tpallow", "glowon", "glowoff", "teamglowon", "teamglowoff",
                    "team", "teamaccept", "teamdeny", "unteam", "teampvpon", "teampvpoff", "teamsitallow", "teamtpallow",
                    "war", "warmembers", "pvpready"
            ).stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("info") || sub.equals("online") || sub.equals("team") || sub.equals("unteam")) {
                return extension.getMainStorage().getAllClans().stream()
                        .map(ClanData::getClanKey)
                        .filter(k -> k.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}