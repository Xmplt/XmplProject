package me.xmpl.nexusclans.service;

import me.xmpl.lib.util.ChatUtil;
import me.xmpl.lib.util.InteractiveChatUtil;
import me.xmpl.lib.util.NumberDateUtil;
import me.xmpl.lib.util.TimeUtil;
import me.xmpl.lib.util.TitleUtil;
import me.xmpl.nexusclans.NexusClansExtension;
import me.xmpl.nexusclans.model.ClanData;
import me.xmpl.nexusclans.model.ClanMember;
import me.xmpl.nexusclans.model.ClanRole;
import me.xmpl.nexusclans.model.ClanWarSession;
import me.xmpl.nexusclans.model.ClanWarSession.PendingReplacementInvite;
import me.xmpl.nexusclans.model.ClanWarSession.SpectatorState;
import me.xmpl.nexusclans.model.ClanWarSession.WarStage;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class ClanWarService {
    private final NexusClansExtension extension;
    private ClanWarSession activeSession = null;

    public ClanWarService(NexusClansExtension extension) {
        this.extension = extension;
    }

    public void startWarClock() {
        extension.runTaskTimer(this::tickSecond, 20L, 20L);
    }

    public FileConfiguration getWarCfg() {
        return extension.getCustomConfig("war-arena.yml").get();
    }

    public FileConfiguration getWarMsgCfg() {
        return extension.getCustomConfig("commands/war-messages.yml").get();
    }

    public ClanWarSession getActiveSession() {
        return activeSession;
    }

    /**
     * Маршрутизатор подкоманд /c war, /c warmembers, /c pvpready, /c warjoin.
     */
    public void handleClanWarSubcommand(Player player, String sub, String[] args) {
        if (!extension.getModuleService().isClanWarsEnabled()) {
            ChatUtil.sendMessage(player, getWarMsgCfg().getString("messages.module-disabled"));
            return;
        }

        switch (sub.toLowerCase()) {
            case "war": {
                if (args.length == 0) {
                    initiateWarSetup(player);
                    return;
                }
                String action = args[0].toLowerCase();
                if (action.equals("invite")) {
                    if (args.length < 3) {
                        ChatUtil.sendMessage(player, getWarMsgCfg().getString("messages.war-invite-usage"));
                        return;
                    }
                    long bet = NumberDateUtil.parseStrictPositiveLong(args[2]);
                    sendWarInviteToClan(player, args[1], bet);
                } else if (action.equals("accept")) {
                    respondToWarInvite(player, true);
                } else if (action.equals("deny")) {
                    respondToWarInvite(player, false);
                } else {
                    initiateWarSetup(player);
                }
                break;
            }
            case "warmembers": {
                setWarRoster(player, args);
                break;
            }
            case "pvpready": {
                markPlayerPvpReady(player);
                break;
            }
            case "warjoin": {
                acceptReplacementInvite(player);
                break;
            }
        }
    }

    /**
     * 1. Запуск подготовки к войне (/c war): только Глава клана.
     */
    public synchronized void initiateWarSetup(Player leader) {
        FileConfiguration wMsg = getWarMsgCfg();
        ClanData clan = extension.getClanCoreService().requireLeader(leader);
        if (clan == null) return;

        if (activeSession != null) {
            ChatUtil.sendMessage(leader, "&cНа сервере уже идет подготовка или проведение клановой битвы!");
            return;
        }

        long now = System.currentTimeMillis();
        if (clan.getWarCooldownUntilMillis() > now) {
            ChatUtil.sendMessage(leader, wMsg.getString("messages.clan-on-war-cooldown"),
                    "{clan}", clan.getRawColoredTag(),
                    "{time}", TimeUtil.formatDuration(clan.getWarCooldownUntilMillis() - now));
            return;
        }

        if (!isArenaConfigured()) {
            ChatUtil.sendMessage(leader, "&cАрена для клановых войн еще не настроена администратором (/nexusclans)!");
            return;
        }

        int timeout = getWarCfg().getInt("settings.roster-assembly-timeout-seconds", 300);
        activeSession = new ClanWarSession(clan.getClanKey(), timeout);

        ChatUtil.sendMessage(leader, wMsg.getString("messages.war-start-prompt-leader"));

        // Уведомляем модераторов клана о том, что глава начал формировать состав
        String modNotify = wMsg.getString("messages.war-start-notify-moders");
        for (ClanMember m : clan.getMembers()) {
            if (m.getRole() == ClanRole.MODERATOR) {
                Player modOnline = Bukkit.getPlayerExact(m.getPlayerName());
                if (modOnline != null && modOnline.isOnline()) {
                    ChatUtil.sendMessage(modOnline, modNotify);
                }
            }
        }
    }

    private boolean isArenaConfigured() {
        return extension.getWarArenaStorage().getArenaRegion() != null
                && !extension.getWarArenaStorage().getClan1Points().isEmpty()
                && !extension.getWarArenaStorage().getClan2Points().isEmpty();
    }

    /**
     * 2. Формирование состава (/c warmembers <5 ников>) для инициатора или защитника.
     */
    public synchronized void setWarRoster(Player leader, String[] memberNicks) {
        FileConfiguration wMsg = getWarMsgCfg();
        ClanData clan = extension.getClanCoreService().requireLeader(leader);
        if (clan == null) return;

        if (activeSession == null) {
            ChatUtil.sendMessage(leader, "&cСначала начните сбор на войну командой &e/c war&c!");
            return;
        }

        boolean isAttacker = activeSession.getStage() == WarStage.ATTACKER_ROSTER_ASSEMBLY
                && activeSession.getClan1Key().equalsIgnoreCase(clan.getClanKey());
        boolean isDefender = activeSession.getStage() == WarStage.DEFENDER_ROSTER_ASSEMBLY
                && clan.getClanKey().equalsIgnoreCase(activeSession.getClan2Key());

        if (!isAttacker && !isDefender) {
            ChatUtil.sendMessage(leader, "&cСейчас ваш клан не находится на стадии формирования состава!");
            return;
        }

        int requiredSize = getWarCfg().getInt("settings.team-size", 5);
        if (memberNicks.length != requiredSize) {
            ChatUtil.sendMessage(leader, wMsg.getString("messages.warmembers-need-5"));
            return;
        }

        Set<String> uniqueCheck = new HashSet<>();
        List<String> resolvedNames = new ArrayList<>();
        for (String nick : memberNicks) {
            Player online = Bukkit.getPlayerExact(nick);
            if (online == null || !online.isOnline()) {
                ChatUtil.sendMessage(leader, "&cИгрок &e" + nick + " &cсейчас не в сети!");
                return;
            }
            if (clan.getMember(online.getName()) == null) {
                ChatUtil.sendMessage(leader, "&cИгрок &e" + online.getName() + " &cне состоит в вашем клане!");
                return;
            }
            if (!uniqueCheck.add(online.getName().toLowerCase())) {
                ChatUtil.sendMessage(leader, "&cНики участников не должны повторяться!");
                return;
            }
            resolvedNames.add(online.getName());
        }

        String rosterJoined = String.join(", ", resolvedNames);

        if (isAttacker) {
            activeSession.getClan1Roster().clear();
            activeSession.getClan1Roster().addAll(resolvedNames);
            activeSession.setTimerSeconds(getWarCfg().getInt("settings.roster-assembly-timeout-seconds", 300));

            ChatUtil.sendMessage(leader, wMsg.getString("messages.warmembers-added-leader"));
            for (String pName : resolvedNames) {
                Player p = Bukkit.getPlayerExact(pName);
                if (p != null) {
                    ChatUtil.sendMessage(p, wMsg.getString("messages.warmembers-added-participant"), "{roster}", rosterJoined);
                }
            }
        } else {
            // Защитник сформировал состав -> переходим к 10-минутной подготовке инвентарей и /clan pvpready!
            activeSession.getClan2Roster().clear();
            activeSession.getClan2Roster().addAll(resolvedNames);

            for (String pName : resolvedNames) {
                Player p = Bukkit.getPlayerExact(pName);
                if (p != null) {
                    ChatUtil.sendMessage(p, wMsg.getString("messages.warmembers-added-participant"), "{roster}", rosterJoined);
                }
            }

            activeSession.setStage(WarStage.INVENTORY_PREP);
            activeSession.setTimerSeconds(getWarCfg().getInt("settings.inventory-prep-seconds", 600));
            activeSession.getReadyPlayers().clear();

            String msg = wMsg.getString("messages.defender-roster-ready-broadcast");
            notifyWarParticipantsAndStaff(activeSession.getClan1Key(), msg, "{tag}", clan.getRawColoredTag(), "{roster}", rosterJoined);
            notifyWarParticipantsAndStaff(activeSession.getClan2Key(), msg, "{tag}", clan.getRawColoredTag(), "{roster}", rosterJoined);
        }
    }

    /**
     * 3. Отправка вызова второму клану (/c war invite <клан> <ставка от 1000$>).
     * Ставка сразу списывается (замораживается) с казны вызывающего клана и возвращается, если бой не начался.
     */
    public synchronized void sendWarInviteToClan(Player leader, String targetClanArg, long betAmount) {
        FileConfiguration wMsg = getWarMsgCfg();
        ClanData ownClan = extension.getClanCoreService().requireLeader(leader);
        if (ownClan == null) return;

        if (activeSession == null || !activeSession.getClan1Key().equalsIgnoreCase(ownClan.getClanKey())
                || activeSession.getStage() != WarStage.ATTACKER_ROSTER_ASSEMBLY) {
            ChatUtil.sendMessage(leader, "&cСначала начните сбор (/c war) и укажите состав (/c warmembers)!");
            return;
        }

        int reqSize = getWarCfg().getInt("settings.team-size", 5);
        if (activeSession.getClan1Roster().size() < reqSize) {
            ChatUtil.sendMessage(leader, wMsg.getString("messages.warmembers-need-5"));
            return;
        }

        double minBet = getWarCfg().getDouble("settings.min-bet-amount", 1000.0);
        if (betAmount < minBet) {
            ChatUtil.sendMessage(leader, "&cМинимальная ставка для клановой битвы: &e" + (long) minBet + "$&c!");
            return;
        }

        ClanData targetClan = extension.getMainStorage().getClanByNameOrTag(targetClanArg);
        if (targetClan == null || targetClan.getClanKey().equalsIgnoreCase(ownClan.getClanKey())) {
            ChatUtil.sendMessage(leader, "&cКлан &e" + targetClanArg + " &cне найден!");
            return;
        }

        if (extension.getAlliancesStorage().areAllies(ownClan.getClanKey(), targetClan.getClanKey())) {
            ChatUtil.sendMessage(leader, wMsg.getString("messages.cannot-war-ally"));
            return;
        }

        long now = System.currentTimeMillis();
        if (targetClan.getWarCooldownUntilMillis() > now) {
            ChatUtil.sendMessage(leader, wMsg.getString("messages.clan-on-war-cooldown"),
                    "{clan}", targetClan.getRawColoredTag(),
                    "{time}", TimeUtil.formatDuration(targetClan.getWarCooldownUntilMillis() - now));
            return;
        }

        if (ownClan.getBalance() < betAmount) {
            ChatUtil.sendMessage(leader, wMsg.getString("messages.war-invite-not-enough-treasury"),
                    "{bet}", NumberDateUtil.formatInteger(betAmount));
            return;
        }

        if (targetClan.getBalance() < betAmount) {
            ChatUtil.sendMessage(leader, wMsg.getString("messages.war-invite-target-not-enough-treasury"),
                    "{bet}", NumberDateUtil.formatInteger(betAmount));
            return;
        }

        Player targetLeader = Bukkit.getPlayerExact(targetClan.getLeaderName());
        if (targetLeader == null || !targetLeader.isOnline()) {
            ChatUtil.sendMessage(leader, "&cГлава клана &e" + targetClan.getRawColoredTag() + " &cсейчас не в сети!");
            return;
        }

        // Списываем ставку с казны вызывающего клана (заморозка до конца боя; при отмене до боя — возвращается)
        ownClan.setBalance(ownClan.getBalance() - betAmount);
        extension.getMainStorage().saveClanAsync(ownClan);

        activeSession.setClan2Key(targetClan.getClanKey());
        activeSession.setBetAmount(betAmount);
        activeSession.setClan1BetEscrowed(true);
        activeSession.setStage(WarStage.WAITING_DEFENDER_ACCEPT);
        activeSession.setTimerSeconds(getWarCfg().getInt("settings.invite-expire-seconds", 120));

        String betFmt = NumberDateUtil.formatInteger(betAmount);
        String rosterStr = String.join(", ", activeSession.getClan1Roster());

        ChatUtil.sendMessage(leader, wMsg.getString("messages.war-invite-sent"),
                "{bet}", betFmt, "{tag}", targetClan.getRawColoredTag());

        ChatUtil.sendMessage(targetLeader, wMsg.getString("messages.war-invite-received"),
                "{tag}", ownClan.getRawColoredTag(), "{bet}", betFmt, "{roster}", rosterStr);

        TextComponent accBtn = InteractiveChatUtil.button(
                wMsg.getString("buttons.accept-war-label", "&a&l[Принять вызов]"),
                "/war accept",
                Collections.singletonList("&aНажмите, чтобы принять клановую битву!"));
        TextComponent space = InteractiveChatUtil.text("   ");
        TextComponent denyBtn = InteractiveChatUtil.button(
                wMsg.getString("buttons.deny-war-label", "&c&l[Отклонить]"),
                "/war deny",
                Collections.singletonList("&cНажмите, чтобы отклонить вызов!"));

        InteractiveChatUtil.sendComponents(targetLeader, accBtn, space, denyBtn);
    }

    /**
     * 4. Принятие или отклонение вызова вторым кланом (/war accept / /war deny).
     */
    public synchronized void respondToWarInvite(Player defenderLeader, boolean accept) {
        FileConfiguration wMsg = getWarMsgCfg();
        ClanData defClan = extension.getClanCoreService().requireLeader(defenderLeader);
        if (defClan == null) return;

        if (activeSession == null || activeSession.getStage() != WarStage.WAITING_DEFENDER_ACCEPT
                || !defClan.getClanKey().equalsIgnoreCase(activeSession.getClan2Key())) {
            ChatUtil.sendMessage(defenderLeader, "&cУ вашего клана нет активного вызова на войну!");
            return;
        }

        ClanData attClan = extension.getMainStorage().getClanByNameOrTag(activeSession.getClan1Key());

        if (!accept) {
            ChatUtil.sendMessage(defenderLeader, "&eВы отклонили вызов на клановую войну.");
            if (attClan != null) {
                notifyWarParticipantsAndStaff(attClan.getClanKey(),
                        "&c[Война] Клан " + defClan.getRawColoredTag() + " &cотклонил ваш вызов на битву. Ставка возвращена в казну.");
            }
            cancelWarAndRefundBets(null);
            return;
        }

        double bet = activeSession.getBetAmount();
        if (defClan.getBalance() < bet) {
            ChatUtil.sendMessage(defenderLeader, wMsg.getString("messages.war-invite-not-enough-treasury"),
                    "{bet}", NumberDateUtil.formatInteger((long) bet));
            return;
        }

        // Замораживаем ставку второго клана
        defClan.setBalance(defClan.getBalance() - bet);
        extension.getMainStorage().saveClanAsync(defClan);
        activeSession.setClan2BetEscrowed(true);

        activeSession.setStage(WarStage.DEFENDER_ROSTER_ASSEMBLY);
        activeSession.setTimerSeconds(getWarCfg().getInt("settings.opponent-roster-timeout-seconds", 600));

        String enemyRoster = String.join(", ", activeSession.getClan1Roster());
        ChatUtil.sendMessage(defenderLeader, wMsg.getString("messages.war-accepted-defender"),
                "{enemy_roster}", enemyRoster);

        // Оповещаем модераторов второго клана
        for (ClanMember m : defClan.getMembers()) {
            if (m.getRole() == ClanRole.MODERATOR) {
                Player mod = Bukkit.getPlayerExact(m.getPlayerName());
                if (mod != null && mod.isOnline()) {
                    ChatUtil.sendMessage(mod, wMsg.getString("messages.war-start-notify-moders"));
                }
            }
        }

        if (attClan != null) {
            notifyWarParticipantsAndStaff(attClan.getClanKey(),
                    wMsg.getString("messages.war-accepted-attacker"), "{tag}", defClan.getRawColoredTag());
        }
    }

    /**
     * 5. Замена участника во время 10-минутного таймера подготовки (/war membersedit <старыйНик> <новыйНик>).
     */
    public synchronized void requestMemberReplacement(Player leader, String oldNick, String newNick) {
        FileConfiguration wMsg = getWarMsgCfg();
        ClanData clan = extension.getClanCoreService().requireLeader(leader);
        if (clan == null) return;

        if (activeSession == null || activeSession.getStage() != WarStage.INVENTORY_PREP) {
            ChatUtil.sendMessage(leader, "&cМенять состав можно только во время 10-минутной подготовки к бою!");
            return;
        }

        boolean isClan1 = clan.getClanKey().equalsIgnoreCase(activeSession.getClan1Key());
        boolean isClan2 = clan.getClanKey().equalsIgnoreCase(activeSession.getClan2Key());
        if (!isClan1 && !isClan2) return;

        List<String> roster = isClan1 ? activeSession.getClan1Roster() : activeSession.getClan2Roster();
        String foundOld = null;
        for (String r : roster) {
            if (r.equalsIgnoreCase(oldNick)) {
                foundOld = r;
                break;
            }
        }
        if (foundOld == null) {
            ChatUtil.sendMessage(leader, "&cИгрок &e" + oldNick + " &cне числится в вашем боевом составе!");
            return;
        }

        Player newTarget = Bukkit.getPlayerExact(newNick);
        if (newTarget == null || !newTarget.isOnline() || clan.getMember(newTarget.getName()) == null) {
            ChatUtil.sendMessage(leader, "&cИгрок &e" + newNick + " &cдолжен быть в сети и состоять в вашем клане!");
            return;
        }

        if (activeSession.isParticipant(newTarget.getName())) {
            ChatUtil.sendMessage(leader, "&cИгрок &e" + newTarget.getName() + " &cуже находится в составе!");
            return;
        }

        PendingReplacementInvite rep = new PendingReplacementInvite(
                clan.getClanKey(), foundOld, newTarget.getName(), System.currentTimeMillis() + 60000L
        );
        activeSession.getReplacementInvites().put(newTarget.getName().toLowerCase(), rep);

        ChatUtil.sendMessage(leader, "&aПриглашение на замену отправлено игроку &e" + newTarget.getName() + "&a!");
        ChatUtil.sendMessage(newTarget, wMsg.getString("messages.member-replace-invite"));

        TextComponent joinBtn = InteractiveChatUtil.button("&a&l[Принять участие]", "/clan warjoin",
                Collections.singletonList("&aНажмите, чтобы вступить в боевой состав!"));
        InteractiveChatUtil.sendComponents(newTarget, joinBtn);
    }

    public synchronized void acceptReplacementInvite(Player newPlayer) {
        if (activeSession == null || activeSession.getStage() != WarStage.INVENTORY_PREP) {
            ChatUtil.sendMessage(newPlayer, "&cСейчас нет активного приглашения в боевой состав!");
            return;
        }

        PendingReplacementInvite rep = activeSession.getReplacementInvites().remove(newPlayer.getName().toLowerCase());
        if (rep == null || rep.isExpired()) {
            ChatUtil.sendMessage(newPlayer, "&cПриглашение в боевой состав истекло!");
            return;
        }

        List<String> roster = rep.clanKey.equalsIgnoreCase(activeSession.getClan1Key())
                ? activeSession.getClan1Roster()
                : activeSession.getClan2Roster();

        synchronized (roster) {
            for (int i = 0; i < roster.size(); i++) {
                if (roster.get(i).equalsIgnoreCase(rep.oldPlayerName)) {
                    roster.set(i, newPlayer.getName());
                    break;
                }
            }
        }
        activeSession.getReadyPlayers().remove(rep.oldPlayerName.toLowerCase());

        String rosterStr = String.join(", ", roster);
        ChatUtil.sendMessage(newPlayer, getWarMsgCfg().getString("messages.warmembers-added-participant"), "{roster}", rosterStr);
        ChatUtil.sendMessage(newPlayer, "&eПодготовьте инвентарь и введите &a/clan pvpready&e!");

        notifyWarParticipantsAndStaff(rep.clanKey,
                "&e[Война] Игрок &a" + newPlayer.getName() + " &eзаменил бойца &c" + rep.oldPlayerName + " &eв составе!");
    }

    /**
     * 6. Подтверждение готовности бойца (/clan pvpready):
     * если все участники обоих кланов подтвердили готовность — таймер моментально переходит к 3-минутной блокировке инвентарей!
     */
    public synchronized void markPlayerPvpReady(Player player) {
        if (activeSession == null || activeSession.getStage() != WarStage.INVENTORY_PREP) {
            ChatUtil.sendMessage(player, "&cСейчас не идет стадия подтверждения готовности к бою!");
            return;
        }

        if (!activeSession.isParticipant(player.getName())) {
            ChatUtil.sendMessage(player, "&cВы не являетесь участником предстоящей клановой битвы!");
            return;
        }

        String key = player.getName().toLowerCase();
        if (!activeSession.getReadyPlayers().add(key)) {
            ChatUtil.sendMessage(player, "&eВы уже подтвердили свою готовность!");
            return;
        }

        ClanData playerClan = extension.getMainStorage().getPlayerClan(player.getName());
        String tag = playerClan != null ? playerClan.getRawColoredTag() : "";
        int totalRequired = activeSession.getClan1Roster().size() + activeSession.getClan2Roster().size();
        int currentReady = activeSession.getReadyPlayers().size();

        String msg = getWarMsgCfg().getString("messages.pvpready-notify");
        notifyWarParticipantsAndStaff(activeSession.getClan1Key(), msg,
                "{player}", player.getName(), "{tag}", tag, "{ready}", currentReady, "{total}", totalRequired);
        notifyWarParticipantsAndStaff(activeSession.getClan2Key(), msg,
                "{player}", player.getName(), "{tag}", tag, "{ready}", currentReady, "{total}", totalRequired);

        if (currentReady >= totalRequired) {
            transitionToInventoryLockStage();
        }
    }

    private void transitionToInventoryLockStage() {
        activeSession.setStage(WarStage.INVENTORY_LOCK_3MIN);
        activeSession.setTimerSeconds(getWarCfg().getInt("settings.inventory-lock-countdown-seconds", 180));

        // Закрываем открытые окна инвентаря у всех бойцов
        for (String name : activeSession.getAllParticipantsNames()) {
            Player p = Bukkit.getPlayerExact(name);
            if (p != null && p.isOnline()) {
                p.closeInventory();
            }
        }

        String msg = getWarMsgCfg().getString("messages.all-ready-lock-started");
        notifyWarParticipantsAndStaff(activeSession.getClan1Key(), msg);
        notifyWarParticipantsAndStaff(activeSession.getClan2Key(), msg);
    }

    /**
     * Главный ежесекундный таймер всех этапов клановой войны.
     */
    private synchronized void tickSecond() {
        if (activeSession == null) return;

        activeSession.decrementTimer();
        int sec = activeSession.getTimerSeconds();
        FileConfiguration wMsg = getWarMsgCfg();

        switch (activeSession.getStage()) {
            case ATTACKER_ROSTER_ASSEMBLY:
            case WAITING_DEFENDER_ACCEPT:
            case DEFENDER_ROSTER_ASSEMBLY: {
                if (sec <= 0) {
                    cancelWarAndRefundBets(wMsg.getString("messages.stage-expired-cancelled"));
                }
                break;
            }
            case INVENTORY_PREP: {
                if (sec <= 0) {
                    transitionToInventoryLockStage();
                }
                break;
            }
            case INVENTORY_LOCK_3MIN: {
                if (sec == 120 || sec == 60 || sec == 30 || (sec <= 5 && sec >= 1)) {
                    String timeLabel = sec == 120 ? "2 минуты"
                            : (sec == 60 ? "1 минута"
                            : (sec == 30 ? "30 секунд" : (sec + " сек.")));
                    String notify = wMsg.getString("messages.lock-countdown-notify");
                    notifyWarParticipantsAndStaff(activeSession.getClan1Key(), notify, "{time}", timeLabel);
                    notifyWarParticipantsAndStaff(activeSession.getClan2Key(), notify, "{time}", timeLabel);
                }

                if (sec <= 0) {
                    // Проверяем, все ли 10 участников находятся на сервере перед телепортацией!
                    String offlineName = findFirstOfflineParticipant();
                    if (offlineName != null) {
                        // Если хотя бы 1 игрок не в сети — бой отменяется БЕЗ кулдауна и откатывается к замене игрока!
                        activeSession.getReadyPlayers().remove(offlineName.toLowerCase());
                        activeSession.setStage(WarStage.INVENTORY_PREP);
                        activeSession.setTimerSeconds(getWarCfg().getInt("settings.inventory-prep-seconds", 600));

                        String err = wMsg.getString("messages.fighter-offline-cancelled");
                        notifyWarParticipantsAndStaff(activeSession.getClan1Key(), err, "{player}", offlineName);
                        notifyWarParticipantsAndStaff(activeSession.getClan2Key(), err, "{player}", offlineName);
                        return;
                    }
                    teleportFightersToCagesAndStartCountdown();
                }
                break;
            }
            case CAGE_COUNTDOWN_1MIN: {
                if (sec <= 5 && sec >= 1) {
                    sendTitleToAllArenaActors("&c&l" + sec, "&eПриготовьтесь к бою!");
                }
                if (sec <= 0) {
                    sendTitleToAllArenaActors("&a&lСТАРТ!", "&fБарьеры сняты — в бой!");
                    extension.getWarArenaStorage().removeAllBarrierCages();
                    activeSession.setStage(WarStage.ACTIVE_BATTLE);
                    activeSession.setTimerSeconds(getWarCfg().getInt("settings.battle-max-duration-seconds", 1800));
                    checkVictoryCondition();
                }
                break;
            }
            case ACTIVE_BATTLE: {
                int remInterval = Math.max(30, getWarCfg().getInt("settings.spectator-reminder-interval-seconds", 150));
                if (sec > 0 && sec % remInterval == 0) {
                    String specRem = wMsg.getString("messages.spectator-reminder");
                    for (UUID specUuid : activeSession.getSpectators().keySet()) {
                        Player sp = Bukkit.getPlayer(specUuid);
                        if (sp != null && sp.isOnline()) {
                            ChatUtil.sendMessage(sp, specRem);
                        }
                    }
                }
                if (sec <= 0) {
                    // Если истекли 30 минут битвы — побеждает клан, у которого осталось больше живых бойцов
                    String winner = activeSession.getAliveClan1().size() >= activeSession.getAliveClan2().size()
                            ? activeSession.getClan1Key() : activeSession.getClan2Key();
                    declareWarWinner(winner);
                }
                break;
            }
            case WINNER_LOOT_2MIN: {
                if (sec <= 0) {
                    finishWarAndRegenerateArena();
                }
                break;
            }
        }
    }

    private String findFirstOfflineParticipant() {
        for (String name : activeSession.getAllParticipantsNames()) {
            Player p = Bukkit.getPlayerExact(name);
            if (p == null || !p.isOnline()) {
                return name;
            }
        }
        return null;
    }

    /**
     * 7. Телепортация бойцов на точки Clan 1 и Clan 2 в барьерные капсулы на 1 минуту + глобальный анонс /war spectate.
     */
    private void teleportFightersToCagesAndStartCountdown() {
        activeSession.setStage(WarStage.CAGE_COUNTDOWN_1MIN);
        activeSession.setTimerSeconds(getWarCfg().getInt("settings.cage-countdown-seconds", 60));

        List<Location> pts1 = extension.getWarArenaStorage().getClan1Points();
        List<Location> pts2 = extension.getWarArenaStorage().getClan2Points();

        List<Location> allUsedSpawns = new ArrayList<>();
        List<String> r1 = activeSession.getClan1Roster();
        for (int i = 0; i < r1.size(); i++) {
            String name = r1.get(i);
            Location spawn = pts1.get(i % pts1.size()).clone();
            allUsedSpawns.add(spawn);
            activeSession.getAssignedSpawnPoints().put(name.toLowerCase(), spawn);
            activeSession.getAliveClan1().add(name.toLowerCase());
        }

        List<String> r2 = activeSession.getClan2Roster();
        for (int i = 0; i < r2.size(); i++) {
            String name = r2.get(i);
            Location spawn = pts2.get(i % pts2.size()).clone();
            allUsedSpawns.add(spawn);
            activeSession.getAssignedSpawnPoints().put(name.toLowerCase(), spawn);
            activeSession.getAliveClan2().add(name.toLowerCase());
        }

        // Строим барьерные капсулы по бокам и сверху
        extension.getWarArenaStorage().buildBarrierCagesAround(allUsedSpawns);

        FileConfiguration wMsg = getWarMsgCfg();
        String waitMsg = wMsg.getString("messages.cage-wait-1min");

        for (String name : activeSession.getAllParticipantsNames()) {
            Player p = Bukkit.getPlayerExact(name);
            Location targetLoc = activeSession.getAssignedSpawnPoints().get(name.toLowerCase());
            if (p != null && p.isOnline() && targetLoc != null) {
                p.leaveVehicle();
                p.eject();
                p.setGameMode(GameMode.SURVIVAL);
                p.setFlying(false);
                p.setAllowFlight(false);
                p.teleport(targetLoc);
                ChatUtil.sendMessage(p, waitMsg);
            }
        }

        // Глобальное объявление о начале битвы с кликабельной кнопкой [Смотреть] (/war spectate)
        ClanData c1 = extension.getMainStorage().getClanByNameOrTag(activeSession.getClan1Key());
        ClanData c2 = extension.getMainStorage().getClanByNameOrTag(activeSession.getClan2Key());
        String tag1 = c1 != null ? c1.getRawColoredTag() : activeSession.getClan1Key();
        String tag2 = c2 != null ? c2.getRawColoredTag() : activeSession.getClan2Key();

        String globalRaw = ChatUtil.applyReplacements(wMsg.getString("messages.global-battle-started"),
                "{tag1}", tag1, "{tag2}", tag2);
        TextComponent prefixComp = InteractiveChatUtil.text(globalRaw);
        TextComponent specBtn = InteractiveChatUtil.button(
                wMsg.getString("buttons.spectate-label", "&6&l[Смотреть]"),
                "/war spectate",
                wMsg.getStringList("buttons.spectate-hover")
        );

        for (Player online : Bukkit.getOnlinePlayers()) {
            InteractiveChatUtil.sendComponents(online, prefixComp, specBtn);
        }
    }

    /**
     * 8. Обработка смерти или выхода бойца с сервера во время капсул или боя.
     */
    public synchronized void handleFighterDeathOrQuit(Player victim, Player killerIfAny, boolean quitServer) {
        if (activeSession == null) return;
        if (activeSession.getStage() != WarStage.CAGE_COUNTDOWN_1MIN && activeSession.getStage() != WarStage.ACTIVE_BATTLE) {
            return;
        }

        String vKey = victim.getName().toLowerCase();
        boolean wasClan1 = activeSession.getAliveClan1().remove(vKey);
        boolean wasClan2 = activeSession.getAliveClan2().remove(vKey);
        if (!wasClan1 && !wasClan2) return;

        if (wasClan1) {
            activeSession.getClan1DeathOrder().add(victim.getName());
            if (quitServer) activeSession.incrementQuitDeathsClan1();
        } else {
            activeSession.getClan2DeathOrder().add(victim.getName());
            if (quitServer) activeSession.incrementQuitDeathsClan2();
        }

        if (killerIfAny != null && !killerIfAny.equals(victim) && activeSession.isParticipant(killerIfAny.getName())) {
            String kKey = killerIfAny.getName().toLowerCase();
            activeSession.getWarKills().put(kKey, activeSession.getWarKills().getOrDefault(kKey, 0) + 1);
        }

        ClanData vClan = extension.getMainStorage().getPlayerClan(victim.getName());
        String tag = vClan != null ? vClan.getRawColoredTag() : "";
        String deathMsg = ChatUtil.applyReplacements(getWarMsgCfg().getString("messages.fighter-died-broadcast"),
                "{player}", victim.getName(), "{tag}", tag);

        broadcastToArenaFightersAndSpectators(deathMsg);
        checkVictoryCondition();
    }

    private void checkVictoryCondition() {
        if (activeSession == null) return;
        if (activeSession.getStage() != WarStage.CAGE_COUNTDOWN_1MIN && activeSession.getStage() != WarStage.ACTIVE_BATTLE) {
            return;
        }

        if (activeSession.getAliveClan1().isEmpty()) {
            declareWarWinner(activeSession.getClan2Key());
        } else if (activeSession.getAliveClan2().isEmpty()) {
            declareWarWinner(activeSession.getClan1Key());
        }
    }

    /**
     * 9. Победа в войне:
     * - Выплата банка (обеих ставок) в казну клана-победителя.
     * - Списание 100 очков с участников проигравшего клана по порядку смерти (1-й умерший теряет больше всех, последний — меньше всех).
     * - Начисление 100 очков участникам клана-победителя по количеству киллов (от малого к большему),
     *   либо рандомно, если проигравшие вышли из игры.
     * - Наложение 1-дневного кулдауна на войну обоим кланам.
     * - Запуск 2-минутного таймера для сбора лута победителями.
     */
    private synchronized void declareWarWinner(String winnerClanKey) {
        if (activeSession == null || activeSession.getWinningClanKey() != null) return;

        extension.getWarArenaStorage().removeAllBarrierCages();
        activeSession.setWinningClanKey(winnerClanKey);

        String loserClanKey = winnerClanKey.equalsIgnoreCase(activeSession.getClan1Key())
                ? activeSession.getClan2Key() : activeSession.getClan1Key();

        ClanData winnerClan = extension.getMainStorage().getClanByNameOrTag(winnerClanKey);
        ClanData loserClan = extension.getMainStorage().getClanByNameOrTag(loserClanKey);

        double totalBank = activeSession.getBetAmount() * 2.0;
        long cdDurationMs = getWarCfg().getLong("settings.clan-war-cooldown-seconds", 86400L) * 1000L;
        long cdUntil = System.currentTimeMillis() + cdDurationMs;

        if (winnerClan != null) {
            winnerClan.setBalance(winnerClan.getBalance() + totalBank);
            winnerClan.setWarCooldownUntilMillis(cdUntil);
            extension.getMainStorage().saveClanAsync(winnerClan);
        }
        if (loserClan != null) {
            loserClan.setWarCooldownUntilMillis(cdUntil);
            extension.getMainStorage().saveClanAsync(loserClan);
        }

        activeSession.setClan1BetEscrowed(false);
        activeSession.setClan2BetEscrowed(false);

        distributeWarPoints(winnerClan, loserClan);

        String winTag = winnerClan != null ? winnerClan.getRawColoredTag() : winnerClanKey;
        String loseTag = loserClan != null ? loserClan.getRawColoredTag() : loserClanKey;
        String bc = ChatUtil.applyReplacements(getWarMsgCfg().getString("messages.war-victory-broadcast"),
                "{winner}", winTag, "{loser}", loseTag, "{bank}", NumberDateUtil.formatInteger(Math.round(totalBank)));
        Bukkit.broadcastMessage(ChatUtil.color(bc));

        activeSession.setStage(WarStage.WINNER_LOOT_2MIN);
        activeSession.setTimerSeconds(getWarCfg().getInt("settings.winner-loot-collection-seconds", 120));
    }

    private void distributeWarPoints(ClanData winnerClan, ClanData loserClan) {
        boolean winnerIsClan1 = winnerClan != null && winnerClan.getClanKey().equalsIgnoreCase(activeSession.getClan1Key());
        List<String> winnerRoster = new ArrayList<>(winnerIsClan1 ? activeSession.getClan1Roster() : activeSession.getClan2Roster());
        List<String> loserRoster = new ArrayList<>(winnerIsClan1 ? activeSession.getClan2Roster() : activeSession.getClan1Roster());
        List<String> loserDeathOrder = new ArrayList<>(winnerIsClan1 ? activeSession.getClan2DeathOrder() : activeSession.getClan1DeathOrder());
        int loserQuitCount = winnerIsClan1 ? activeSession.getQuitDeathsCountClan2() : activeSession.getQuitDeathsCountClan1();

        List<Integer> deathPenalties = getWarCfg().getIntegerList("points-distribution.loser-death-order-penalties");
        if (deathPenalties == null || deathPenalties.size() < 5) {
            deathPenalties = java.util.Arrays.asList(26, 23, 20, 17, 14); // В сумме 100
        }

        List<Integer> killRewards = getWarCfg().getIntegerList("points-distribution.winner-kill-rank-rewards");
        if (killRewards == null || killRewards.size() < 5) {
            killRewards = java.util.Arrays.asList(28, 23, 20, 16, 13); // В сумме 100
        }

        // 1. Списываем 100 очков с проигравших по порядку их смерти (1-й умерший теряет 26, последний — 14)
        if (loserClan != null && !loserRoster.isEmpty()) {
            List<String> orderedLosers = new ArrayList<>(loserDeathOrder);
            for (String m : loserRoster) {
                if (orderedLosers.stream().noneMatch(x -> x.equalsIgnoreCase(m))) {
                    orderedLosers.add(m);
                }
            }

            for (int i = 0; i < orderedLosers.size(); i++) {
                String player = orderedLosers.get(i);
                int penalty = (i < deathPenalties.size()) ? deathPenalties.get(i) : 20;
                String log = "&fИгрок &e" + player + " &fпотерял &c-" + penalty + " очк. &7(Поражение в войне кланов, смерть #" + (i + 1) + ")";
                extension.getSmartPointsService().addPointsAndLog(loserClan, player, -penalty, log);
            }
        }

        // 2. Начисляем 100 очков победителям:
        // Если все проигравшие вышли из игры (или totalKills == 0 из-за дисконнекта) — распределяем рандомно!
        if (winnerClan != null && !winnerRoster.isEmpty()) {
            int totalWinnerKills = 0;
            for (String w : winnerRoster) {
                totalWinnerKills += activeSession.getWarKills().getOrDefault(w.toLowerCase(), 0);
            }

            List<String> orderedWinners = new ArrayList<>(winnerRoster);
            boolean distributeRandomly = (loserQuitCount >= loserRoster.size()) || (totalWinnerKills == 0);

            if (distributeRandomly) {
                Collections.shuffle(orderedWinners);
            } else {
                // Сортируем от большего числа убийств к меньшему
                orderedWinners.sort((a, b) -> Integer.compare(
                        activeSession.getWarKills().getOrDefault(b.toLowerCase(), 0),
                        activeSession.getWarKills().getOrDefault(a.toLowerCase(), 0)
                ));
            }

            for (int i = 0; i < orderedWinners.size(); i++) {
                String player = orderedWinners.get(i);
                int reward = (i < killRewards.size()) ? killRewards.get(i) : 20;
                int kills = activeSession.getWarKills().getOrDefault(player.toLowerCase(), 0);
                String log = "&fИгрок &e" + player + " &fзаработал &a+" + reward + " очк. &7(Победа в войне кланов, убийств: " + kills + ")";
                extension.getSmartPointsService().addPointsAndLog(winnerClan, player, reward, log);
            }
        }
    }

    /**
     * 10. Режим наблюдателя (/war spectate и /war leave).
     */
    public synchronized void joinSpectatorMode(Player player) {
        if (activeSession == null || (activeSession.getStage() != WarStage.CAGE_COUNTDOWN_1MIN
                && activeSession.getStage() != WarStage.ACTIVE_BATTLE
                && activeSession.getStage() != WarStage.WINNER_LOOT_2MIN)) {
            ChatUtil.sendMessage(player, "&cСейчас на арене не проходит активная клановая битва!");
            return;
        }

        if (activeSession.isAliveFighter(player.getName())) {
            ChatUtil.sendMessage(player, "&cВы являетесь участником боя и не можете перейти в наблюдатели!");
            return;
        }

        Location specPoint = extension.getWarArenaStorage().getSpectatorPoint();
        if (specPoint == null || specPoint.getWorld() == null) {
            ChatUtil.sendMessage(player, "&cТочка наблюдения не установлена администратором!");
            return;
        }

        if (!activeSession.getSpectators().containsKey(player.getUniqueId())) {
            activeSession.getSpectators().put(player.getUniqueId(),
                    new SpectatorState(player.getLocation().clone(), player.getGameMode()));
        }

        player.setGameMode(GameMode.SPECTATOR);
        player.teleport(specPoint);
        ChatUtil.sendMessage(player, getWarMsgCfg().getString("messages.spectator-joined"));
    }

    public synchronized void leaveSpectatorMode(Player player) {
        if (activeSession == null || !activeSession.getSpectators().containsKey(player.getUniqueId())) {
            ChatUtil.sendMessage(player, "&cВы сейчас не находитесь в режиме наблюдения за войной кланов!");
            return;
        }

        SpectatorState state = activeSession.getSpectators().remove(player.getUniqueId());
        restoreSpectator(player, state);
        ChatUtil.sendMessage(player, getWarMsgCfg().getString("messages.spectator-left"));
    }

    private void restoreSpectator(Player player, SpectatorState state) {
        if (player == null || !player.isOnline()) return;
        Location fallback = getServerSpawn();
        player.setGameMode(state != null && state.previousGameMode != GameMode.SPECTATOR ? state.previousGameMode : GameMode.SURVIVAL);
        player.teleport(state != null && state.previousLocation != null ? state.previousLocation : fallback);
    }

    /**
     * 11. Окончание 2-минутного таймера сбора лута победителями -> телепортация оставшихся на спавн и регенерация арены.
     */
    public synchronized void finishWarAndRegenerateArena() {
        if (activeSession == null) return;

        Location spawn = getServerSpawn();

        // Возвращаем всех наблюдателей
        for (Map.Entry<UUID, SpectatorState> entry : new ArrayList<>(activeSession.getSpectators().entrySet())) {
            Player sp = Bukkit.getPlayer(entry.getKey());
            if (sp != null && sp.isOnline()) {
                restoreSpectator(sp, entry.getValue());
            }
        }
        activeSession.getSpectators().clear();

        // Телепортируем оставшихся на арене победителей на спавн
        for (String fighterName : activeSession.getAllParticipantsNames()) {
            Player p = Bukkit.getPlayerExact(fighterName);
            if (p != null && p.isOnline() && extension.getWarArenaStorage().isInWarArena(p.getLocation())) {
                if (spawn != null) p.teleport(spawn);
            }
        }

        // Регенерируем блоки арены
        extension.getWarArenaStorage().regenerateArena();
        activeSession = null;
    }

    public synchronized void cancelWarAndRefundBets(String broadcastReasonIfAny) {
        if (activeSession == null) return;

        if (activeSession.isClan1BetEscrowed()) {
            ClanData c1 = extension.getMainStorage().getClanByNameOrTag(activeSession.getClan1Key());
            if (c1 != null) {
                c1.setBalance(c1.getBalance() + activeSession.getBetAmount());
                extension.getMainStorage().saveClanAsync(c1);
            }
        }
        if (activeSession.isClan2BetEscrowed() && activeSession.getClan2Key() != null) {
            ClanData c2 = extension.getMainStorage().getClanByNameOrTag(activeSession.getClan2Key());
            if (c2 != null) {
                c2.setBalance(c2.getBalance() + activeSession.getBetAmount());
                extension.getMainStorage().saveClanAsync(c2);
            }
        }

        if (broadcastReasonIfAny != null && !broadcastReasonIfAny.isEmpty()) {
            notifyWarParticipantsAndStaff(activeSession.getClan1Key(), broadcastReasonIfAny);
            if (activeSession.getClan2Key() != null) {
                notifyWarParticipantsAndStaff(activeSession.getClan2Key(), broadcastReasonIfAny);
            }
        }

        extension.getWarArenaStorage().removeAllBarrierCages();
        activeSession = null;
    }

    private void notifyWarParticipantsAndStaff(String clanKey, String message, Object... replacements) {
        if (clanKey == null || message == null) return;
        ClanData clan = extension.getMainStorage().getClanByNameOrTag(clanKey);
        if (clan == null) return;

        for (ClanMember m : clan.getMembers()) {
            boolean isStaff = m.getRole().isAtLeast(ClanRole.MODERATOR);
            boolean isFighter = activeSession != null && activeSession.isParticipant(m.getPlayerName());
            if (isStaff || isFighter) {
                Player p = Bukkit.getPlayerExact(m.getPlayerName());
                if (p != null && p.isOnline()) {
                    ChatUtil.sendMessage(p, message, replacements);
                }
            }
        }
    }

    private void broadcastToArenaFightersAndSpectators(String message) {
        if (activeSession == null) return;
        Set<UUID> recipients = new HashSet<>(activeSession.getSpectators().keySet());
        for (String f : activeSession.getAllParticipantsNames()) {
            Player p = Bukkit.getPlayerExact(f);
            if (p != null && p.isOnline()) recipients.add(p.getUniqueId());
        }
        for (UUID uuid : recipients) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                ChatUtil.sendMessage(p, message);
            }
        }
    }

    private void sendTitleToAllArenaActors(String title, String subtitle) {
        if (activeSession == null) return;
        for (String f : activeSession.getAllParticipantsNames()) {
            Player p = Bukkit.getPlayerExact(f);
            if (p != null && p.isOnline()) {
                TitleUtil.sendTitle(p, title, subtitle, 0, 25, 5);
            }
        }
        for (UUID uuid : activeSession.getSpectators().keySet()) {
            Player sp = Bukkit.getPlayer(uuid);
            if (sp != null && sp.isOnline()) {
                TitleUtil.sendTitle(sp, title, subtitle, 0, 25, 5);
            }
        }
    }

    private Location getServerSpawn() {
        World w = !Bukkit.getWorlds().isEmpty() ? Bukkit.getWorlds().get(0) : null;
        return w != null ? w.getSpawnLocation() : null;
    }
}