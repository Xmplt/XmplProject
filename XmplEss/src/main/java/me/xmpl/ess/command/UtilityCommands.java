package me.xmpl.ess.command;

import me.xmpl.ess.XmplEssExtension;
import me.xmpl.lib.util.ChatUtil;
import me.xmpl.lib.util.PacketGlowUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class UtilityCommands implements CommandExecutor {
    private final XmplEssExtension extension;

    public UtilityCommands(XmplEssExtension extension) {
        this.extension = extension;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player player = (Player) sender;
        String name = cmd.getName().toLowerCase();

        switch (name) {
            case "invsee": return handleInvSee(player, args);
            case "near": return handleNear(player);
            case "rtp": return handleRtp(player);
            default: return true;
        }
    }

    private boolean handleInvSee(Player player, String[] args) {
        FileConfiguration cfg = extension.getCustomConfig("commands/invsee.yml").get();
        if (!player.hasPermission(cfg.getString("permissions.view", "xmpless.invsee.view"))) {
            ChatUtil.sendMessage(player, cfg.getString("messages.no-permission"));
            return true;
        }
        if (args.length < 1) {
            ChatUtil.sendMessage(player, cfg.getString("messages.usage"));
            return true;
        }
        extension.getInvSeeService().openInvSee(player, args[0]);
        return true;
    }

    private boolean handleNear(Player player) {
        FileConfiguration cfg = extension.getCustomConfig("commands/near.yml").get();
        if (!player.hasPermission(cfg.getString("permissions.use", "xmpless.near.use"))) {
            ChatUtil.sendMessage(player, cfg.getString("messages.no-permission"));
            return true;
        }

        int maxRadius = cfg.getInt("settings.default-radius", 100);
        String prefix = cfg.getString("permissions.range-prefix", "xmpless.near.range.");
        ConfigurationSection rangesSec = cfg.getConfigurationSection("settings.ranges");
        if (rangesSec != null) {
            for (String groupKey : rangesSec.getKeys(false)) {
                int r = rangesSec.getInt(groupKey);
                if (player.hasPermission(prefix + groupKey) && r > maxRadius) {
                    maxRadius = r;
                }
            }
        }

        String immunityPerm = cfg.getString("permissions.immunity", "xmpless.near.exempt");
        List<Player> foundTargets = new ArrayList<>();
        List<String> lines = new ArrayList<>();

        for (Player target : player.getWorld().getPlayers()) {
            if (target.equals(player)) continue;
            if (!extension.getVanishService().canSee(player, target)) continue;
            if (target.hasPermission(immunityPerm)) continue;

            double dist = player.getLocation().distance(target.getLocation());
            if (dist <= maxRadius) {
                foundTargets.add(target);
                lines.add(ChatUtil.applyReplacements(cfg.getString("messages.format"),
                        "{player}", target.getName(), "{distance}", (int) Math.round(dist)));
            }
        }

        if (foundTargets.isEmpty()) {
            ChatUtil.sendMessage(player, cfg.getString("messages.none-found"));
            return true;
        }

        ChatUtil.sendMessage(player, cfg.getString("messages.header"), "{radius}", maxRadius);
        for (String line : lines) {
            ChatUtil.sendMessage(player, line);
        }

        // Индивидуальное свечение на 10 секунд только для того игрока, который ввел /near
        int glowSeconds = cfg.getInt("settings.glow-duration", 10);
        PacketGlowUtil.sendTemporaryClientGlow(player, foundTargets, glowSeconds);
        return true;
    }

    private boolean handleRtp(Player player) {
        FileConfiguration cfg = extension.getCustomConfig("commands/rtp.yml").get();
        if (!player.hasPermission(cfg.getString("permissions.use", "xmpless.rtp.use"))) {
            ChatUtil.sendMessage(player, cfg.getString("messages.no-permission"));
            return true;
        }
        extension.getRtpService().openRtpMenu(player);
        return true;
    }
}