package me.xmpl.lib.util;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;

import java.util.List;

public final class InteractiveChatUtil {

    private InteractiveChatUtil() {}

    /**
     * Создаёт текстовый компонент с поддержкой цветовых кодов '&'.
     */
    public static TextComponent text(String rawText) {
        return new TextComponent(TextComponent.fromLegacyText(ChatUtil.color(rawText)));
    }

    /**
     * Создаёт кликабельную кнопку в чате, которая выполняет команду при клике
     * и показывает многострочную подсказку при наведении курсора.
     */
    public static TextComponent button(String label, String commandToRun, List<String> hoverLines) {
        TextComponent comp = text(label);
        if (commandToRun != null && !commandToRun.isEmpty()) {
            String cmd = commandToRun.startsWith("/") ? commandToRun : "/" + commandToRun;
            comp.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd));
        }
        if (hoverLines != null && !hoverLines.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < hoverLines.size(); i++) {
                sb.append(ChatUtil.color(hoverLines.get(i)));
                if (i + 1 < hoverLines.size()) {
                    sb.append("\n");
                }
            }
            BaseComponent[] hoverComp = TextComponent.fromLegacyText(sb.toString());
            comp.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverComp));
        }
        return comp;
    }

    /**
     * Создаёт текстовый элемент с всплывающей подсказкой (например, ник игрока в /c online).
     */
    public static TextComponent hoverableText(String displayText, List<String> hoverLines) {
        return button(displayText, null, hoverLines);
    }

    /**
     * Отправляет собранную строку из нескольких компонентов игроку.
     */
    public static void sendComponents(Player player, BaseComponent... components) {
        if (player == null || !player.isOnline() || components == null || components.length == 0) return;
        player.spigot().sendMessage(components);
    }
}