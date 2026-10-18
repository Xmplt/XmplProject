package me.xmpl.ess.command;

import me.xmpl.ess.XmplEssExtension;
import me.xmpl.ess.service.VanishAndHelperService;
import me.xmpl.lib.util.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class VanishAndStickCommands implements CommandExecutor {
    private final XmplEssExtension extension;
    private final VanishAndHelperService vanishService;

    public VanishAndStickCommands(XmplEssExtension extension, VanishAndHelperService vanishService) {
        this.extension = extension;
        this.vanishService = vanishService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String name = cmd.getName().toLowerCase();
        switch (name) {
            case "vanish": return handleVanish(sender);
            case "online": return handleOnline(sender);
            case "god": return handleGod(sender);
            case "stickhelper": return handleStickHelper(sender, args);
            default: return true;
        }
    }

    private boolean handleVanish(CommandSender sender) {
        if (!(sender instanceof Player)) return true;
        Player player = (Player) sender;
        FileConfiguration cfg = extension.getCustomConfig("commands/vanish.yml").get();

        if (!player.hasPermission(cfg.getString("permissions.vanish-use", "xmpless.vanish.use"))) {
            ChatUtil.sendMessage(player, cfg.getString("messages.no-permission"));
            return true;
        }

        boolean enabled = vanishService.toggleVanish(player);
        if (enabled) {
            ChatUtil.sendMessage(player, cfg.getString("messages.vanish-enabled"),
                    "{level}", vanishService.getVanishLevel(player));
        } else {
            ChatUtil.sendMessage(player, cfg.getString("messages.vanish-disabled"));
        }
        return true;
    }

    private boolean handleOnline(CommandSender sender) {
        FileConfiguration cfg = extension.getCustomConfig("commands/vanish.yml").get();
        if (!sender.hasPermission(cfg.getString("permissions.online-use", "xmpless.online"))) {
            ChatUtil.sendMessage(sender, cfg.getString("messages.no-permission"));
            return true;
        }

        List<String> visibleNames = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (sender instanceof Player) {
                if (vanishService.canSee((Player) sender, p)) {
                    visibleNames.add(p.getName());
                }
            } else {
                visibleNames.add(p.getName());
            }
        }

        String joinedPlayers = String.join(", ", visibleNames); // Без запятой и точки в конце!
        ChatUtil.sendMessage(sender, cfg.getString("messages.online-header", "&fСейчас на сервере онлайн &6&l{count}&f:"),
                "{count}", visibleNames.size());
        ChatUtil.sendMessage(sender, cfg.getString("messages.online-list", "&f{players}"),
                "{players}", joinedPlayers);
        return true;
    }

    private boolean handleGod(CommandSender sender) {
        if (!(sender instanceof Player)) return true;
        Player player = (Player) sender;
        FileConfiguration cfg = extension.getCustomConfig("commands/vanish.yml").get();

        if (!player.hasPermission(cfg.getString("permissions.god-use", "xmpless.god"))) {
            ChatUtil.sendMessage(player, cfg.getString("messages.no-permission"));
            return true;
        }

        boolean enabled = vanishService.toggleGod(player);
        ChatUtil.sendMessage(player, enabled ? cfg.getString("messages.god-enabled") : cfg.getString("messages.god-disabled"));
        return true;
    }

    private boolean handleStickHelper(CommandSender sender, String[] args) {
        FileConfiguration cfg = extension.getCustomConfig("commands/stickhelper.yml").get();
        String selfPerm = cfg.getString("permissions.hold-and-self", "xmpless.stickhelper");
        String givePerm = cfg.getString("permissions.give-others", "xmpless.stickhelper.admin");

        if (args.length == 0) {
            if (!(sender instanceof Player)) return true;
            Player player = (Player) sender;
            if (!player.hasPermission(selfPerm)) {
                ChatUtil.sendMessage(player, cfg.getString("messages.no-permission"));
                return true;
            }
            player.getInventory().addItem(vanishService.createHelperStick(player.getName(), player.getName()));
            ChatUtil.sendMessage(player, cfg.getString("messages.received-self"));
            return true;
        }

        // Выдача другому хелперу: /stickhelper <Ник>
        if (!sender.hasPermission(givePerm)) {
            ChatUtil.sendMessage(sender, cfg.getString("messages.no-permission"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            ChatUtil.sendMessage(sender, "&cИгрок не в сети!");
            return true;
        }

        if (!target.hasPermission(selfPerm)) {
            ChatUtil.sendMessage(sender, cfg.getString("messages.target-no-permission"), "{perm}", selfPerm);
            return true;
        }

        target.getInventory().addItem(vanishService.createHelperStick(target.getName(), sender.getName()));
        ChatUtil.sendMessage(sender, cfg.getString("messages.given-to-helper"), "{target}", target.getName());
        ChatUtil.sendMessage(target, cfg.getString("messages.received-self"));
        return true;
    }
}