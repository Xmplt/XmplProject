package me.xmpl.adender.command;

import me.xmpl.adender.AdEnderExtension;
import me.xmpl.adender.model.PortalDestinationType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class EnderPortalCommand implements CommandExecutor {
    private final AdEnderExtension extension;

    public EnderPortalCommand(AdEnderExtension extension) {
        this.extension = extension;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        extension.getTeleportService().attemptTeleport((Player) sender, PortalDestinationType.ENDER);
        return true;
    }
}