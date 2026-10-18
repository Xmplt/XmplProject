package me.xmpl.lib.util;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import com.comphenix.protocol.wrappers.WrappedDataWatcher.Registry;
import com.comphenix.protocol.wrappers.WrappedDataWatcher.Serializer;
import me.xmpl.lib.XmplLib;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;

public final class PacketGlowUtil {
    private PacketGlowUtil() {}

    public static void sendTemporaryClientGlow(Player viewer, List<Player> targets, int durationSeconds) {
        if (Bukkit.getPluginManager().getPlugin("ProtocolLib") == null) return;
        if (viewer == null || !viewer.isOnline() || targets.isEmpty()) return;

        for (Player target : targets) {
            sendGlowPacket(viewer, target, true);
        }

        Bukkit.getScheduler().runTaskLater(XmplLib.getInstance(), () -> {
            if (!viewer.isOnline()) return;
            for (Player target : targets) {
                if (target != null && target.isOnline() && !target.isGlowing()) {
                    sendGlowPacket(viewer, target, false);
                }
            }
        }, durationSeconds * 20L);
    }

    private static void sendGlowPacket(Player viewer, Player target, boolean glowing) {
        try {
            ProtocolManager pm = ProtocolLibrary.getProtocolManager();
            PacketContainer packet = pm.createPacket(PacketType.Play.Server.ENTITY_METADATA);
            packet.getIntegers().write(0, target.getEntityId());

            WrappedDataWatcher watcher = new WrappedDataWatcher();
            Serializer serializer = Registry.get(Byte.class);
            byte currentMask = WrappedDataWatcher.getEntityWatcher(target).getByte(0);
            if (glowing) {
                currentMask |= 0x40; // Бит свечения (Glowing) в 1.12.2
            } else {
                currentMask &= ~0x40;
            }

            watcher.setEntity(target);
            watcher.setObject(0, serializer, currentMask);
            packet.getWatchableCollectionModifier().write(0, watcher.getWatchableObjects());

            pm.sendServerPacket(viewer, packet);
        } catch (Exception ignored) {}
    }
}