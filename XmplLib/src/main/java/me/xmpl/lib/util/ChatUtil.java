package me.xmpl.lib.util;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class ChatUtil {
    private ChatUtil() {}

    public static String color(String text) {
        if (text == null) return "";
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    public static List<String> color(List<String> list) {
        if (list == null) return new ArrayList<>();
        return list.stream().map(ChatUtil::color).collect(Collectors.toList());
    }

    public static void sendMessage(CommandSender sender, String message, Object... replacements) {
        if (sender == null || message == null || message.isEmpty()) return;
        String formatted = applyReplacements(message, replacements);
        sender.sendMessage(color(formatted));
    }

    public static void sendMessages(CommandSender sender, List<String> messages, Object... replacements) {
        if (sender == null || messages == null || messages.isEmpty()) return;
        for (String line : messages) {
            sendMessage(sender, line, replacements);
        }
    }

    public static String applyReplacements(String text, Object... replacements) {
        if (text == null) return "";
        String result = text;
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            String key = String.valueOf(replacements[i]);
            String value = String.valueOf(replacements[i + 1]);
            result = result.replace(key, value);
        }
        return result;
    }
}