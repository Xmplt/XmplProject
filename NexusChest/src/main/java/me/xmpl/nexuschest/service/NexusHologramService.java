package me.xmpl.nexuschest.service;

import me.xmpl.lib.util.PacketHologramUtil;
import me.xmpl.lib.util.PacketHologramUtil.HologramLinePacket;
import me.xmpl.nexuschest.NexusChestExtension;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class NexusHologramService {
    private final NexusChestExtension extension;
    private List<HologramLinePacket> chest1Packets = new ArrayList<>();
    private List<HologramLinePacket> chest2Packets = new ArrayList<>();
    private final Set<UUID> viewersWithActiveHolograms = ConcurrentHashMap.newKeySet();

    public NexusHologramService(NexusChestExtension extension) {
        this.extension = extension;
        rebuildPackets();
    }

    public void rebuildPackets() {
        hideAll();
        FileConfiguration holoCfg = extension.getCustomConfig("holograms.yml").get();
        if (!holoCfg.getBoolean("enabled", true)) return;

        double offset = holoCfg.getDouble("height-offset", 1.25);
        Location c1 = extension.getArenaStorage().getChest1Location();
        Location c2 = extension.getArenaStorage().getChest2Location();

        if (c1 != null) {
            chest1Packets = PacketHologramUtil.buildHologramPackets(c1, holoCfg.getStringList("chest1-lines"), offset);
        } else {
            chest1Packets = Collections.emptyList();
        }

        if (c2 != null) {
            chest2Packets = PacketHologramUtil.buildHologramPackets(c2, holoCfg.getStringList("chest2-lines"), offset);
        } else {
            chest2Packets = Collections.emptyList();
        }
    }

    public void updateVisibilityForPlayer(Player player) {
        if (player == null || !player.isOnline()) return;
        boolean inRegion = extension.getArenaStorage().isInArenaRegion(player.getLocation());
        UUID uuid = player.getUniqueId();

        if (inRegion) {
            if (viewersWithActiveHolograms.add(uuid)) {
                PacketHologramUtil.sendSpawnPackets(player, chest1Packets);
                PacketHologramUtil.sendSpawnPackets(player, chest2Packets);
            }
        } else {
            if (viewersWithActiveHolograms.remove(uuid)) {
                PacketHologramUtil.sendDestroyPackets(player, chest1Packets);
                PacketHologramUtil.sendDestroyPackets(player, chest2Packets);
            }
        }
    }

    public void removePlayer(UUID uuid) {
        viewersWithActiveHolograms.remove(uuid);
    }

    public void hideAll() {
        for (UUID uuid : viewersWithActiveHolograms) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                PacketHologramUtil.sendDestroyPackets(p, chest1Packets);
                PacketHologramUtil.sendDestroyPackets(p, chest2Packets);
            }
        }
        viewersWithActiveHolograms.clear();
    }
}