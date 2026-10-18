package me.xmpl.nexusclans.service;

import me.xmpl.lib.util.ChatUtil;
import me.xmpl.nexusclans.NexusClansExtension;
import me.xmpl.nexusclans.model.ClanData;
import me.xmpl.nexusclans.model.ClanMember;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public class ClanSmartPointsService {
    private final NexusClansExtension extension;

    public ClanSmartPointsService(NexusClansExtension extension) {
        this.extension = extension;
    }

    /**
     * Универсальный метод изменения очков клана и личного вклада игрока с автоматической записью в /c log.
     */
    public void addPointsAndLog(ClanData clan, String playerName, int pointsDelta, String logMessage) {
        if (clan == null || pointsDelta == 0) return;

        clan.addTotalPoints(pointsDelta);
        extension.getMainStorage().saveClanAsync(clan);

        if (playerName != null && !playerName.isEmpty()) {
            ClanMember member = clan.getMember(playerName);
            if (member != null) {
                member.addContributedPoints(pointsDelta);
                extension.getMainStorage().saveMemberAsync(member);
            }
        }

        if (logMessage != null && !logMessage.isEmpty()) {
            extension.getLogsStorage().addLogAsync(clan.getClanKey(), playerName, pointsDelta, logMessage);
        }
    }

    /**
     * Обработка пополнения казны клана (/c donate): каждые 10 000$ (даже по частям) = +1 очко.
     */
    public int handleDepositPoints(ClanData clan, Player player, double amountDeposited) {
        if (!extension.getModuleService().isMoneyPointsEnabled()) return 0;

        FileConfiguration ptsCfg = extension.getCustomConfig("points-and-limits.yml").get();
        double stepMoney = ptsCfg.getDouble("money-points.deposit-step-money", 10000.0);
        int ptsPerStep = ptsCfg.getInt("money-points.points-per-deposit-step", 1);

        int earnedPoints = extension.getPointsStorage().accumulateDepositAndCalcPoints(
                clan.getClanKey(), player.getName(), amountDeposited, stepMoney, ptsPerStep
        );

        if (earnedPoints > 0) {
            String logMsg = "&fИгрок &e" + player.getName() + " &fзаработал &a+" + earnedPoints + " очк. &7(Вклад в казну)";
            addPointsAndLog(clan, player.getName(), earnedPoints, logMsg);
        }
        return earnedPoints;
    }

    /**
     * Обработка снятия денег главой (/c take): каждые 10 000$ (даже по частям) = -1 очко у главы и клана.
     */
    public int handleWithdrawPenaltyPoints(ClanData clan, Player leader, double amountWithdrawn) {
        if (!extension.getModuleService().isMoneyPointsEnabled()) return 0;

        FileConfiguration ptsCfg = extension.getCustomConfig("points-and-limits.yml").get();
        double stepMoney = ptsCfg.getDouble("money-points.withdraw-step-money", 10000.0);
        int ptsPerStep = ptsCfg.getInt("money-points.points-per-withdraw-step", 1);

        int lostPoints = extension.getPointsStorage().accumulateWithdrawAndCalcPenaltyPoints(
                clan.getClanKey(), leader.getName(), amountWithdrawn, stepMoney, ptsPerStep
        );

        if (lostPoints > 0) {
            String logMsg = "&fГлава &e" + leader.getName() + " &fпотерял &c-" + lostPoints + " очк. &7(Снятие с казны)";
            addPointsAndLog(clan, leader.getName(), -lostPoints, logMsg);
        }
        return lostPoints;
    }

    /**
     * Уникальные ПвП-очки при убийстве игрока из другого клана:
     * +1 очко убийце (1 раз за конкретную жертву для этого убийцы; другие соклановцы тоже могут получить по 1 разу).
     * -1 очко жертве (1 раз от каждого уникального убийцы из другого клана).
     */
    public void handleUniquePvpKillBetweenClans(Player killer, Player victim) {
        if (!extension.getModuleService().isSmartPointsEnabled()) return;
        if (killer == null || victim == null || killer.equals(victim)) return;

        ClanData killerClan = extension.getMainStorage().getPlayerClan(killer.getName());
        ClanData victimClan = extension.getMainStorage().getPlayerClan(victim.getName());

        if (killerClan == null || victimClan == null) return;
        if (killerClan.getClanKey().equalsIgnoreCase(victimClan.getClanKey())) return;

        // Проверяем уникальность пары (Убийца -> Жертва)
        if (!extension.getPointsStorage().tryRegisterUniqueClanKillPair(killer.getName(), victim.getName())) {
            return; // Этот убийца уже получал очко за этого игрока!
        }

        FileConfiguration ptsCfg = extension.getCustomConfig("points-and-limits.yml").get();
        int gain = ptsCfg.getInt("pvp-unique-points.killer-points-gain", 1);
        int loss = ptsCfg.getInt("pvp-unique-points.victim-points-loss", 1);

        if (gain > 0) {
            String kLog = "&fИгрок &e" + killer.getName() + " &fзаработал &a+" + gain + " очк. &7(Убийство &f" + victim.getName() + "&7)";
            addPointsAndLog(killerClan, killer.getName(), gain, kLog);
            ChatUtil.sendMessage(killer, "&a[Клан] &fВы принесли клану &a+" + gain + " очко &fза убийство игрока &e" + victim.getName() + "&f!");
        }

        if (loss > 0) {
            String vLog = "&fИгрок &e" + victim.getName() + " &fпотерял &c-" + loss + " очк. &7(Убит игроком &f" + killer.getName() + "&7)";
            addPointsAndLog(victimClan, victim.getName(), -loss, vLog);
            ChatUtil.sendMessage(victim, "&c[Клан] &fВы потеряли &c-" + loss + " очко &fклана от руки &e" + killer.getName() + "&f!");
        }
    }

    /**
     * Начисляет очки за первое взятие предмета / активацию ивента (AirDrop, NexusChest, TOPKA)
     * с масштабированием от 50 до 75 очков в зависимости от онлайна сервера.
     * Если первым взял игрок БЕЗ клана — очки никому не начисляются!
     */
    public void handleFirstEventAction(Player firstPlayer, String eventConfigKey, String eventDisplayName) {
        if (!extension.getModuleService().isSmartPointsEnabled() || firstPlayer == null) return;

        ClanData clan = extension.getMainStorage().getPlayerClan(firstPlayer.getName());
        if (clan == null) {
            // Игрок без клана забрал первым — очки в этом раунде сгорают и никому не достаются!
            return;
        }

        FileConfiguration ptsCfg = extension.getCustomConfig("points-and-limits.yml").get();
        String base = "events-smart-points." + eventConfigKey.toLowerCase();
        if (!ptsCfg.getBoolean(base + ".enabled", true)) return;

        int minPts = ptsCfg.getInt(base + ".min-points", 50);
        int maxPts = ptsCfg.getInt(base + ".max-points", 75);
        int fullOnline = Math.max(1, ptsCfg.getInt("events-smart-points.full-online-threshold", 40));

        double ratio = Math.min(1.0, (double) Bukkit.getOnlinePlayers().size() / (double) fullOnline);
        int points = (int) Math.round(minPts + (maxPts - minPts) * ratio);
        points = Math.max(minPts, Math.min(maxPts, points));

        String logMsg = "&fИгрок &e" + firstPlayer.getName() + " &fзаработал &a+" + points + " очк. &7(Ивент " + eventDisplayName + ")";
        addPointsAndLog(clan, firstPlayer.getName(), points, logMsg);

        extension.getClanCoreService().broadcastToClan(clan,
                "&6&l[Клан] &fИгрок &e" + firstPlayer.getName() + " &fпервым открыл &e" + eventDisplayName
                        + " &fи принес клану &a&l+" + points + " очков&f!");
    }

    /**
     * Начисление за выкопанную руду в шахте: каждые 20 руды = +1 очко (общий лимит клана: 100 очков в сутки).
     */
    public void handleMineOreBroken(Player player) {
        if (!extension.getModuleService().isSmartPointsEnabled() || player == null) return;
        ClanData clan = extension.getMainStorage().getPlayerClan(player.getName());
        if (clan == null) return;

        FileConfiguration ptsCfg = extension.getCustomConfig("points-and-limits.yml").get();
        if (!ptsCfg.getBoolean("jobs-daily-points.mine.enabled", true)) return;

        int oresPerPoint = Math.max(1, ptsCfg.getInt("jobs-daily-points.mine.ores-per-point", 20));
        int dailyMax = Math.max(1, ptsCfg.getInt("jobs-daily-points.mine.clan-daily-max-points", 100));

        int earned = extension.getPointsStorage().incrementMineOreAndCheckPoint(clan.getClanKey(), oresPerPoint, dailyMax);
        if (earned > 0) {
            String logMsg = "&fИгрок &e" + player.getName() + " &fзаработал &a+" + earned + " очк. &7(Добыча руды в шахте)";
            addPointsAndLog(clan, player.getName(), earned, logMsg);
            ChatUtil.sendMessage(player, "&a[Клан] &fВы принесли клану &a+1 очко &fза добычу руды в шахте!");
        }
    }

    /**
     * Начисление за срубленное дерево на лесопилке: каждые 15 деревьев = +1 очко (общий лимит клана: 100 очков в сутки).
     */
    public void handleWoodTreeChopped(Player player) {
        if (!extension.getModuleService().isSmartPointsEnabled() || player == null) return;
        ClanData clan = extension.getMainStorage().getPlayerClan(player.getName());
        if (clan == null) return;

        FileConfiguration ptsCfg = extension.getCustomConfig("points-and-limits.yml").get();
        if (!ptsCfg.getBoolean("jobs-daily-points.wood.enabled", true)) return;

        int treesPerPoint = Math.max(1, ptsCfg.getInt("jobs-daily-points.wood.trees-per-point", 15));
        int dailyMax = Math.max(1, ptsCfg.getInt("jobs-daily-points.wood.clan-daily-max-points", 100));

        int earned = extension.getPointsStorage().incrementWoodTreeAndCheckPoint(clan.getClanKey(), treesPerPoint, dailyMax);
        if (earned > 0) {
            String logMsg = "&fИгрок &e" + player.getName() + " &fзаработал &a+" + earned + " очк. &7(Рубка леса)";
            addPointsAndLog(clan, player.getName(), earned, logMsg);
            ChatUtil.sendMessage(player, "&a[Клан] &fВы принесли клану &a+1 очко &fза вырубку деревьев!");
        }
    }
}