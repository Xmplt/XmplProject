package me.xmpl.lib.util;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public final class ActionBarUtil {
    private ActionBarUtil() {}

    public static void sendActionBar(Player player, String message) {
        if (player == null || !player.isOnline() || message == null) return;
        String colored = ChatUtil.color(message);

        try {
            String version = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
            Class<?> chatSerializer = Class.forName("net.minecraft.server." + version + ".IChatBaseComponent$ChatSerializer");
            Class<?> iChatBaseComponent = Class.forName("net.minecraft.server." + version + ".IChatBaseComponent");
            Class<?> packetPlayOutChat = Class.forName("net.minecraft.server." + version + ".PacketPlayOutChat");
            Class<?> chatMessageTypeClass = Class.forName("net.minecraft.server." + version + ".ChatMessageType");

            Method aMethod = chatSerializer.getMethod("a", String.class);
            Object chatComp = aMethod.invoke(null, "{\"text\":\"" + colored.replace("\"", "\\\"") + "\"}");

            Method valueOfType = chatMessageTypeClass.getMethod("valueOf", String.class);
            Object gameInfoType = valueOfType.invoke(null, "GAME_INFO");

            Constructor<?> packetConstructor = packetPlayOutChat.getConstructor(iChatBaseComponent, chatMessageTypeClass);
            Object packet = packetConstructor.newInstance(chatComp, gameInfoType);

            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            Object playerConnection = handle.getClass().getField("playerConnection").get(handle);
            Method sendPacket = playerConnection.getClass().getMethod("sendPacket", Class.forName("net.minecraft.server." + version + ".Packet"));
            sendPacket.invoke(playerConnection, packet);
        } catch (Exception e) {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(colored));
        }
    }
}