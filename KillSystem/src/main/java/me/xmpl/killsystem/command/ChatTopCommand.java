package me.xmpl.killsystem.command;

import me.xmpl.killsystem.KillSystemExtension;
import me.xmpl.killsystem.model.PlayerKillRecord;
import me.xmpl.lib.util.ChatUtil;
import me.xmpl.lib.util.NumberDateUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

public class ChatTopCommand implements CommandExecutor {
    private final KillSystemExtension extension;

    public ChatTopCommand(KillSystemExtension extension) {
        this.extension = extension;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (extension.getConfig().getBoolean("permissions.require-top-permission", false)) {
            String perm = extension.getConfig().getString("permissions.top-view", "killsystem.view");
            if (!sender.hasPermission(perm)) {
                String noPerm = extension.getCustomConfig("commands/killsystem.yml").get().getString("messages.no-permission");
                ChatUtil.sendMessage(sender, noPerm);
                return true;
            }
        }

        int page = 1;
        if (args.length >= 1) {
            long parsed = NumberDateUtil.parseStrictPositiveLong(args[0]);
            if (parsed != -1L) {
                page = (int) parsed;
            }
        }

        FileConfiguration chatCfg = extension.getCustomConfig("chat-top.yml").get();
        List<PlayerKillRecord> sorted = extension.getDatabaseStorage().getSortedTopList();

        if (sorted.isEmpty()) {
            ChatUtil.sendMessage(sender, chatCfg.getString("messages.empty-top"));
            return true;
        }

        int perPage = Math.max(1, chatCfg.getInt("settings.entries-per-page", 10));
        int maxPages = Math.max(1, (int) Math.ceil((double) sorted.size() / perPage));

        if (page < 1 || page > maxPages) {
            ChatUtil.sendMessage(sender, chatCfg.getString("messages.invalid-page"),
                    "{page}", page, "{max_pages}", maxPages);
            return true;
        }

        ChatUtil.sendMessages(sender, chatCfg.getStringList("messages.header"),
                "{page}", page, "{max_pages}", maxPages);

        int startIndex = (page - 1) * perPage;
        int endIndex = Math.min(sorted.size(), startIndex + perPage);
        String entryFmt = chatCfg.getString("messages.entry-format",
                "&e#{rank} &f{player} &8- &fУбийств: &a{kills} &fСмертей: &c{deaths}");

        for (int i = startIndex; i < endIndex; i++) {
            PlayerKillRecord rec = sorted.get(i);
            ChatUtil.sendMessage(sender, entryFmt,
                    "{rank}", i + 1,
                    "{player}", rec.getPlayerName(),
                    "{kills}", rec.getKills(),
                    "{deaths}", rec.getDeaths());
        }

        if (page < maxPages) {
            ChatUtil.sendMessages(sender, chatCfg.getStringList("messages.footer"),
                    "{next_page}", page + 1);
        } else {
            ChatUtil.sendMessages(sender, chatCfg.getStringList("messages.footer-last-page"));
        }
        return true;
    }
}