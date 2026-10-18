package me.xmpl.ess.command;

import me.xmpl.ess.XmplEssExtension;
import me.xmpl.ess.model.PunishmentRecord;
import me.xmpl.ess.model.UserRecord;
import me.xmpl.lib.util.ChatUtil;
import me.xmpl.lib.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public class SeenCommand implements CommandExecutor {
    private final XmplEssExtension extension;

    public SeenCommand(XmplEssExtension extension) {
        this.extension = extension;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        FileConfiguration cfg = extension.getCustomConfig("commands/seen.yml").get();
        if (!sender.hasPermission(cfg.getString("permission", "xmpless.seen"))) {
            ChatUtil.sendMessage(sender, cfg.getString("no-permission"));
            return true;
        }
        if (args.length < 1) {
            ChatUtil.sendMessage(sender, cfg.getString("usage"));
            return true;
        }

        String targetName = args[0];
        Player online = Bukkit.getPlayerExact(targetName);
        UserRecord uRec = extension.getUserStorage().getUser(targetName);

        if (online == null && uRec == null) {
            ChatUtil.sendMessage(sender, cfg.getString("player-never-played"), "{player}", targetName);
            return true;
        }

        String displayPlayer = online != null ? online.getName() : uRec.name;

        // 1. Онлайн или когда был в сети
        String onlineStatus;
        if (online != null && online.isOnline()) {
            String dur = TimeUtil.formatDuration(extension.getUserStorage().getSessionDuration(online.getUniqueId()));
            onlineStatus = cfg.getString("status-online").replace("{time}", dur);
        } else {
            long diff = System.currentTimeMillis() - (uRec != null ? uRec.lastSeen : System.currentTimeMillis());
            onlineStatus = cfg.getString("status-offline").replace("{time}", TimeUtil.formatDuration(diff));
        }

        // 2. Статус бана
        PunishmentRecord ban = extension.getBanStorage().getBan(displayPlayer);
        String banStatus;
        if (ban == null || ban.isExpired()) {
            banStatus = cfg.getString("status-no", "&a&lНет");
        } else if (ban.isPermanent()) {
            banStatus = ChatUtil.applyReplacements(cfg.getString("banned-perm-info"),
                    "{admin}", ban.getAdminName(), "{reason}", ban.getReason());
        } else {
            banStatus = ChatUtil.applyReplacements(cfg.getString("banned-temp-info"),
                    "{admin}", ban.getAdminName(), "{reason}", ban.getReason(),
                    "{time}", TimeUtil.formatDuration(ban.getRemainingMillis()));
        }

        // 3. Статус мута
        PunishmentRecord mute = extension.getMuteStorage().getActiveMute(displayPlayer);
        String muteStatus;
        if (mute == null) {
            muteStatus = cfg.getString("status-no", "&a&lНет");
        } else if (mute.isPermanent()) {
            muteStatus = ChatUtil.applyReplacements(cfg.getString("muted-perm-info"),
                    "{admin}", mute.getAdminName(), "{reason}", mute.getReason());
        } else {
            muteStatus = ChatUtil.applyReplacements(cfg.getString("muted-temp-info"),
                    "{admin}", mute.getAdminName(), "{reason}", mute.getReason(),
                    "{time}", TimeUtil.formatDuration(mute.getRemainingMillis()));
        }

        // 4. Статус Cmd (Есть ли право на мут)
        PunishmentRecord takenCmd = extension.getMuteStorage().getTakenRightRecord(displayPlayer);
        String cmdStatus;
        if (takenCmd == null) {
            cmdStatus = cfg.getString("status-cmd-yes", "&a&lДа");
        } else {
            cmdStatus = ChatUtil.applyReplacements(cfg.getString("cmd-taken-info"),
                    "{admin}", takenCmd.getAdminName(), "{reason}", takenCmd.getReason());
        }

        ChatUtil.sendMessages(sender, cfg.getStringList("format"),
                "{player}", displayPlayer,
                "{online_status}", onlineStatus,
                "{ban_status}", banStatus,
                "{mute_status}", muteStatus,
                "{cmd_status}", cmdStatus);
        return true;
    }
}