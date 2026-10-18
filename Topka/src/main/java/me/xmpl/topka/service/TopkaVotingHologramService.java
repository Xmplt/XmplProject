package me.xmpl.topka.service;

import me.xmpl.lib.util.PacketHologramUtil;
import me.xmpl.lib.util.PacketHologramUtil.HologramLinePacket;
import me.xmpl.topka.TopkaExtension;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TopkaVotingHologramService {
    private final TopkaExtension extension;
    private List<HologramLinePacket> votePackets = Collections.emptyList();
    private int floatingBlockEntityId = -1;
    private Object floatingBlockSpawnPacket = null;
    private Object floatingBlockEquipmentPacket = null;
    private final Set<UUID> activeViewers = ConcurrentHashMap.newKeySet();

    public TopkaVotingHologramService(TopkaExtension extension) {
        this.extension = extension;
    }

    public void updateVotingHologram() {
        FileConfiguration vCfg = extension.getCustomConfig("voting.yml").get();
        if (!vCfg.getBoolean("hologram.enabled", true)) {
            removeAll();
            return;
        }

        Location baseLoc = extension.getArenaStorage().getVoteEmeraldBlockLocation();
        if (baseLoc == null || baseLoc.getWorld() == null) {
            removeAll();
            return;
        }

        List<String> lines = vCfg.getStringList("hologram.lines");
        double offset = vCfg.getDouble("hologram.height-offset", 1.25);

        if (votePackets.isEmpty() || floatingBlockSpawnPacket == null) {
            votePackets = PacketHologramUtil.buildHologramPackets(baseLoc, lines, offset);
            buildFloatingEmeraldBlockPackets(baseLoc, offset - 0.55);
        }

        boolean allowRevote = vCfg.getBoolean("settings.allow-revote", false);

        for (Player player : baseLoc.getWorld().getPlayers()) {
            UUID uuid = player.getUniqueId();
            boolean shouldHideBecauseVoted = !allowRevote && extension.getVotingService().hasPlayerVoted(uuid);
            boolean near = player.getLocation().distanceSquared(baseLoc) <= 3600.0;

            if (shouldHideBecauseVoted || !near) {
                if (activeViewers.remove(uuid)) {
                    sendDestroyToPlayer(player);
                }
            } else {
                if (activeViewers.add(uuid)) {
                    sendSpawnToPlayer(player);
                }
            }
        }
    }

    public void hideHologramForPlayer(Player player) {
        if (player == null) return;
        if (activeViewers.remove(player.getUniqueId())) {
            sendDestroyToPlayer(player);
        }
    }

    public void rebuildAndBroadcastAll() {
        removeAll();
        updateVotingHologram();
    }

    public boolean isVotingHologramEntityId(int entityId) {
        if (entityId == floatingBlockEntityId && floatingBlockEntityId != -1) {
            return true;
        }
        for (HologramLinePacket p : votePackets) {
            if (p.entityId == entityId) return true;
        }
        return false;
    }

    /**
     * Создаёт пакетный прозрачный 3D-изумрудный блок снизу голограммы (невидимая стойка с EMERALD_BLOCK на голове):
     * сквозь него можно свободно ходить, но по нему можно кликать ЛКМ и ПКМ!
     */
    private void buildFloatingEmeraldBlockPackets(Location baseLoc, double yOffset) {
        try {
            String v = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
            Class<?> craftWorldClass = Class.forName("org.bukkit.craftbukkit." + v + ".CraftWorld");
            Class<?> craftItemStackClass = Class.forName("org.bukkit.craftbukkit." + v + ".inventory.CraftItemStack");
            Class<?> nmsWorldClass = Class.forName("net.minecraft.server." + v + ".World");
            Class<?> entityArmorStandClass = Class.forName("net.minecraft.server." + v + ".EntityArmorStand");
            Class<?> entityLivingClass = Class.forName("net.minecraft.server." + v + ".EntityLiving");
            Class<?> packetSpawnLivingClass = Class.forName("net.minecraft.server." + v + ".PacketPlayOutSpawnEntityLiving");
            Class<?> packetEquipmentClass = Class.forName("net.minecraft.server." + v + ".PacketPlayOutEntityEquipment");
            Class<?> enumItemSlotClass = Class.forName("net.minecraft.server." + v + ".EnumItemSlot");
            Class<?> nmsItemStackClass = Class.forName("net.minecraft.server." + v + ".ItemStack");

            Object nmsWorld = craftWorldClass.getMethod("getHandle").invoke(baseLoc.getWorld());
            Constructor<?> standConstructor = entityArmorStandClass.getConstructor(nmsWorldClass, double.class, double.class, double.class);

            double x = baseLoc.getBlockX() + 0.5;
            double y = baseLoc.getBlockY() + yOffset;
            double z = baseLoc.getBlockZ() + 0.5;

            Object stand = standConstructor.newInstance(nmsWorld, x, y, z);
            entityArmorStandClass.getMethod("setInvisible", boolean.class).invoke(stand, true);
            entityArmorStandClass.getMethod("setSmall", boolean.class).invoke(stand, true);
            entityArmorStandClass.getMethod("setNoGravity", boolean.class).invoke(stand, true);
            entityArmorStandClass.getMethod("setCustomNameVisible", boolean.class).invoke(stand, false);

            this.floatingBlockEntityId = (int) entityArmorStandClass.getMethod("getId").invoke(stand);
            this.floatingBlockSpawnPacket = packetSpawnLivingClass.getConstructor(entityLivingClass).newInstance(stand);

            Object nmsEmerald = craftItemStackClass.getMethod("asNMSCopy", ItemStack.class)
                    .invoke(null, new ItemStack(Material.EMERALD_BLOCK));
            Object headSlot = enumItemSlotClass.getMethod("valueOf", String.class).invoke(null, "HEAD");

            Constructor<?> equipConstructor = packetEquipmentClass.getConstructor(int.class, enumItemSlotClass, nmsItemStackClass);
            this.floatingBlockEquipmentPacket = equipConstructor.newInstance(this.floatingBlockEntityId, headSlot, nmsEmerald);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendSpawnToPlayer(Player player) {
        PacketHologramUtil.sendSpawnPackets(player, votePackets);
        if (floatingBlockSpawnPacket != null && floatingBlockEquipmentPacket != null) {
            sendRawPacket(player, floatingBlockSpawnPacket);
            sendRawPacket(player, floatingBlockEquipmentPacket);
        }
    }

    private void sendDestroyToPlayer(Player player) {
        PacketHologramUtil.sendDestroyPackets(player, votePackets);
        if (floatingBlockEntityId != -1) {
            try {
                String v = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
                Class<?> packetDestroyClass = Class.forName("net.minecraft.server." + v + ".PacketPlayOutEntityDestroy");
                Object destroyPacket = packetDestroyClass.getConstructor(int[].class)
                        .newInstance((Object) new int[]{floatingBlockEntityId});
                sendRawPacket(player, destroyPacket);
            } catch (Exception ignored) {}
        }
    }

    private void sendRawPacket(Player player, Object packet) {
        if (player == null || !player.isOnline() || packet == null) return;
        try {
            String v = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
            Class<?> packetClass = Class.forName("net.minecraft.server." + v + ".Packet");
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            Object conn = handle.getClass().getField("playerConnection").get(handle);
            Method sendPacket = conn.getClass().getMethod("sendPacket", packetClass);
            sendPacket.invoke(conn, packet);
        } catch (Exception ignored) {}
    }

    public void removeAll() {
        for (UUID uuid : new HashSet<>(activeViewers)) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                sendDestroyToPlayer(p);
            }
        }
        activeViewers.clear();
        votePackets = Collections.emptyList();
        floatingBlockEntityId = -1;
        floatingBlockSpawnPacket = null;
        floatingBlockEquipmentPacket = null;
    }
}