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

import java.util.List;

public class BanCommands implements CommandExecutor {
    private final XmplEssExtension extension;

    public BanCommands(XmplEssExtension extension) {
        this.extension = extension;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        FileConfiguration cfg = extension.getCustomConfig("commands/bans.yml").get();
        String cmdName = cmd.getName().toLowerCase();

        if (cmdName.equals("pban")) {
            return handlePermBan(sender, args, cfg);
        } else if (cmdName.equals("ban")) {
            return handleTempBan(sender, args, cfg);
        } else if (cmdName.equals("unban")) {
            return handleUnban(sender, args, cfg);
        } else if (cmdName.equals("kick")) {
            return handleKick(sender, args, cfg);
        }
        return true;
    }

    private boolean handlePermBan(CommandSender sender, String[] args, FileConfiguration cfg) {
        if (!sender.hasPermission(cfg.getString("permissions.permban", "xmpless.ban.perm"))) {
            ChatUtil.sendMessage(sender, cfg.getString("messages.no-permission"));
            return true;
        }
        if (args.length < 1) {
            ChatUtil.sendMessage(sender, cfg.getString("messages.usage-pban"));
            return true;
        }

        String targetName = args[0];
        boolean hasCustomReason = args.length >= 2;
        String reason = hasCustomReason ? joinArgs(args, 1) : cfg.getString("default-pban-reason", "Нарушение правил сервера");

        PunishmentRecord rec = new PunishmentRecord(targetName, sender.getName(), reason, hasCustomReason, -1L);
        extension.getBanStorage().saveBan(rec);

        Player targetOnline = Bukkit.getPlayerExact(targetName);
        if (targetOnline != null) {
            targetOnline.kickPlayer(buildScreen(cfg.getStringList("screens.permban-kick"), sender.getName(), reason, "Навсегда"));
        }

        String bc = cfg.getString("messages.broadcast-pban");
        Bukkit.broadcastMessage(ChatUtil.color(ChatUtil.applyReplacements(bc,
                "{target}", targetName, "{admin}", sender.getName(), "{reason}", reason)));
        return true;
    }

    private boolean handleTempBan(CommandSender sender, String[] args, FileConfiguration cfg) {
        if (!sender.hasPermission(cfg.getString("permissions.tempban", "xmpless.ban.temp"))) {
            ChatUtil.sendMessage(sender, cfg.getString("messages.no-permission"));
            return true;
        }
        if (args.length < 3) {
            ChatUtil.sendMessage(sender, cfg.getString("messages.usage-ban"));
            return true;
        }

        String targetName = args[0];
        long totalDurationMillis = 0L;
        int reasonStartIndex = 1;

        while (reasonStartIndex < args.length && TimeUtil.isTimeToken(args[reasonStartIndex])) {
            totalDurationMillis += TimeUtil.parseTokenMillis(args[reasonStartIndex]);
            reasonStartIndex++;
        }

        if (totalDurationMillis <= 0L) {
            ChatUtil.sendMessage(sender, cfg.getString("messages.usage-ban"));
            return true;
        }

        if (reasonStartIndex >= args.length) {
            ChatUtil.sendMessage(sender, cfg.getString("messages.reason-required"));
            return true;
        }

        String reason = joinArgs(args, reasonStartIndex);
        long expireAt = System.currentTimeMillis() + totalDurationMillis;
        String formattedTime = TimeUtil.formatDuration(totalDurationMillis);

        PunishmentRecord rec = new PunishmentRecord(targetName, sender.getName(), reason, true, expireAt);
        extension.getBanStorage().saveBan(rec);

        Player targetOnline = Bukkit.getPlayerExact(targetName);
        if (targetOnline != null) {
            targetOnline.kickPlayer(buildScreen(cfg.getStringList("screens.tempban-kick"), sender.getName(), reason, formattedTime));
        }

        String bc = cfg.getString("messages.broadcast-tempban");
        Bukkit.broadcastMessage(ChatUtil.color(ChatUtil.applyReplacements(bc,
                "{target}", targetName, "{admin}", sender.getName(), "{time}", formattedTime, "{reason}", reason)));
        return true;
    }

    private boolean handleUnban(CommandSender sender, String[] args, FileConfiguration cfg) {
        if (!sender.hasPermission(cfg.getString("permissions.unban", "xmpless.unban"))) {
            ChatUtil.sendMessage(sender, cfg.getString("messages.no-permission"));
            return true;
        }
        if (args.length < 1) {
            ChatUtil.sendMessage(sender, cfg.getString("messages.usage-unban"));
            return true;
        }

        String targetName = args[0];
        PunishmentRecord existing = extension.getBanStorage().getBan(targetName);
        if (existing == null) {
            ChatUtil.sendMessage(sender, cfg.getString("messages.not-banned"), "{player}", targetName);
            return true;
        }

        String unbanReason = args.length >= 2 ? joinArgs(args, 1) : "";
        extension.getBanStorage().removeBan(targetName);

        // Сохраняем уведомление для игрока, чтобы при следующем заходе ему написало кто и почему его разбанил
        UserRecord uRec = extension.getUserStorage().getOrCreate(targetName, "");
        extension.getUserStorage().saveUserRecord(new UserRecord(
                uRec.name, uRec.uuid, uRec.lastSeen,
                sender.getName(), unbanReason, false,
                uRec.pendingOfflineMuteNotice, uRec.pendingTakeCmdNotice
        ));

        String bc = cfg.getString("messages.broadcast-unban");
        Bukkit.broadcastMessage(ChatUtil.color(ChatUtil.applyReplacements(bc,
                "{target}", targetName, "{admin}", sender.getName())));
        return true;
    }

    private boolean handleKick(CommandSender sender, String[] args, FileConfiguration cfg) {
        if (!sender.hasPermission(cfg.getString("permissions.kick", "xmpless.kick"))) {
            ChatUtil.sendMessage(sender, cfg.getString("messages.no-permission"));
            return true;
        }
        if (args.length < 1) {
            ChatUtil.sendMessage(sender, cfg.getString("messages.usage-kick"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            ChatUtil.sendMessage(sender, "&cИгрок не в сети!");
            return true;
        }

        String reason = args.length >= 2 ? joinArgs(args, 1) : cfg.getString("default-kick-reason", "Кикнут администратором");
        target.kickPlayer(buildScreen(cfg.getStringList("screens.kick-screen"), sender.getName(), reason, ""));

        String bc = cfg.getString("messages.broadcast-kick");
        Bukkit.broadcastMessage(ChatUtil.color(ChatUtil.applyReplacements(bc,
                "{target}", target.getName(), "{admin}", sender.getName(), "{reason}", reason)));
        return true;
    }

    public static String buildScreen(List<String> lines, String admin, String reason, String time) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            String line = ChatUtil.applyReplacements(lines.get(i), "{admin}", admin, "{reason}", reason, "{time}", time);
            sb.append(ChatUtil.color(line));
            if (i + 1 < lines.size()) sb.append("\n");
        }
        return sb.toString();
    }

    private String joinArgs(String[] args, int start) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            sb.append(args[i]).append(i + 1 < args.length ? " " : "");
        }
        return sb.toString();
    }
}