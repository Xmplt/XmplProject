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

public class MuteAndNbcCommands implements CommandExecutor {
    private final XmplEssExtension extension;

    public MuteAndNbcCommands(XmplEssExtension extension) {
        this.extension = extension;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        FileConfiguration cfg = extension.getCustomConfig("commands/mutes.yml").get();
        String name = cmd.getName().toLowerCase();

        switch (name) {
            case "mute": return handleTempMute(sender, args, cfg);
            case "pmute": return handlePermMute(sender, args, cfg);
            case "unmute": return handleUnmute(sender, args, cfg);
            case "takecmd": return handleTakeCmd(sender, args, cfg);
            case "nbc": return handleNbc(sender, args, cfg);
            default: return true;
        }
    }

    private boolean handleTempMute(CommandSender sender, String[] args, FileConfiguration cfg) {
        if (!sender.hasPermission(cfg.getString("permissions.tempmute", "xmpless.mute.temp"))) {
            ChatUtil.sendMessage(sender, cfg.getString("messages.no-permission"));
            return true;
        }
        // Проверяем, не отобрано ли у игрока право мутить через /takecmd
        if (sender instanceof Player && extension.getMuteStorage().isMuteRightTaken(sender.getName())) {
            ChatUtil.sendMessage(sender, cfg.getString("messages.mute-right-taken-error"));
            return true;
        }
        if (args.length < 3) {
            ChatUtil.sendMessage(sender, cfg.getString("messages.usage-mute"));
            return true;
        }

        String targetName = args[0];
        long duration = 0L;
        int idx = 1;
        while (idx < args.length && TimeUtil.isTimeToken(args[idx])) {
            duration += TimeUtil.parseTokenMillis(args[idx]);
            idx++;
        }

        if (duration <= 0L || idx >= args.length) {
            ChatUtil.sendMessage(sender, cfg.getString("messages.usage-mute"));
            return true;
        }

        String reason = joinArgs(args, idx);
        String timeStr = TimeUtil.formatDuration(duration);
        PunishmentRecord rec = new PunishmentRecord(targetName, sender.getName(), reason, true, System.currentTimeMillis() + duration);
        extension.getMuteStorage().saveMute(rec);

        Bukkit.broadcastMessage(ChatUtil.color(ChatUtil.applyReplacements(cfg.getString("messages.broadcast-tempmute"),
                "{target}", targetName, "{admin}", sender.getName(), "{time}", timeStr, "{reason}", reason)));

        notifyMutedTarget(targetName, sender.getName(), reason, timeStr, cfg);
        return true;
    }

    private boolean handlePermMute(CommandSender sender, String[] args, FileConfiguration cfg) {
        if (!sender.hasPermission(cfg.getString("permissions.permmute", "xmpless.mute.perm"))) {
            ChatUtil.sendMessage(sender, cfg.getString("messages.no-permission"));
            return true;
        }
        if (args.length < 1) {
            ChatUtil.sendMessage(sender, cfg.getString("messages.usage-pmute"));
            return true;
        }

        String targetName = args[0];
        boolean custom = args.length >= 2;
        String reason = custom ? joinArgs(args, 1) : cfg.getString("default-pmute-reason", "Нарушение правил чата");

        PunishmentRecord rec = new PunishmentRecord(targetName, sender.getName(), reason, custom, -1L);
        extension.getMuteStorage().saveMute(rec);

        Bukkit.broadcastMessage(ChatUtil.color(ChatUtil.applyReplacements(cfg.getString("messages.broadcast-permmute"),
                "{target}", targetName, "{admin}", sender.getName(), "{reason}", reason)));

        notifyMutedTarget(targetName, sender.getName(), reason, "Навсегда", cfg);
        return true;
    }

    private void notifyMutedTarget(String targetName, String admin, String reason, String timeStr, FileConfiguration cfg) {
        String personalMsg = ChatUtil.applyReplacements(cfg.getString("messages.target-muted-notify"),
                "{admin}", admin, "{reason}", reason, "{time}", timeStr);

        Player online = Bukkit.getPlayerExact(targetName);
        if (online != null) {
            ChatUtil.sendMessage(online, personalMsg);
        } else {
            UserRecord uRec = extension.getUserStorage().getOrCreate(targetName, "");
            extension.getUserStorage().saveUserRecord(new UserRecord(
                    uRec.name, uRec.uuid, uRec.lastSeen, uRec.pendingUnbanAdmin, uRec.pendingUnbanReason,
                    uRec.pendingExpiredBanNotice, personalMsg, uRec.pendingTakeCmdNotice
            ));
        }
    }

