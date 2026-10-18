package me.xmpl.lib.hook;

import org.bukkit.entity.Player;

public interface ExtensionPlaceholder {
    String onPlaceholderRequest(Player player, String params);
}