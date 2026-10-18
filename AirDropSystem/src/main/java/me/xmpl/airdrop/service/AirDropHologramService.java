package me.xmpl.airdrop.service;

import me.xmpl.airdrop.AirDropExtension;
import me.xmpl.airdrop.model.AirDropStage;
import me.xmpl.lib.util.PacketHologramUtil;
import me.xmpl.lib.util.PacketHologramUtil.HologramLinePacket;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AirDropHologramService {
    private final AirDropExtension extension;
    private List<HologramLinePacket> currentPackets = Collections.emptyList();
    private final Set<UUID> activeViewers = ConcurrentHashMap.newKeySet();
    private int dotAnimationStep = 0;

    public AirDropHologramService(AirDropExtension extension) {
        this.extension = extension;
    }

    public void updateHologram(Location chestLoc, AirDropStage stage, String formattedTime) {
        removeHologramForAll();

        FileConfiguration hCfg = extension.getCustomConfig("holograms.yml").get();
        if (!hCfg.getBoolean("enabled", true) || chestLoc == null || stage == AirDropStage.IDLE) {
            return;
        }

        dotAnimationStep = (dotAnimationStep % 3) + 1;
        String dots = dotAnimationStep == 1 ? "." : (dotAnimationStep == 2 ? ".." : "...");

        String sectionKey;
        switch (stage) {
            case WAITING_ACTIVATION: sectionKey = "waiting"; break;
            case UNLOCKING: sectionKey = "unlocking"; break;
            case RELOADING: sectionKey = "reloading"; break;
            case DROPPING: sectionKey = "dropping"; break;
            case FINISHED: sectionKey = "finished"; break;
            default: return;
        }

        List<String> rawLines = hCfg.getStringList("holograms." + sectionKey);
        List<String> formattedLines = new ArrayList<>();
        for (String line : rawLines) {
            formattedLines.add(line.replace("{time}", formattedTime).replace("{dots}", dots));
        }

        double offset = hCfg.getDouble("height-offset", 1.35);
        currentPackets = PacketHologramUtil.buildHologramPackets(chestLoc, formattedLines, offset);

        for (Player player : chestLoc.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(chestLoc) <= 2500.0) { // Видна в радиусе 50 блоков
                PacketHologramUtil.sendSpawnPackets(player, currentPackets);
                activeViewers.add(player.getUniqueId());
            }
        }
    }

    public void removeHologramForAll() {
        if (!currentPackets.isEmpty()) {
            for (UUID uuid : activeViewers) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) {
                    PacketHologramUtil.sendDestroyPackets(p, currentPackets);
                }
            }
        }
        activeViewers.clear();
        currentPackets = Collections.emptyList();
    }
}