    private boolean handleUnmute(CommandSender sender, String[] args, FileConfiguration cfg) {
        if (!sender.hasPermission(cfg.getString("permissions.unmute", "xmpless.unmute"))) {
            ChatUtil.sendMessage(sender, cfg.getString("messages.no-permission"));
            return true;
        }
        if (args.length < 1) {
            ChatUtil.sendMessage(sender, cfg.getString("messages.usage-unmute"));
            return true;
        }

        String targetName = args[0];
        extension.getMuteStorage().removeMute(targetName);
        ChatUtil.sendMessage(sender, cfg.getString("messages.unmute-success"), "{target}", targetName);

        Player online = Bukkit.getPlayerExact(targetName);
        if (online != null) {
            ChatUtil.sendMessage(online, cfg.getString("messages.target-unmuted-notify"), "{admin}", sender.getName());
        }
        return true;
    }

    private boolean handleTakeCmd(CommandSender sender, String[] args, FileConfiguration cfg) {
        if (!sender.hasPermission(cfg.getString("permissions.takecmd", "xmpless.takecmd"))) {
            ChatUtil.sendMessage(sender, cfg.getString("messages.no-permission"));
            return true;
        }
        if (args.length < 1) {
            ChatUtil.sendMessage(sender, cfg.getString("messages.usage-takecmd"));
            return true;
        }

        String targetName = args[0];
        // Работает как переключатель: если уже отобраны права — возвращаем право на обычный мут!
        if (extension.getMuteStorage().isMuteRightTaken(targetName)) {
            extension.getMuteStorage().restoreMuteRight(targetName);
            ChatUtil.sendMessage(sender, cfg.getString("messages.takecmd-restored-sender"), "{target}", targetName);

            String targetMsg = ChatUtil.applyReplacements(cfg.getString("messages.takecmd-restored-target"), "{admin}", sender.getName());
            deliverOrQueueTakeCmdNotice(targetName, targetMsg);
            return true;
        }

        String reason = args.length >= 2 ? joinArgs(args, 1) : cfg.getString("default-takecmd-reason", "Злоупотребление правами на мут");
        extension.getMuteStorage().setMuteRightTaken(targetName, sender.getName(), reason);
        ChatUtil.sendMessage(sender, cfg.getString("messages.takecmd-taken-sender"), "{target}", targetName, "{reason}", reason);

        String targetMsg = ChatUtil.applyReplacements(cfg.getString("messages.takecmd-taken-target"),
                "{admin}", sender.getName(), "{reason}", reason);
        deliverOrQueueTakeCmdNotice(targetName, targetMsg);
        return true;
    }

    private void deliverOrQueueTakeCmdNotice(String targetName, String message) {
        Player online = Bukkit.getPlayerExact(targetName);
        if (online != null) {
            ChatUtil.sendMessage(online, message);
        } else {
            UserRecord uRec = extension.getUserStorage().getOrCreate(targetName, "");
            extension.getUserStorage().saveUserRecord(new UserRecord(
                    uRec.name, uRec.uuid, uRec.lastSeen, uRec.pendingUnbanAdmin, uRec.pendingUnbanReason,
                    uRec.pendingExpiredBanNotice, uRec.pendingOfflineMuteNotice, message
            ));
        }
    }

    private boolean handleNbc(CommandSender sender, String[] args, FileConfiguration cfg) {
        if (!sender.hasPermission(cfg.getString("permissions.nbc", "xmpless.nbc"))) {
            ChatUtil.sendMessage(sender, cfg.getString("messages.no-permission"));
            return true;
        }

        // Проверка наличия мута у игрока
        PunishmentRecord mute = extension.getMuteStorage().getActiveMute(sender.getName());
        if (mute != null) {
            if (mute.isPermanent()) {
                ChatUtil.sendMessage(sender, cfg.getString("messages.chat-blocked-perm"),
                        "{admin}", mute.getAdminName(), "{reason}", mute.getReason());
            } else {
                ChatUtil.sendMessage(sender, cfg.getString("messages.chat-blocked-temp"),
                        "{admin}", mute.getAdminName(), "{reason}", mute.getReason(),
                        "{time}", TimeUtil.formatDuration(mute.getRemainingMillis()));
            }
            return true;
        }

        if (args.length < 1) {
            ChatUtil.sendMessage(sender, cfg.getString("messages.usage-nbc"));
            return true;
        }

        String text = joinArgs(args, 0);
        String formatted = ChatUtil.applyReplacements(cfg.getString("messages.nbc-format", "&e&l{message} &7(написал: &f{player}&7)"),
                "{message}", text, "{player}", sender.getName());
        Bukkit.broadcastMessage(ChatUtil.color(formatted));
        return true;
    }

    private String joinArgs(String[] args, int start) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            sb.append(args[i]).append(i + 1 < args.length ? " " : "");
        }
        return sb.toString();
    }
}