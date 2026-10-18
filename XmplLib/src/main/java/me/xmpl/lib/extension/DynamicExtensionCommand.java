package me.xmpl.lib.extension;

import me.xmpl.lib.util.ChatUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.Collections;
import java.util.List;

public class DynamicExtensionCommand extends Command {
    private final XmplExtension extension;
    private CommandExecutor executor;
    private TabCompleter tabCompleter;

    public DynamicExtensionCommand(String name, List<String> aliases, XmplExtension extension, CommandExecutor executor) {
        super(name);
        if (aliases != null && !aliases.isEmpty()) {
            setAliases(aliases);
        }
        this.extension = extension;
        this.executor = executor;
        if (executor instanceof TabCompleter) {
            this.tabCompleter = (TabCompleter) executor;
        }
    }

    public void updateExecutor(CommandExecutor executor, TabCompleter tabCompleter) {
        this.executor = executor;
        this.tabCompleter = tabCompleter;
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        // Если дополнение отключено админом — блокируем команду и пишем сообщение
        if (!extension.isEnabled() || executor == null) {
            String msg = extension.getCore().getMessagesConfig().get().getString(
                    "extension-disabled-command",
                    "&c[!] Дополнение &e%extension% &cв данный момент отключено администратором!"
            );
            ChatUtil.sendMessage(sender, msg.replace("%extension%", extension.getName()).replace("%command%", commandLabel));
            return true;
        }
        return executor.onCommand(sender, this, commandLabel, args);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) throws IllegalArgumentException {
        if (!extension.isEnabled() || tabCompleter == null) {
            return Collections.emptyList();
        }
        List<String> completions = tabCompleter.onTabComplete(sender, this, alias, args);
        return completions != null ? completions : Collections.emptyList();
    }
}