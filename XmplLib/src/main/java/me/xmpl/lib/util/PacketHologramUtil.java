package me.xmpl.lib.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class PacketHologramUtil {
    private PacketHologramUtil() {}

    public static class HologramLinePacket {
        public final int entityId;
        public final Object spawnPacket;

        public HologramLinePacket(int entityId, Object spawnPacket) {
            this.entityId = entityId;
            this.spawnPacket = spawnPacket;
        }
    }

    public static List<HologramLinePacket> buildHologramPackets(Location baseBlockLoc, List<String> lines, double heightOffset) {
        List<HologramLinePacket> result = new ArrayList<>();
        if (baseBlockLoc == null || baseBlockLoc.getWorld() == null || lines == null || lines.isEmpty()) {
            return result;
        }

        try {
            String v = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
            Class<?> craftWorldClass = Class.forName("org.bukkit.craftbukkit." + v + ".CraftWorld");
            Class<?> nmsWorldClass = Class.forName("net.minecraft.server." + v + ".World");
            Class<?> entityArmorStandClass = Class.forName("net.minecraft.server." + v + ".EntityArmorStand");
            Class<?> entityLivingClass = Class.forName("net.minecraft.server." + v + ".EntityLiving");
            Class<?> packetSpawnLivingClass = Class.forName("net.minecraft.server." + v + ".PacketPlayOutSpawnEntityLiving");

            Object nmsWorld = craftWorldClass.getMethod("getHandle").invoke(baseBlockLoc.getWorld());
            Constructor<?> standConstructor = entityArmorStandClass.getConstructor(nmsWorldClass, double.class, double.class, double.class);
            Constructor<?> packetConstructor = packetSpawnLivingClass.getConstructor(entityLivingClass);

            Method setCustomName = entityArmorStandClass.getMethod("setCustomName", String.class);
            Method setCustomNameVisible = entityArmorStandClass.getMethod("setCustomNameVisible", boolean.class);
            Method setInvisible = entityArmorStandClass.getMethod("setInvisible", boolean.class);
            Method setSmall = entityArmorStandClass.getMethod("setSmall", boolean.class);
            Method setNoGravity = entityArmorStandClass.getMethod("setNoGravity", boolean.class);
            Method getId = entityArmorStandClass.getMethod("getId");

            double x = baseBlockLoc.getBlockX() + 0.5;
            double z = baseBlockLoc.getBlockZ() + 0.5;
            double startY = baseBlockLoc.getBlockY() + heightOffset + ((lines.size() - 1) * 0.27);

            for (int i = 0; i < lines.size(); i++) {
                double y = startY - (i * 0.27);
                Object stand = standConstructor.newInstance(nmsWorld, x, y, z);
                setCustomName.invoke(stand, ChatUtil.color(lines.get(i)));
                setCustomNameVisible.invoke(stand, true);
                setInvisible.invoke(stand, true);
                setSmall.invoke(stand, true);
                setNoGravity.invoke(stand, true);

                int id = (int) getId.invoke(stand);
                Object packet = packetConstructor.newInstance(stand);
                result.add(new HologramLinePacket(id, packet));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    public static void sendSpawnPackets(Player player, List<HologramLinePacket> packets) {
        if (player == null || !player.isOnline() || packets == null || packets.isEmpty()) return;
        try {
            String v = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
            Class<?> packetClass = Class.forName("net.minecraft.server." + v + ".Packet");
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            Object conn = handle.getClass().getField("playerConnection").get(handle);
            Method sendPacket = conn.getClass().getMethod("sendPacket", packetClass);

            for (HologramLinePacket p : packets) {
                sendPacket.invoke(conn, p.spawnPacket);
            }
        } catch (Exception ignored) {}
    }

    public static void sendDestroyPackets(Player player, List<HologramLinePacket> packets) {
        if (player == null || !player.isOnline() || packets == null || packets.isEmpty()) return;
        try {
            String v = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
            Class<?> packetDestroyClass = Class.forName("net.minecraft.server." + v + ".PacketPlayOutEntityDestroy");
            Class<?> packetClass = Class.forName("net.minecraft.server." + v + ".Packet");

            int[] ids = new int[packets.size()];
            for (int i = 0; i < packets.size(); i++) {
                ids[i] = packets.get(i).entityId;
            }

            Object destroyPacket = packetDestroyClass.getConstructor(int[].class).newInstance((Object) ids);
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            Object conn = handle.getClass().getField("playerConnection").get(handle);
            conn.getClass().getMethod("sendPacket", packetClass).invoke(conn, destroyPacket);
        } catch (Exception ignored) {}
    }
}