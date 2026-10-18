package me.xmpl.nexusclans.service;

import me.xmpl.lib.util.ChatUtil;
import me.xmpl.lib.util.InteractiveChatUtil;
import me.xmpl.nexusclans.NexusClansExtension;
import me.xmpl.nexusclans.model.ClanData;
import me.xmpl.nexusclans.model.PendingAllianceRequest;
import me.xmpl.nexusclans.storage.ClanAlliancesDatabaseStorage.ClanTeamSettings;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ClanAllianceService {
    private final NexusClansExtension extension;
    // Запросы союза: targetClanKey -> PendingAllianceRequest
    private final Map<String, PendingAllianceRequest> pendingRequests = new ConcurrentHashMap<>();

    public ClanAllianceService(NexusClansExtension extension) {
        this.extension = extension;
    }

    public void sendAllianceRequest(Player leader, String targetClanArg) {
        if (!extension.getModuleService().isAlliancesEnabled()) return;
        FileConfiguration msgCfg = extension.getClanCoreService().getMsgCfg();
        ClanData ownClan = extension.getClanCoreService().requireLeader(leader);
        if (ownClan == null) return;

        ClanData targetClan = extension.getMainStorage().getClanByNameOrTag(targetClanArg);
        if (targetClan == null || targetClan.getClanKey().equalsIgnoreCase(ownClan.getClanKey())) {
            ChatUtil.sendMessage(leader, msgCfg.getString("messages.clan-not-found"), "{clan}", targetClanArg);
            return;
        }

        if (extension.getAlliancesStorage().areAllies(ownClan.getClanKey(), targetClan.getClanKey())) {
            ChatUtil.sendMessage(leader, "&cВы уже находитесь в союзе с этим кланом!");
            return;
        }

        int maxAllies = extension.getCustomConfig("alliances.yml").get().getInt("settings.max-allies-per-clan", 2);
        if (extension.getAlliancesStorage().getAllies(ownClan.getClanKey()).size() >= maxAllies
                || extension.getAlliancesStorage().getAllies(targetClan.getClanKey()).size() >= maxAllies) {
            ChatUtil.sendMessage(leader, msgCfg.getString("messages.alliance-max-reached"));
            return;
        }

        Player targetLeader = Bukkit.getPlayerExact(targetClan.getLeaderName());
        if (targetLeader == null || !targetLeader.isOnline()) {
            ChatUtil.sendMessage(leader, msgCfg.getString("messages.alliance-leader-offline"),
                    "{clan}", targetClan.getRawColoredTag());
            return;
        }

        int expSec = extension.getCustomConfig("alliances.yml").get().getInt("settings.request-expire-seconds", 60);
        pendingRequests.put(targetClan.getClanKey(), new PendingAllianceRequest(
                ownClan.getClanKey(), targetClan.getClanKey(), System.currentTimeMillis() + (expSec * 1000L)
        ));

        ChatUtil.sendMessage(leader, msgCfg.getString("messages.alliance-sent"), "{tag}", targetClan.getRawColoredTag());
        ChatUtil.sendMessage(targetLeader, msgCfg.getString("messages.alliance-received-line"), "{tag}", ownClan.getRawColoredTag());

        List<String> accHover = msgCfg.getStringList("buttons.accept-hover").stream()
                .map(s -> s.replace("{cmd}", "/c teamaccept")).collect(Collectors.toList());
        List<String> denyHover = msgCfg.getStringList("buttons.deny-hover").stream()
                .map(s -> s.replace("{cmd}", "/c teamdeny")).collect(Collectors.toList());

        TextComponent accBtn = InteractiveChatUtil.button(
                msgCfg.getString("buttons.accept-label", "&a&l[Принять]"), "/c teamaccept", accHover);
        TextComponent space = InteractiveChatUtil.text("   ");
        TextComponent denyBtn = InteractiveChatUtil.button(
                msgCfg.getString("buttons.deny-label", "&c&l[Отклонить]"), "/c teamdeny", denyHover);

        InteractiveChatUtil.sendComponents(targetLeader, accBtn, space, denyBtn);
    }

    public void respondToAllianceRequest(Player targetLeader, boolean accept) {
        FileConfiguration msgCfg = extension.getClanCoreService().getMsgCfg();
        ClanData targetClan = extension.getClanCoreService().requireLeader(targetLeader);
        if (targetClan == null) return;

        PendingAllianceRequest req = pendingRequests.remove(targetClan.getClanKey());
        if (req == null || req.isExpired()) {
            ChatUtil.sendMessage(targetLeader, msgCfg.getString("messages.invite-expired-try"));
            return;
        }

        ClanData requesterClan = extension.getMainStorage().getClanByNameOrTag(req.getRequesterClanKey());
        if (requesterClan == null) {
            ChatUtil.sendMessage(targetLeader, msgCfg.getString("messages.invite-expired-try"));
            return;
        }

        if (!accept) {
            ChatUtil.sendMessage(targetLeader, "&eВы отклонили предложение союза от клана " + requesterClan.getRawColoredTag());
            Player reqLeader = Bukkit.getPlayerExact(requesterClan.getLeaderName());
            if (reqLeader != null && reqLeader.isOnline()) {
                ChatUtil.sendMessage(reqLeader, "&cГлава клана " + targetClan.getRawColoredTag() + " &cотклонил запрос на союз.");
            }
            return;
        }

        int maxAllies = extension.getCustomConfig("alliances.yml").get().getInt("settings.max-allies-per-clan", 2);
        if (extension.getAlliancesStorage().getAllies(requesterClan.getClanKey()).size() >= maxAllies
                || extension.getAlliancesStorage().getAllies(targetClan.getClanKey()).size() >= maxAllies) {
            ChatUtil.sendMessage(targetLeader, msgCfg.getString("messages.alliance-max-reached"));
            return;
        }

        // Собираем сторонних союзников обоих кланов ДО добавления нового союза
        Set<String> thirdPartyAllies = new HashSet<>();
        thirdPartyAllies.addAll(extension.getAlliancesStorage().getAllies(requesterClan.getClanKey()));
        thirdPartyAllies.addAll(extension.getAlliancesStorage().getAllies(targetClan.getClanKey()));
        thirdPartyAllies.remove(requesterClan.getClanKey());
        thirdPartyAllies.remove(targetClan.getClanKey());

        extension.getAlliancesStorage().createAlliance(requesterClan.getClanKey(), targetClan.getClanKey());

        // Оповещаем оба вступивших в союз клана
        extension.getClanCoreService().broadcastToClan(requesterClan,
                msgCfg.getString("messages.alliance-created-both"), "{tag}", targetClan.getRawColoredTag());
        extension.getClanCoreService().broadcastToClan(targetClan,
                msgCfg.getString("messages.alliance-created-both"), "{tag}", requesterClan.getRawColoredTag());

        // Оповещаем другие союзные кланы
        for (String thirdKey : thirdPartyAllies) {
            ClanData thirdClan = extension.getMainStorage().getClanByNameOrTag(thirdKey);
            if (thirdClan != null) {
                extension.getClanCoreService().broadcastToClan(thirdClan,
                        msgCfg.getString("messages.alliance-created-third-party"),
                        "{tag1}", requesterClan.getRawColoredTag(),
                        "{tag2}", targetClan.getRawColoredTag());
            }
        }

        extension.getGlowAndRideService().refreshAllOnlineGlows();
    }

    public void breakAlliance(Player leader, String targetClanArg) {
        FileConfiguration msgCfg = extension.getClanCoreService().getMsgCfg();
        ClanData ownClan = extension.getClanCoreService().requireLeader(leader);
        if (ownClan == null) return;

        ClanData targetClan = extension.getMainStorage().getClanByNameOrTag(targetClanArg);
        if (targetClan == null || !extension.getAlliancesStorage().areAllies(ownClan.getClanKey(), targetClan.getClanKey())) {
            ChatUtil.sendMessage(leader, "&cВаш клан не состоит в союзе с &e" + targetClanArg + "&c!");
            return;
        }

        extension.getAlliancesStorage().breakAlliance(ownClan.getClanKey(), targetClan.getClanKey());

        extension.getClanCoreService().broadcastToClan(ownClan,
                msgCfg.getString("messages.alliance-broken-self"), "{tag}", targetClan.getRawColoredTag());
        extension.getClanCoreService().broadcastToClan(targetClan,
                msgCfg.getString("messages.alliance-broken-target"), "{tag}", ownClan.getRawColoredTag());

        Set<String> thirdPartyAllies = new HashSet<>();
        thirdPartyAllies.addAll(extension.getAlliancesStorage().getAllies(ownClan.getClanKey()));
        thirdPartyAllies.addAll(extension.getAlliancesStorage().getAllies(targetClan.getClanKey()));
        thirdPartyAllies.remove(ownClan.getClanKey());
        thirdPartyAllies.remove(targetClan.getClanKey());

        for (String thirdKey : thirdPartyAllies) {
            ClanData thirdClan = extension.getMainStorage().getClanByNameOrTag(thirdKey);
            if (thirdClan != null) {
                extension.getClanCoreService().broadcastToClan(thirdClan,
                        msgCfg.getString("messages.alliance-broken-third-party"),
                        "{tag1}", ownClan.getRawColoredTag(),
                        "{tag2}", targetClan.getRawColoredTag());
            }
        }

        extension.getGlowAndRideService().refreshAllOnlineGlows();
    }

    public void setTeamPvp(Player actor, boolean enabled) {
        FileConfiguration msgCfg = extension.getClanCoreService().getMsgCfg();
        ClanData ownClan = extension.getClanCoreService().requireModeratorOrLeader(actor);
        if (ownClan == null) return;

        ClanTeamSettings settings = extension.getAlliancesStorage().getSettings(ownClan.getClanKey());
        settings.teamPvpEnabled = enabled;
        extension.getAlliancesStorage().saveSettingsAsync(ownClan.getClanKey());

        extension.getClanCoreService().broadcastToClan(ownClan,
                msgCfg.getString(enabled ? "messages.teampvp-enabled-own" : "messages.teampvp-disabled-own"));

        String allyMsg = msgCfg.getString(enabled ? "messages.teampvp-enabled-allies" : "messages.teampvp-disabled-allies");
        for (String allyKey : extension.getAlliancesStorage().getAllies(ownClan.getClanKey())) {
            ClanData ally = extension.getMainStorage().getClanByNameOrTag(allyKey);
            if (ally != null) {
                extension.getClanCoreService().broadcastToClan(ally, allyMsg, "{tag}", ownClan.getRawColoredTag());
            }
        }
    }

    public void toggleTeamSit(Player actor) {
        FileConfiguration msgCfg = extension.getClanCoreService().getMsgCfg();
        ClanData ownClan = extension.getClanCoreService().requireModeratorOrLeader(actor);
        if (ownClan == null) return;

        ClanTeamSettings settings = extension.getAlliancesStorage().getSettings(ownClan.getClanKey());
        settings.teamSitAllowed = !settings.teamSitAllowed;
        extension.getAlliancesStorage().saveSettingsAsync(ownClan.getClanKey());

        String stateStr = settings.teamSitAllowed
                ? msgCfg.getString("status-tags.yes", "&aДа")
                : msgCfg.getString("status-tags.no", "&cНет");

        extension.getClanCoreService().broadcastToClan(ownClan,
                msgCfg.getString("messages.teamsit-toggled-own"), "{state}", stateStr);

        for (String allyKey : extension.getAlliancesStorage().getAllies(ownClan.getClanKey())) {
            ClanData ally = extension.getMainStorage().getClanByNameOrTag(allyKey);
            if (ally != null) {
                extension.getClanCoreService().broadcastToClan(ally,
                        msgCfg.getString("messages.teamsit-toggled-allies"),
                        "{tag}", ownClan.getRawColoredTag(), "{state}", stateStr);
            }
        }
    }

    public void toggleTeamTp(Player actor) {
        FileConfiguration msgCfg = extension.getClanCoreService().getMsgCfg();
        ClanData ownClan = extension.getClanCoreService().requireModeratorOrLeader(actor);
        if (ownClan == null) return;

        ClanTeamSettings settings = extension.getAlliancesStorage().getSettings(ownClan.getClanKey());
        settings.teamTpAllowed = !settings.teamTpAllowed;
        extension.getAlliancesStorage().saveSettingsAsync(ownClan.getClanKey());

        String stateStr = settings.teamTpAllowed
                ? msgCfg.getString("status-tags.yes", "&aДа")
                : msgCfg.getString("status-tags.no", "&cНет");

        extension.getClanCoreService().broadcastToClan(ownClan,
                msgCfg.getString("messages.teamtp-toggled-own"), "{state}", stateStr);

        for (String allyKey : extension.getAlliancesStorage().getAllies(ownClan.getClanKey())) {
            ClanData ally = extension.getMainStorage().getClanByNameOrTag(allyKey);
            if (ally != null) {
                extension.getClanCoreService().broadcastToClan(ally,
                        msgCfg.getString("messages.teamtp-toggled-allies"),
                        "{tag}", ownClan.getRawColoredTag(), "{state}", stateStr);
            }
        }
    }
}