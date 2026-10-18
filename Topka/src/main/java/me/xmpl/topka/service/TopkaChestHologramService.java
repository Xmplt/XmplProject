package me.xmpl.topka.service;

import me.xmpl.lib.util.PacketHologramUtil;
import me.xmpl.lib.util.PacketHologramUtil.HologramLinePacket;
import me.xmpl.lib.util.TimeUtil;
import me.xmpl.topka.TopkaExtension;
import me.xmpl.topka.model.PersistedTopkaState;
import me.xmpl.topka.model.TopkaStage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TopkaChestHologramService {
    private final TopkaExtension extension;
    private List<HologramLinePacket> chest1Packets = Collections.emptyList();
    private List<HologramLinePacket> chest2Packets = Collections.emptyList();
    private final Set<UUID> activeViewers = ConcurrentHashMap.newKeySet();
    private int dotStep = 0;

    public TopkaChestHologramService(TopkaExtension extension) {
        this.extension = extension;
    }

    public void updateHolograms(PersistedTopkaState state, boolean pausedNotEnoughPlayers, int minOnlineRequired) {
        removeAll();

        FileConfiguration hCfg = extension.getCustomConfig("holograms.yml").get();
        if (!hCfg.getBoolean("enabled", true)) return;

        Location h1 = extension.getArenaStorage().getHolo1BaseLocation();
        Location h2 = extension.getArenaStorage().getHolo2BaseLocation();
        if (h1 == null && h2 == null) return;

        boolean twoChests = extension.getArenaStorage().hasTwoChests() || (h1 != null && h2 != null);
        double offset = hCfg.getDouble("height-offset", 1.25);

        dotStep = (dotStep % 3) + 1;
        String animatedDots = dotStep == 1 ? "." : (dotStep == 2 ? ".." : "...");
        String formattedTime = TimeUtil.formatDuration(state.remainingSeconds * 1000L);

        List<String> rawTemplate;
        if (state.stage == TopkaStage.TIMER && pausedNotEnoughPlayers) {
            rawTemplate = hCfg.getStringList("not-enough-players");
        } else if (state.stage == TopkaStage.TIMER) {
            rawTemplate = hCfg.getStringList("timer");
        } else if (state.stage == TopkaStage.FILLING) {
            rawTemplate = hCfg.getStringList("filling");
        } else {
            rawTemplate = hCfg.getStringList("spawned");
        }

        if (h1 != null && h1.getWorld() != null) {
            List<String> lines1 = formatLinesForChest(rawTemplate, state.stage, pausedNotEnoughPlayers,
                    twoChests ? 1 : 0, formattedTime, minOnlineRequired, animatedDots);
            chest1Packets = PacketHologramUtil.buildHologramPackets(h1, lines1, offset);
        }

        if (h2 != null && h2.getWorld() != null) {
            List<String> lines2 = formatLinesForChest(rawTemplate, state.stage, pausedNotEnoughPlayers,
                    2, formattedTime, minOnlineRequired, animatedDots);
            chest2Packets = PacketHologramUtil.buildHologramPackets(h2, lines2, offset);
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            boolean sentAny = false;
            if (h1 != null && isNear(player, h1)) {
                PacketHologramUtil.sendSpawnPackets(player, chest1Packets);
                sentAny = true;
            }
            if (h2 != null && isNear(player, h2)) {
                PacketHologramUtil.sendSpawnPackets(player, chest2Packets);
                sentAny = true;
            }
            if (sentAny) {
                activeViewers.add(player.getUniqueId());
            }
        }
    }

    private List<String> formatLinesForChest(List<String> template, TopkaStage stage, boolean paused,
                                             int chestNumber, String timeStr, int minOnline, String dots) {
        List<String> result = new ArrayList<>(template.size());
        for (int i = 0; i < template.size(); i++) {
            String line = template.get(i)
                    .replace("%time%", timeStr)
                    .replace("{time}", timeStr)
                    .replace("%min%", String.valueOf(minOnline))
                    .replace("{min}", String.valueOf(minOnline));

            if (chestNumber > 0 && i == 0) {
                if (line.contains("TOPKA")) {
                    line = line.replace("TOPKA", "TOPKA #" + chestNumber);
                } else {
                    line = line + " #" + chestNumber;
                }
            }

            if (stage == TopkaStage.FILLING && !paused && i == template.size() - 1) {
                line = line + dots;
            }

            result.add(line);
        }
        return result;
    }

    private boolean isNear(Player player, Location loc) {
        if (player.getWorld() == null || loc.getWorld() == null) return false;
        if (!player.getWorld().getName().equalsIgnoreCase(loc.getWorld().getName())) return false;
        return player.getLocation().distanceSquared(loc) <= 3600.0;
    }

    public void removeAll() {
        for (UUID uuid : activeViewers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                if (!chest1Packets.isEmpty()) PacketHologramUtil.sendDestroyPackets(p, chest1Packets);
                if (!chest2Packets.isEmpty()) PacketHologramUtil.sendDestroyPackets(p, chest2Packets);
            }
        }
        activeViewers.clear();
        chest1Packets = Collections.emptyList();
        chest2Packets = Collections.emptyList();
    }
}