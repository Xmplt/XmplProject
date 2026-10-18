package me.xmpl.nexusclans.service;

import me.xmpl.antirelog.AntiRelogExtension;
import me.xmpl.lib.extension.XmplExtension;
import me.xmpl.lib.hook.VaultHook;
import me.xmpl.lib.util.ChatUtil;
import me.xmpl.lib.util.InteractiveChatUtil;
import me.xmpl.lib.util.NumberDateUtil;
import me.xmpl.lib.util.TimeUtil;
import me.xmpl.nexusclans.NexusClansExtension;
import me.xmpl.nexusclans.model.ClanData;
import me.xmpl.nexusclans.model.ClanLogEntry;
import me.xmpl.nexusclans.model.ClanMember;
import me.xmpl.nexusclans.model.ClanRole;
import me.xmpl.nexusclans.model.PendingInvite;
import me.xmpl.nexusclans.model.PendingLeaderTransfer;
import me.xmpl.nexusclans.storage.ClanAlliancesDatabaseStorage.ClanTeamSettings;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ClanCoreService {
    private final NexusClansExtension extension;
    // Активные приглашения в клан: targetKey -> PendingInvite
    private final Map<String, PendingInvite> activeInvites = new ConcurrentHashMap<>();
    // Ожидание подтверждения /clan remove confirm: leaderUUID -> expireAtMillis
    private final Map<UUID, Long> pendingRemoveConfirms = new ConcurrentHashMap<>();
    // Ожидание подтверждения /c leaderaccept <ник>: leaderUUID -> PendingLeaderTransfer
    private final Map<UUID, PendingLeaderTransfer> pendingLeaderTransfers = new ConcurrentHashMap<>();

    public ClanCoreService(NexusClansExtension extension) {
        this.extension = extension;
    }

    public void startInviteExpirationTask() {
        extension.runTaskTimer(() -> {
            long now = System.currentTimeMillis();
            FileConfiguration msgCfg = getMsgCfg();
            Iterator<Map.Entry<String, PendingInvite>> it = activeInvites.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, PendingInvite> entry = it.next();
                PendingInvite inv = entry.getValue();
                if (now >= inv.getExpireAtMillis()) {
                    it.remove();
                    Player sender = Bukkit.getPlayerExact(inv.getSenderName());
                    if (sender != null && sender.isOnline()) {
                        ChatUtil.sendMessage(sender, msgCfg.getString("messages.invite-expired-sender"),
                                "{target}", inv.getTargetName());
                    }
                    Player target = Bukkit.getPlayerExact(inv.getTargetName());
                    if (target != null && target.isOnline()) {
                        ChatUtil.sendMessage(target, msgCfg.getString("messages.invite-expired-target"));
                    }
                }
            }
        }, 20L, 20L);
    }

    public FileConfiguration getMsgCfg() {
        return extension.getCustomConfig("commands/clan-messages.yml").get();
    }

    public void broadcastToClan(ClanData clan, String message, Object... replacements) {
        if (clan == null || message == null || message.isEmpty()) return;
        for (ClanMember m : clan.getMembers()) {
            Player online = Bukkit.getPlayerExact(m.getPlayerName());
            if (online != null && online.isOnline()) {
                ChatUtil.sendMessage(online, message, replacements);
            }
        }
    }

    public boolean isPlayerInAntiRelogPvp(Player player) {
        if (!extension.getCustomConfig("chest-and-home.yml").get().getBoolean("clan-home.block-teleports-in-antirelog-pvp", true)) {
            return false;
        }
        XmplExtension ext = extension.getCore().getExtensionManager().getExtension("AntiRelog");
        if (ext instanceof AntiRelogExtension && ext.isEnabled()) {
            return ((AntiRelogExtension) ext).getCombatManager().isInPvp(player);
        }
        return false;
    }

    /**
     * 1. Вывод умного /clan help в зависимости от наличия клана и роли игрока + шапка настроек клана и союзов.
     */
    public void sendDynamicHelp(Player player) {
        FileConfiguration msgCfg = getMsgCfg();
        ClanData clan = extension.getMainStorage().getPlayerClan(player.getName());
        if (clan == null) {
            ChatUtil.sendMessages(player, msgCfg.getStringList("help.no-clan"));
            return;
        }

        ClanMember member = clan.getMember(player.getName());
        String yes = msgCfg.getString("status-tags.yes", "&aДа");
        String no = msgCfg.getString("status-tags.no", "&cНет");

        ClanTeamSettings teamSettings = extension.getAlliancesStorage().getSettings(clan.getClanKey());

        ChatUtil.sendMessages(player, msgCfg.getStringList("help.settings-header"),
                "{tag}", clan.getRawColoredTag(),
                "{pvp}", clan.isPvpEnabled() ? yes : no,
                "{sit}", clan.isSitAllowed() ? yes : no,
                "{tp}", clan.isTpAllowed() ? yes : no,
                "{team_pvp}", teamSettings.teamPvpEnabled ? yes : no,
                "{team_sit}", teamSettings.teamSitAllowed ? yes : no,
                "{team_tp}", teamSettings.teamTpAllowed ? yes : no);

        ChatUtil.sendMessages(player, msgCfg.getStringList("help.member"));
        if (member != null && member.getRole().isAtLeast(ClanRole.MODERATOR)) {
            ChatUtil.sendMessages(player, msgCfg.getStringList("help.moderator-extra"));
        }
        if (member != null && member.getRole() == ClanRole.LEADER) {
            ChatUtil.sendMessages(player, msgCfg.getStringList("help.leader-extra"));
        }
    }

    /**
     * 2. Создание клана (/clan create <название>): стоимость 280 000$, поддержка цветов, жирного и курсива.
     */
    public void createClan(Player player, String rawTagArg) {
        FileConfiguration msgCfg = getMsgCfg();
        if (extension.getMainStorage().getPlayerClan(player.getName()) != null) {
            ChatUtil.sendMessage(player, msgCfg.getString("messages.already-in-clan"));
            return;
        }

        String strippedKey = ClanData.stripToKey(rawTagArg);
        int minLen = extension.getConfig().getInt("clan-settings.min-name-length", 3);
        int maxLen = extension.getConfig().getInt("clan-settings.max-name-length", 16);

        if (strippedKey.length() < minLen || strippedKey.length() > maxLen) {
            ChatUtil.sendMessage(player, msgCfg.getString("messages.invalid-name-length"),
                    "{min}", minLen, "{max}", maxLen);
            return;
        }

        if (extension.getMainStorage().getClanByNameOrTag(strippedKey) != null) {
            ChatUtil.sendMessage(player, msgCfg.getString("messages.clan-name-taken"));
            return;
        }

        double cost = extension.getConfig().getDouble("clan-settings.create-cost", 280000.0);
        if (!VaultHook.withdrawMoney(player, cost)) {
            ChatUtil.sendMessage(player, msgCfg.getString("messages.not-enough-personal-money"),
                    "{cost}", NumberDateUtil.formatInteger((long) cost));
            return;
        }

        long now = System.currentTimeMillis();
        // По ТЗ при создании клана тег НЕ виден (tagShown = false), пока глава не пропишет /c showtag за 25 000$ в казне!
        ClanData clan = new ClanData(
                strippedKey, rawTagArg, player.getName(), now,
                0.0, 0, false,
                false, false, true,
                null, 0L, 0L
        );
        ClanMember leader = new ClanMember(player.getName(), strippedKey, ClanRole.LEADER, 0, now, now, true, true);
        extension.getMainStorage().registerNewClan(clan, leader);

        extension.getLogsStorage().addLogAsync(strippedKey, player.getName(), 0,
                "&fИгрок &e" + player.getName() + " &fосновал клан " + rawTagArg);

        ChatUtil.sendMessage(player, msgCfg.getString("messages.clan-created"),
                "{tag}", rawTagArg, "{cost}", NumberDateUtil.formatInteger((long) cost));
        extension.getGlowAndRideService().refreshPlayerGlow(player);
    }

    /**
     * 3. Единоразовое включение тега клана для PAPI (/c showtag): только Глава, нужно 25 000$ на балансе клана.
     */
    public void enableShowTag(Player player) {
        FileConfiguration msgCfg = getMsgCfg();
        ClanData clan = extension.getMainStorage().getPlayerClan(player.getName());
        if (clan == null) {
            ChatUtil.sendMessage(player, msgCfg.getString("messages.not-in-clan"));
            return;
        }
        ClanMember member = clan.getMember(player.getName());
        if (member == null || member.getRole() != ClanRole.LEADER) {
            ChatUtil.sendMessage(player, msgCfg.getString("messages.only-leader"));
            return;
        }
        if (clan.isTagShown()) {
            ChatUtil.sendMessage(player, msgCfg.getString("messages.showtag-already-enabled"));
            return;
        }

        double reqBalance = extension.getConfig().getDouble("clan-settings.showtag-required-clan-balance", 25000.0);
        if (clan.getBalance() < reqBalance) {
            ChatUtil.sendMessage(player, msgCfg.getString("messages.showtag-not-enough-balance"));
            return;
        }

        clan.setBalance(clan.getBalance() - reqBalance);
        clan.setTagShown(true);
        extension.getMainStorage().saveClanAsync(clan);

        extension.getLogsStorage().addLogAsync(clan.getClanKey(), player.getName(), 0,
                "&fГлава &e" + player.getName() + " &fактивировал отображение тега клана (&e-25 000$&f)");
        broadcastToClan(clan, msgCfg.getString("messages.showtag-enabled"),
                "{player}", player.getName(), "{tag}", clan.getRawColoredTag());
    }

    /**
     * 4. Переименование клана (/c rename <новоеНазвание>): только Глава, стоимость 7 000 000$.
     */
    public void renameClan(Player player, String newRawTag) {
        FileConfiguration msgCfg = getMsgCfg();
        ClanData clan = extension.getMainStorage().getPlayerClan(player.getName());
        if (clan == null) {
            ChatUtil.sendMessage(player, msgCfg.getString("messages.not-in-clan"));
            return;
        }
        if (clan.getMember(player.getName()).getRole() != ClanRole.LEADER) {
            ChatUtil.sendMessage(player, msgCfg.getString("messages.only-leader"));
            return;
        }

        String stripped = ClanData.stripToKey(newRawTag);
        int minLen = extension.getConfig().getInt("clan-settings.min-name-length", 3);
        int maxLen = extension.getConfig().getInt("clan-settings.max-name-length", 16);
        if (stripped.length() < minLen || stripped.length() > maxLen) {
            ChatUtil.sendMessage(player, msgCfg.getString("messages.invalid-name-length"), "{min}", minLen, "{max}", maxLen);
            return;
        }

        ClanData existing = extension.getMainStorage().getClanByNameOrTag(stripped);
        if (existing != null && !existing.getClanKey().equalsIgnoreCase(clan.getClanKey())) {
            ChatUtil.sendMessage(player, msgCfg.getString("messages.clan-name-taken"));
            return;
        }

        double cost = extension.getConfig().getDouble("clan-settings.rename-cost", 7000000.0);
        if (!VaultHook.withdrawMoney(player, cost)) {
            ChatUtil.sendMessage(player, msgCfg.getString("messages.not-enough-personal-money"),
                    "{cost}", NumberDateUtil.formatInteger((long) cost));
            return;
        }

        clan.setRawColoredTag(newRawTag);
        extension.getMainStorage().saveClanAsync(clan);
        broadcastToClan(clan, msgCfg.getString("messages.clan-renamed"),
                "{player}", player.getName(), "{tag}", newRawTag);
    }

    /**
     * 5. Казна клана: /c balance, /c donate <сумма>, /c take <сумма>.
     */
    public void viewBalance(Player player) {
        FileConfiguration msgCfg = getMsgCfg();
        ClanData clan = extension.getMainStorage().getPlayerClan(player.getName());
        if (clan == null) {
            ChatUtil.sendMessage(player, msgCfg.getString("messages.not-in-clan"));
            return;
        }
        ChatUtil.sendMessage(player, msgCfg.getString("messages.balance-view"),
                "{balance}", NumberDateUtil.formatInteger(Math.round(clan.getBalance())));
    }

    public void donateToClan(Player player, long amount) {
        FileConfiguration msgCfg = getMsgCfg();
        ClanData clan = extension.getMainStorage().getPlayerClan(player.getName());
        if (clan == null) {
            ChatUtil.sendMessage(player, msgCfg.getString("messages.not-in-clan"));
            return;
        }
        if (amount <= 0) return;

        if (!VaultHook.withdrawMoney(player, amount)) {
            ChatUtil.sendMessage(player, msgCfg.getString("messages.not-enough-personal-money"),
                    "{cost}", NumberDateUtil.formatInteger(amount));
            return;
        }

        clan.setBalance(clan.getBalance() + amount);
        extension.getMainStorage().saveClanAsync(clan);

        String fmtAmount = NumberDateUtil.formatInteger(amount);
        broadcastToClan(clan, msgCfg.getString("messages.donate-success"),
                "{player}", player.getName(), "{amount}", fmtAmount);

        int earnedPoints = extension.getSmartPointsService().handleDepositPoints(clan, player, amount);
        if (earnedPoints > 0) {
            ChatUtil.sendMessage(player, msgCfg.getString("messages.donate-points-earned"), "{points}", earnedPoints);
        } else {
            extension.getLogsStorage().addLogAsync(clan.getClanKey(), player.getName(), 0,
                    "&fИгрок &e" + player.getName() + " &fвнес в казну &a+" + fmtAmount + "$");
        }
    }

    public void takeFromClan(Player player, long amount) {
        FileConfiguration msgCfg = getMsgCfg();
        ClanData clan = extension.getMainStorage().getPlayerClan(player.getName());
        if (clan == null) {
            ChatUtil.sendMessage(player, msgCfg.getString("messages.not-in-clan"));
            return;
        }
        if (clan.getMember(player.getName()).getRole() != ClanRole.LEADER) {
            ChatUtil.sendMessage(player, msgCfg.getString("messages.only-leader"));
            return;
        }
        if (amount <= 0) return;

        if (clan.getBalance() < amount) {
            ChatUtil.sendMessage(player, msgCfg.getString("messages.take-not-enough"),
                    "{balance}", NumberDateUtil.formatInteger(Math.round(clan.getBalance())));
            return;
        }

        clan.setBalance(clan.getBalance() - amount);
        extension.getMainStorage().saveClanAsync(clan);
        VaultHook.depositMoney(player, amount);

        String fmtAmount = NumberDateUtil.formatInteger(amount);
        broadcastToClan(clan, msgCfg.getString("messages.take-success"),
                "{player}", player.getName(), "{amount}", fmtAmount);

        int penaltyPoints = extension.getSmartPointsService().handleWithdrawPenaltyPoints(clan, player, amount);
        if (penaltyPoints > 0) {
            ChatUtil.sendMessage(player, msgCfg.getString("messages.take-points-lost"), "{points}", penaltyPoints);
        } else {
            extension.getLogsStorage().addLogAsync(clan.getClanKey(), player.getName(), 0,
                    "&fГлава &e" + player.getName() + " &fснял с казны &c-" + fmtAmount + "$");
        }
    }

    /**
     * 6. Переключатели настроек внутри клана: /c pvpon, /c pvpoff, /c sitallow, /c tpallow.
     */
    public void setClanPvp(Player player, boolean enabled) {
        FileConfiguration msgCfg = getMsgCfg();
        ClanData clan = requireModeratorOrLeader(player);
        if (clan == null) return;

        clan.setPvpEnabled(enabled);
        extension.getMainStorage().saveClanAsync(clan);
        broadcastToClan(clan, msgCfg.getString(enabled ? "messages.pvp-enabled" : "messages.pvp-disabled"),
                "{player}", player.getName());
    }

    public void toggleClanSitAllow(Player player) {
        FileConfiguration msgCfg = getMsgCfg();
        ClanData clan = requireModeratorOrLeader(player);
        if (clan == null) return;

        boolean next = !clan.isSitAllowed();
        clan.setSitAllowed(next);
        extension.getMainStorage().saveClanAsync(clan);
        broadcastToClan(clan, msgCfg.getString(next ? "messages.sit-enabled" : "messages.sit-disabled"),
                "{player}", player.getName());
    }

    public void toggleClanTpAllow(Player player) {
        FileConfiguration msgCfg = getMsgCfg();
        ClanData clan = requireModeratorOrLeader(player);
        if (clan == null) return;

        boolean next = !clan.isTpAllowed();
        clan.setTpAllowed(next);
        extension.getMainStorage().saveClanAsync(clan);
        broadcastToClan(clan, msgCfg.getString(next ? "messages.tp-enabled" : "messages.tp-disabled"),
                "{player}", player.getName());
    }

    /**
     * 7. Дом клана (/c sethome, /c delhome, /c home), объявление (/c bc) и телепорты (/c tp, /c teamtp).
     */
    public void setClanHome(Player player) {
        FileConfiguration msgCfg = getMsgCfg();
        ClanData clan = requireLeader(player);
        if (clan == null) return;

        clan.setHomeLocation(player.getLocation());
        extension.getMainStorage().saveClanAsync(clan);
        broadcastToClan(clan, msgCfg.getString("messages.sethome-success"), "{player}", player.getName());
    }

    public void deleteClanHome(Player player) {
        FileConfiguration msgCfg = getMsgCfg();
        ClanData clan = requireLeader(player);
        if (clan == null) return;

        clan.setHomeLocation(null);
        extension.getMainStorage().saveClanAsync(clan);
        broadcastToClan(clan, msgCfg.getString("messages.delhome-success"), "{player}", player.getName());
    }

    public void teleportToClanHome(Player player) {
        FileConfiguration msgCfg = getMsgCfg();
        ClanData clan = extension.getMainStorage().getPlayerClan(player.getName());
        if (clan == null) {
            ChatUtil.sendMessage(player, msgCfg.getString("messages.not-in-clan"));
            return;
        }
        Location home = clan.getHomeLocation();
        if (home == null || home.getWorld() == null) {
            ChatUtil.sendMessage(player, msgCfg.getString("messages.home-not-set"));
            return;
        }
        if (isPlayerInAntiRelogPvp(player)) {
            ChatUtil.sendMessage(player, msgCfg.getString("messages.tp-in-pvp-error"));
            return;
        }
        player.teleport(home);
        ChatUtil.sendMessage(player, msgCfg.getString("messages.home-teleported"));
    }

    public void broadcastCallToHome(Player player) {
        FileConfiguration msgCfg = getMsgCfg();
        ClanData clan = extension.getMainStorage().getPlayerClan(player.getName());
        if (clan == null) {
            ChatUtil.sendMessage(player, msgCfg.getString("messages.not-in-clan"));
            return;
        }
        broadcastToClan(clan, msgCfg.getString("messages.bc-format"), "{player}", player.getName());
    }

    public void teleportToClanmate(Player player, String targetName) {
        FileConfiguration msgCfg = getMsgCfg();
        ClanData clan = extension.getMainStorage().getPlayerClan(player.getName());
        if (clan == null) {
            ChatUtil.sendMessage(player, msgCfg.getString("messages.not-in-clan"));
            return;
        }
        if (!clan.isTpAllowed()) {
            ChatUtil.sendMessage(player, msgCfg.getString("messages.tp-disabled-in-clan"));
            return;
        }
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null || !target.isOnline() || clan.getMember(target.getName()) == null) {
            ChatUtil.sendMessage(player, "&cСоклановец &e" + targetName + " &cсейчас не в сети!");
            return;
        }
        if (isPlayerInAntiRelogPvp(player) || isPlayerInAntiRelogPvp(target)) {
            ChatUtil.sendMessage(player, msgCfg.getString("messages.tp-in-pvp-error"));
            return;
        }
        player.teleport(target.getLocation());
        ChatUtil.sendMessage(player, msgCfg.getString("messages.tp-success"), "{target}", target.getName());
    }

    public void teleportToAlly(Player player, String targetName) {
        FileConfiguration msgCfg = getMsgCfg();
        ClanData ownClan = extension.getMainStorage().getPlayerClan(player.getName());
        if (ownClan == null) {
            ChatUtil.sendMessage(player, msgCfg.getString("messages.not-in-clan"));
            return;
        }
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null || !target.isOnline()) {
            ChatUtil.sendMessage(player, "&cИгрок &e" + targetName + " &cне в сети!");
            return;
        }
        ClanData targetClan = extension.getMainStorage().getPlayerClan(target.getName());
        if (targetClan == null || !extension.getAlliancesStorage().areAllies(ownClan.getClanKey(), targetClan.getClanKey())) {
            ChatUtil.sendMessage(player, "&cИгрок &e" + target.getName() + " &cне состоит в союзном клане!");
            return;
        }
        if (!extension.getAlliancesStorage().isEffectiveAllyTpAllowed(ownClan.getClanKey(), targetClan.getClanKey())) {
            ChatUtil.sendMessage(player, msgCfg.getString("messages.teamtp-disabled"));
            return;
        }
        if (isPlayerInAntiRelogPvp(player) || isPlayerInAntiRelogPvp(target)) {
            ChatUtil.sendMessage(player, msgCfg.getString("messages.tp-in-pvp-error"));
            return;
        }
        player.teleport(target.getLocation());
        ChatUtil.sendMessage(player, msgCfg.getString("messages.teamtp-success"), "{target}", target.getName());
    }

    /**
     * 8. Приглашения в клан (/clan invite <ник>, /c invite accept, /c invite deny) с JSON-кнопками.
     */
    public void sendInvite(Player sender, String targetNameArg) {
        FileConfiguration msgCfg = getMsgCfg();
        ClanData clan = requireModeratorOrLeader(sender);
        if (clan == null) return;

        Player target = Bukkit.getPlayerExact(targetNameArg);
        if (target == null || !target.isOnline()) {
            ChatUtil.sendMessage(sender, "&cИгрок &e" + targetNameArg + " &cне в сети!");
            return;
        }

        if (extension.getMainStorage().getPlayerClan(target.getName()) != null) {
            ChatUtil.sendMessage(sender, msgCfg.getString("messages.invite-target-in-clan"), "{target}", target.getName());
            return;
        }

        long kickBanRem = extension.getMainStorage().getRemainingKickBanMillis(clan.getClanKey(), target.getName());
        if (kickBanRem > 0L) {
            ChatUtil.sendMessage(sender, msgCfg.getString("messages.invite-kick-cooldown"),
                    "{target}", target.getName(), "{time}", TimeUtil.formatDuration(kickBanRem));
            return;
        }

        int expireSec = extension.getConfig().getInt("clan-settings.invite-expire-seconds", 60);
        PendingInvite inv = new PendingInvite(clan.getClanKey(), sender.getName(), target.getName(),
                System.currentTimeMillis() + (expireSec * 1000L));
        activeInvites.put(target.getName().toLowerCase(), inv);

        ChatUtil.sendMessage(sender, msgCfg.getString("messages.invite-sent-sender"), "{target}", target.getName());

        // Отправляем приглашаемому строку текста + 2 кликабельные кнопки [Принять] и [Отклонить]
        ChatUtil.sendMessage(target, msgCfg.getString("messages.invite-received-line"),
                "{sender}", sender.getName(), "{tag}", clan.getRawColoredTag());

        List<String> accHover = msgCfg.getStringList("buttons.accept-hover").stream()
                .map(s -> s.replace("{cmd}", "/c invite accept")).collect(Collectors.toList());
        List<String> denyHover = msgCfg.getStringList("buttons.deny-hover").stream()
                .map(s -> s.replace("{cmd}", "/c invite deny")).collect(Collectors.toList());

        TextComponent accBtn = InteractiveChatUtil.button(
                msgCfg.getString("buttons.accept-label", "&a&l[Принять]"), "/c invite accept", accHover);
        TextComponent space = InteractiveChatUtil.text("   ");
        TextComponent denyBtn = InteractiveChatUtil.button(
                msgCfg.getString("buttons.deny-label", "&c&l[Отклонить]"), "/c invite deny", denyHover);

        InteractiveChatUtil.sendComponents(target, accBtn, space, denyBtn);
    }

    public void respondToInvite(Player target, boolean accept) {
        FileConfiguration msgCfg = getMsgCfg();
        if (extension.getMainStorage().getPlayerClan(target.getName()) != null) {
            ChatUtil.sendMessage(target, msgCfg.getString("messages.already-in-clan"));
            return;
        }

        PendingInvite inv = activeInvites.remove(target.getName().toLowerCase());
        if (inv == null || inv.isExpired()) {
            ChatUtil.sendMessage(target, msgCfg.getString("messages.invite-expired-try"));
            return;
        }

        ClanData clan = extension.getMainStorage().getClanByNameOrTag(inv.getClanKey());
        if (clan == null) {
            ChatUtil.sendMessage(target, msgCfg.getString("messages.invite-expired-try"));
            return;
        }

        if (!accept) {
            ChatUtil.sendMessage(target, msgCfg.getString("messages.invite-denied-target"));
            Player sender = Bukkit.getPlayerExact(inv.getSenderName());
            if (sender != null && sender.isOnline()) {
                ChatUtil.sendMessage(sender, msgCfg.getString("messages.invite-denied-sender"), "{target}", target.getName());
            }
            return;
        }

        long now = System.currentTimeMillis();
        ClanMember newMember = new ClanMember(target.getName(), clan.getClanKey(), ClanRole.MEMBER, 0, now, now, true, true);
        extension.getMainStorage().addMemberToClan(clan, newMember);
        extension.getLogsStorage().addLogAsync(clan.getClanKey(), target.getName(), 0,
                "&fИгрок &e" + target.getName() + " &fвступил в клан");

        broadcastToClan(clan, msgCfg.getString("messages.invite-joined-clan"), "{player}", target.getName());
        extension.getGlowAndRideService().refreshPlayerGlow(target);
    }

    /**
     * 9. Управление ролями: /c addmoder, /c removemoder, /c leader + /c leaderaccept (КД 5 дней), /c remove + confirm.
     */
    public void setModeratorRole(Player leader, String targetName, boolean promote) {
        FileConfiguration msgCfg = getMsgCfg();
        ClanData clan = requireLeader(leader);
        if (clan == null) return;

        ClanMember target = clan.getMember(targetName);
        if (target == null || target.getRole() == ClanRole.LEADER) {
            ChatUtil.sendMessage(leader, "&cИгрок &e" + targetName + " &cне является участником вашего клана!");
            return;
        }

        target.setRole(promote ? ClanRole.MODERATOR : ClanRole.MEMBER);
        extension.getMainStorage().saveMemberAsync(target);
        broadcastToClan(clan, msgCfg.getString(promote ? "messages.moder-added" : "messages.moder-removed"),
                "{player}", target.getPlayerName());
    }

    public void initiateLeaderTransfer(Player leader, String targetModerName) {
        FileConfiguration msgCfg = getMsgCfg();
        ClanData clan = requireLeader(leader);
        if (clan == null) return;

        long cdSec = extension.getConfig().getLong("clan-settings.leader-transfer-cooldown-seconds", 432000L);
        long elapsed = System.currentTimeMillis() - clan.getLastLeaderTransferMillis();
        if (clan.getLastLeaderTransferMillis() > 0L && elapsed < (cdSec * 1000L)) {
            long rem = (cdSec * 1000L) - elapsed;
            ChatUtil.sendMessage(leader, msgCfg.getString("messages.leader-on-cooldown"),
                    "{time}", TimeUtil.formatDuration(rem));
            return;
        }

        ClanMember targetMember = clan.getMember(targetModerName);
        if (targetMember == null || targetMember.getRole() != ClanRole.MODERATOR) {
            ChatUtil.sendMessage(leader, msgCfg.getString("messages.leader-only-to-moder"));
            return;
        }

        int expSec = extension.getConfig().getInt("clan-settings.leader-transfer-confirm-expire-seconds", 60);
        pendingLeaderTransfers.put(leader.getUniqueId(), new PendingLeaderTransfer(
                clan.getClanKey(), leader.getName(), targetMember.getPlayerName(),
                System.currentTimeMillis() + (expSec * 1000L)
        ));

        ChatUtil.sendMessage(leader, msgCfg.getString("messages.leader-prompt"), "{target}", targetMember.getPlayerName());
    }

    public void confirmLeaderTransfer(Player leader, String targetModerName) {
        FileConfiguration msgCfg = getMsgCfg();
        PendingLeaderTransfer pending = pendingLeaderTransfers.remove(leader.getUniqueId());
        if (pending == null || pending.isExpired() || !pending.getTargetModeratorName().equalsIgnoreCase(targetModerName)) {
            ChatUtil.sendMessage(leader, msgCfg.getString("messages.leader-no-pending"));
            return;
        }

        ClanData clan = requireLeader(leader);
        if (clan == null) return;

        ClanMember oldLeaderMem = clan.getMember(leader.getName());
        ClanMember newLeaderMem = clan.getMember(pending.getTargetModeratorName());
        if (newLeaderMem == null || newLeaderMem.getRole() != ClanRole.MODERATOR) {
            ChatUtil.sendMessage(leader, msgCfg.getString("messages.leader-only-to-moder"));
            return;
        }

        oldLeaderMem.setRole(ClanRole.MODERATOR);
        newLeaderMem.setRole(ClanRole.LEADER);
        clan.setLeaderName(newLeaderMem.getPlayerName());
        clan.setLastLeaderTransferMillis(System.currentTimeMillis());

        extension.getMainStorage().saveMemberAsync(oldLeaderMem);
        extension.getMainStorage().saveMemberAsync(newLeaderMem);
        extension.getMainStorage().saveClanAsync(clan);

        extension.getLogsStorage().addLogAsync(clan.getClanKey(), leader.getName(), 0,
                "&fГлава &e" + leader.getName() + " &fпередал лидерство игроку &a" + newLeaderMem.getPlayerName());

        broadcastToClan(clan, msgCfg.getString("messages.leader-transferred"),
                "{old_leader}", leader.getName(), "{new_leader}", newLeaderMem.getPlayerName());
    }

    public void handleRemoveClanCommand(Player leader, boolean isConfirmArg) {
        FileConfiguration msgCfg = getMsgCfg();
        ClanData clan = requireLeader(leader);
        if (clan == null) return;

        if (!isConfirmArg) {
            int expSec = extension.getConfig().getInt("clan-settings.remove-confirm-expire-seconds", 30);
            pendingRemoveConfirms.put(leader.getUniqueId(), System.currentTimeMillis() + (expSec * 1000L));
            ChatUtil.sendMessage(leader, msgCfg.getString("messages.remove-prompt"));
            return;
        }

        Long exp = pendingRemoveConfirms.remove(leader.getUniqueId());
        if (exp == null || System.currentTimeMillis() >= exp) {
            ChatUtil.sendMessage(leader, msgCfg.getString("messages.remove-no-pending"));
            return;
        }

        broadcastToClan(clan, msgCfg.getString("messages.remove-broadcast-clan"), "{player}", leader.getName());
        deleteClanFullCleanup(clan.getClanKey());
    }

    public void deleteClanFullCleanup(String clanKey) {
        extension.getAlliancesStorage().removeAllAlliancesForClan(clanKey);
        extension.getLogsStorage().deleteAllLogsForClan(clanKey);
        extension.getChestStorage().deleteChestForClan(clanKey);
        extension.getVirtualChestService().closeAndRemoveClanChest(clanKey);
        extension.getMainStorage().deleteClanCompletely(clanKey);
        extension.getGlowAndRideService().refreshAllOnlineGlows();
    }

    public void clearPendingOnQuit(UUID uuid) {
        pendingRemoveConfirms.remove(uuid);
        pendingLeaderTransfers.remove(uuid);
    }

    /**
     * 10. Кик участника (/c kick <ник> <причина>) и выход из клана (/c leave).
     */
    public void kickMember(Player actor, String targetName, String reason) {
        FileConfiguration msgCfg = getMsgCfg();
        ClanData clan = requireModeratorOrLeader(actor);
        if (clan == null) return;

        ClanMember actorMem = clan.getMember(actor.getName());
        ClanMember targetMem = clan.getMember(targetName);
        if (targetMem == null) {
            ChatUtil.sendMessage(actor, "&cИгрок &e" + targetName + " &cне состоит в вашем клане!");
            return;
        }

        if (targetMem.getRole().getWeight() >= actorMem.getRole().getWeight()) {
            ChatUtil.sendMessage(actor, "&cВы не можете изгнать игрока с равной или более высокой ролью!");
            return;
        }

        String realTargetName = targetMem.getPlayerName();
        extension.getMainStorage().removeMemberFromClan(clan, realTargetName);

        long banSec = extension.getConfig().getLong("clan-settings.kick-reinvite-cooldown-seconds", 259200L);
        extension.getMainStorage().setKickReinviteBan(clan.getClanKey(), realTargetName, banSec);

        // Соклановцам — сообщение БЕЗ причины
        broadcastToClan(clan, msgCfg.getString("messages.kick-clan-broadcast"), "{target}", realTargetName);

        // Кикнутому игроку — с причиной (если оффлайн — сохраняем в БД до захода на сервер)
        Player targetOnline = Bukkit.getPlayerExact(realTargetName);
        if (targetOnline != null && targetOnline.isOnline()) {
            ChatUtil.sendMessage(targetOnline, msgCfg.getString("messages.kick-victim-message"), "{reason}", reason);
            extension.getGlowAndRideService().refreshPlayerGlow(targetOnline);
        } else {
            extension.getMainStorage().setOfflineKickNotice(realTargetName, reason);
        }

        extension.getLogsStorage().addLogAsync(clan.getClanKey(), actor.getName(), 0,
                "&fИгрок &e" + actor.getName() + " &fизгнал &c" + realTargetName + " &7(Причина: " + reason + ")");
    }

    public void leaveClan(Player player) {
        FileConfiguration msgCfg = getMsgCfg();
        ClanData clan = extension.getMainStorage().getPlayerClan(player.getName());
        if (clan == null) {
            ChatUtil.sendMessage(player, msgCfg.getString("messages.not-in-clan"));
            return;
        }
        ClanMember mem = clan.getMember(player.getName());
        if (mem.getRole() == ClanRole.LEADER) {
            ChatUtil.sendMessage(player, msgCfg.getString("messages.leave-leader-error"));
            return;
        }

        extension.getMainStorage().removeMemberFromClan(clan, player.getName());
        ChatUtil.sendMessage(player, "&eВы покинули клан " + clan.getRawColoredTag() + "&e.");
        broadcastToClan(clan, msgCfg.getString("messages.leave-broadcast"), "{player}", player.getName());
        extension.getGlowAndRideService().refreshPlayerGlow(player);
    }

    /**
     * 11. Информация о клане (/c info [название]), список участников (/c online [название]), /c top и /c log.
     */
    public void sendClanInfo(CommandSender sender, String optionalClanArg) {
        FileConfiguration msgCfg = getMsgCfg();
        ClanData clan;
        if (optionalClanArg == null || optionalClanArg.isEmpty()) {
            if (!(sender instanceof Player)) {
                ChatUtil.sendMessage(sender, msgCfg.getString("messages.info-usage-not-in-clan"));
                return;
            }
            clan = extension.getMainStorage().getPlayerClan(sender.getName());
            if (clan == null) {
                ChatUtil.sendMessage(sender, msgCfg.getString("messages.info-usage-not-in-clan"));
                return;
            }
        } else {
            clan = extension.getMainStorage().getClanByNameOrTag(optionalClanArg);
            if (clan == null) {
                ChatUtil.sendMessage(sender, msgCfg.getString("messages.clan-not-found"), "{clan}", optionalClanArg);
                return;
            }
        }

        String noneText = msgCfg.getString("status-tags.none", "&7Нет");

        List<String> onlineModers = new ArrayList<>();
        for (ClanMember m : clan.getMembers()) {
            if (m.getRole() == ClanRole.MODERATOR) {
                Player p = Bukkit.getPlayerExact(m.getPlayerName());
                if (p != null && p.isOnline()) {
                    onlineModers.add(p.getName());
                }
            }
        }
        String modersStr = onlineModers.isEmpty() ? noneText : String.join(", ", onlineModers);

        Set<String> allyKeys = extension.getAlliancesStorage().getAllies(clan.getClanKey());
        List<String> allyTags = new ArrayList<>();
        for (String aKey : allyKeys) {
            ClanData aClan = extension.getMainStorage().getClanByNameOrTag(aKey);
            if (aClan != null) allyTags.add(ChatUtil.color(aClan.getRawColoredTag()));
        }
        String alliesStr = allyTags.isEmpty() ? noneText : String.join("&7, ", allyTags);

        int rank = extension.getMainStorage().getClanRankPosition(clan.getClanKey());

        ChatUtil.sendMessages(sender, msgCfg.getStringList("messages.info-format"),
                "{tag}", clan.getRawColoredTag(),
                "{leader}", clan.getLeaderName(),
                "{moders_online}", modersStr,
                "{days}", clan.getAgeInDays(),
                "{rank}", rank,
                "{allies}", alliesStr,
                "{points}", clan.getTotalPoints());
    }

    /**
     * Выводит /c online [клан] в одну сплошную строку:
     * Сначала зеленые ники онлайн-участников (отсортированные по убыванию принесенных очков),
     * затем серые ники оффлайн-участников. При наведении на каждый ник — 2 строки подсказки!
     */
    public void sendClanOnlineList(Player viewer, String optionalClanArg) {
        FileConfiguration msgCfg = getMsgCfg();
        ClanData clan;
        if (optionalClanArg == null || optionalClanArg.isEmpty()) {
            clan = extension.getMainStorage().getPlayerClan(viewer.getName());
            if (clan == null) {
                ChatUtil.sendMessage(viewer, msgCfg.getString("messages.online-usage-not-in-clan"));
                return;
            }
        } else {
            clan = extension.getMainStorage().getClanByNameOrTag(optionalClanArg);
            if (clan == null) {
                ChatUtil.sendMessage(viewer, msgCfg.getString("messages.clan-not-found"), "{clan}", optionalClanArg);
                return;
            }
        }

        List<ClanMember> onlineMembers = new ArrayList<>();
        List<ClanMember> offlineMembers = new ArrayList<>();

        for (ClanMember m : clan.getMembers()) {
            Player p = Bukkit.getPlayerExact(m.getPlayerName());
            if (p != null && p.isOnline()) {
                onlineMembers.add(m);
            } else {
                offlineMembers.add(m);
            }
        }

        onlineMembers.sort((a, b) -> Integer.compare(b.getContributedPoints(), a.getContributedPoints()));
        offlineMembers.sort((a, b) -> Integer.compare(b.getContributedPoints(), a.getContributedPoints()));

        List<ClanMember> ordered = new ArrayList<>(onlineMembers.size() + offlineMembers.size());
        ordered.addAll(onlineMembers);
        ordered.addAll(offlineMembers);

        List<BaseComponent> components = new ArrayList<>();
        String prefixRaw = msgCfg.getString("messages.online-prefix", "&fСписок игроков клана (&e{total}&f): ")
                .replace("{total}", String.valueOf(ordered.size()));
        components.add(InteractiveChatUtil.text(prefixRaw));

        for (int i = 0; i < ordered.size(); i++) {
            ClanMember m = ordered.get(i);
            Player p = Bukkit.getPlayerExact(m.getPlayerName());
            boolean isOnline = (p != null && p.isOnline());

            String coloredNick = (isOnline ? "&a" : "&7") + m.getPlayerName();
            int pts = m.getContributedPoints();

            String line1 = (pts >= 0)
                    ? msgCfg.getString("messages.online-hover-positive", "&a&lВнес в клан +{points} очков")
                    .replace("{points}", String.valueOf(pts))
                    : msgCfg.getString("messages.online-hover-negative", "&c&lВнес в клан {points} очков")
                    .replace("{points}", String.valueOf(pts));

            String line2;
            if (isOnline) {
                line2 = msgCfg.getString("status-tags.online-now", "&aСейчас в сети");
            } else {
                long diffMillis = Math.max(1000L, System.currentTimeMillis() - m.getLastSeenMillis());
                line2 = msgCfg.getString("status-tags.offline-ago", "&cПоследний раз был в сети {time} назад")
                        .replace("{time}", formatFullRussianDuration(diffMillis));
            }

            components.add(InteractiveChatUtil.hoverableText(coloredNick, Arrays.asList(line1, line2)));
            if (i + 1 < ordered.size()) {
                components.add(InteractiveChatUtil.text("&7, "));
            }
        }

        InteractiveChatUtil.sendComponents(viewer, components.toArray(new BaseComponent[0]));
    }

    private String formatFullRussianDuration(long millis) {
        long totalSec = Math.max(1L, millis / 1000L);
        long days = totalSec / 86400L;
        totalSec %= 86400L;
        long hours = totalSec / 3600L;
        totalSec %= 3600L;
        long minutes = totalSec / 60L;
        long seconds = totalSec % 60L;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append(" дней ");
        if (hours > 0) sb.append(hours).append(" часов ");
        if (minutes > 0) sb.append(minutes).append(" минут ");
        sb.append(seconds).append(" секунд");
        return sb.toString().trim();
    }

    public void sendClanTopPage(CommandSender sender, int page) {
        FileConfiguration msgCfg = getMsgCfg();
        List<ClanData> sorted = extension.getMainStorage().getSortedTopClans();
        if (sorted.isEmpty()) {
            ChatUtil.sendMessage(sender, msgCfg.getString("messages.top-empty"));
            return;
        }

        int perPage = 10;
        int maxPages = Math.max(1, (int) Math.ceil((double) sorted.size() / perPage));
        int safePage = Math.max(1, Math.min(page, maxPages));

        ChatUtil.sendMessages(sender, msgCfg.getStringList("messages.top-header"),
                "{page}", safePage, "{max_pages}", maxPages);

        int start = (safePage - 1) * perPage;
        int end = Math.min(sorted.size(), start + perPage);
        String fmt = msgCfg.getString("messages.top-entry");

        for (int i = start; i < end; i++) {
            ClanData c = sorted.get(i);
            ChatUtil.sendMessage(sender, fmt,
                    "{rank}", i + 1,
                    "{tag}", c.getRawColoredTag(),
                    "{leader}", c.getLeaderName(),
                    "{points}", c.getTotalPoints());
        }
    }

    public void sendClanLogs(Player player, String targetNickOrNull, int requestedPage) {
        FileConfiguration msgCfg = getMsgCfg();
        ClanData clan = extension.getMainStorage().getPlayerClan(player.getName());
        if (clan == null) {
            ChatUtil.sendMessage(player, msgCfg.getString("messages.not-in-clan"));
            return;
        }

        int perPage = Math.max(1, extension.getConfig().getInt("clan-settings.log-entries-per-page", 15));

        extension.runTaskAsync(() -> {
            if (targetNickOrNull == null || targetNickOrNull.isEmpty()) {
                int total = extension.getLogsStorage().countGlobalLogs(clan.getClanKey());
                int maxPages = Math.max(1, (int) Math.ceil((double) total / perPage));
                int safePage = Math.max(1, Math.min(requestedPage, maxPages));

                List<ClanLogEntry> entries = total > 0
                        ? extension.getLogsStorage().getGlobalLogsPage(clan.getClanKey(), safePage, perPage)
                        : Collections.emptyList();

                ChatUtil.sendMessages(player, msgCfg.getStringList("messages.log-global-header"),
                        "{tag}", clan.getRawColoredTag(), "{page}", safePage, "{max_pages}", maxPages);

                if (entries.isEmpty()) {
                    ChatUtil.sendMessage(player, msgCfg.getString("messages.log-empty"));
                } else {
                    String fmt = msgCfg.getString("messages.log-entry");
                    for (ClanLogEntry e : entries) {
                        ChatUtil.sendMessage(player, fmt,
                                "{date}", NumberDateUtil.formatDate(e.getTimestamp()),
                                "{message}", e.getMessage());
                    }
                }
            } else {
                ClanMember targetMem = clan.getMember(targetNickOrNull);
                String displayTarget = targetMem != null ? targetMem.getPlayerName() : targetNickOrNull;
                int contributed = targetMem != null ? targetMem.getContributedPoints() : 0;
                String pointsColored = contributed >= 0 ? ("&a&l+" + contributed) : ("&c&l" + contributed);

                int total = extension.getLogsStorage().countPlayerLogs(clan.getClanKey(), displayTarget);
                int maxPages = Math.max(1, (int) Math.ceil((double) total / perPage));
                int safePage = Math.max(1, Math.min(requestedPage, maxPages));

                List<ClanLogEntry> entries = total > 0
                        ? extension.getLogsStorage().getPlayerLogsPage(clan.getClanKey(), displayTarget, safePage, perPage)
                        : Collections.emptyList();

                ChatUtil.sendMessages(player, msgCfg.getStringList("messages.log-player-header"),
                        "{player}", displayTarget,
                        "{points_colored}", pointsColored,
                        "{page}", safePage,
                        "{max_pages}", maxPages);

                if (entries.isEmpty()) {
                    ChatUtil.sendMessage(player, msgCfg.getString("messages.log-empty"));
                } else {
                    String fmt = msgCfg.getString("messages.log-entry");
                    for (ClanLogEntry e : entries) {
                        ChatUtil.sendMessage(player, fmt,
                                "{date}", NumberDateUtil.formatDate(e.getTimestamp()),
                                "{message}", e.getMessage());
                    }
                }
            }
        });
    }

    public ClanData requireLeader(Player player) {
        FileConfiguration msgCfg = getMsgCfg();
        ClanData clan = extension.getMainStorage().getPlayerClan(player.getName());
        if (clan == null) {
            ChatUtil.sendMessage(player, msgCfg.getString("messages.not-in-clan"));
            return null;
        }
        ClanMember m = clan.getMember(player.getName());
        if (m == null || m.getRole() != ClanRole.LEADER) {
            ChatUtil.sendMessage(player, msgCfg.getString("messages.only-leader"));
            return null;
        }
        return clan;
    }

    public ClanData requireModeratorOrLeader(Player player) {
        FileConfiguration msgCfg = getMsgCfg();
        ClanData clan = extension.getMainStorage().getPlayerClan(player.getName());
        if (clan == null) {
            ChatUtil.sendMessage(player, msgCfg.getString("messages.not-in-clan"));
            return null;
        }
        ClanMember m = clan.getMember(player.getName());
        if (m == null || !m.getRole().isAtLeast(ClanRole.MODERATOR)) {
            ChatUtil.sendMessage(player, msgCfg.getString("messages.only-moder-or-leader"));
            return null;
        }
        return clan;
    }
}