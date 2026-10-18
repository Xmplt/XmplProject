package me.xmpl.funeffects.command;

import me.xmpl.funeffects.FunEffectsExtension;
import me.xmpl.lib.util.ChatUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ActionCommands implements CommandExecutor {
    private final FunEffectsExtension extension;

    public ActionCommands(FunEffectsExtension extension) {
        this.extension = extension;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase();

        if (cmd.equals("funeffects")) {
            String adminPerm = extension.getConfig().getString("permissions.admin", "funeffects.admin");
            if (!sender.hasPermission(adminPerm)) {
                ChatUtil.sendMessage(sender, extension.getConfig().getString("messages.no-permission"));
                return true;
            }
            extension.reloadAllConfigs();
            extension.getSpitService().reload();
            ChatUtil.sendMessage(sender, extension.getConfig().getString("messages.reload-success"));
            return true;
        }

        if (!(sender instanceof Player)) return true;
        Player player = (Player) sender;

        switch (cmd) {
            case "spit":
                extension.getSpitService().performSpit(player);
                return true;
            case "spitlist":
                extension.getSpitService().openSpitListGui(player, 1);
                return true;
            case "fart":
                extension.getFartService().performFart(player);
                return true;
            case "kaka":
                extension.getKakaPipiService().startKaka(player);
                return true;
            case "pipi":
                extension.getKakaPipiService().startPipi(player);
                return true;
            default:
                return true;
        }
    }
}