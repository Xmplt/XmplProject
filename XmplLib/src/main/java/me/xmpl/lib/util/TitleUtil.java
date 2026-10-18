package me.xmpl.lib.util;

import org.bukkit.entity.Player;

public final class TitleUtil {
    private TitleUtil() {}

    public static void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut, Object... replacements) {
        if (player == null || !player.isOnline()) return;
        String formattedTitle = ChatUtil.color(ChatUtil.applyReplacements(title, replacements));
        String formattedSub = ChatUtil.color(ChatUtil.applyReplacements(subtitle, replacements));
        // Если fadeIn = 0, текст появляется мгновенно со 100% яркостью и непрозрачностью!
        player.sendTitle(formattedTitle, formattedSub, fadeIn, stay, fadeOut);
    }
}