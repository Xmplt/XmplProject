package me.xmpl.funeffects.command;

import me.xmpl.funeffects.FunEffectsExtension;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class VocalCommandExecutor implements CommandExecutor {
    private final FunEffectsExtension extension;
    private final String cmdKey;

    public VocalCommandExecutor(FunEffectsExtension extension, String cmdKey) {
        this.extension = extension;
        this.cmdKey = cmdKey;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        extension.getVocalSoundService().executeVocalCommand((Player) sender, cmdKey);
        return true;
    }
}