package me.xmpl.nexusclans.storage;

import me.xmpl.lib.config.CustomConfig;
import me.xmpl.lib.util.CuboidBox;
import me.xmpl.lib.util.LocationSerializerUtil;
import me.xmpl.nexusclans.NexusClansExtension;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClanWarArenaStorage {
    public static class SavedBlock {
        public final int x, y, z;
        public final Material material;
        public final byte data;

        public SavedBlock(int x, int y, int z, Material material, byte data) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.material = material;
            this.data = data;
        }
    }

    private final NexusClansExtension extension;
    private CuboidBox arenaRegion;
    private Location spectatorPoint;
    private final List<Location> clan1Points = new ArrayList<>();
    private final List<Location> clan2Points = new ArrayList<>();

    // Временные блоки барьерных капсул, которые ставятся вокруг бойцов на 1 минуту перед боем
    private final Map<Location, Material> placedBarrierOriginalTypes = new HashMap<>();
    private final Map<Location, Byte> placedBarrierOriginalData = new HashMap<>();

    public ClanWarArenaStorage(NexusClansExtension extension) {
        this.extension = extension;
        reload();
    }

    public void reload() {
        clan1Points.clear();
        clan2Points.clear();

        FileConfiguration cfg = extension.getCustomConfig("war-arena.yml").get();
        if (cfg.isConfigurationSection("arena.region")) {
            String world = cfg.getString("arena.region.world", "world");
            arenaRegion = new CuboidBox("war_arena", world,
                    cfg.getInt("arena.region.minX"), cfg.getInt("arena.region.minY"), cfg.getInt("arena.region.minZ"),
                    cfg.getInt("arena.region.maxX"), cfg.getInt("arena.region.maxY"), cfg.getInt("arena.region.maxZ"));
        } else {
            arenaRegion = null;
        }

        spectatorPoint = LocationSerializerUtil.readLocation(cfg, "arena.spectator-point");

        ConfigurationSection c1Sec = cfg.getConfigurationSection("arena.clan1-points");
        if (c1Sec != null) {
            for (String key : c1Sec.getKeys(false)) {
                Location loc = LocationSerializerUtil.readLocation(cfg, "arena.clan1-points." + key);
                if (loc != null) clan1Points.add(loc);
            }
        }

        ConfigurationSection c2Sec = cfg.getConfigurationSection("arena.clan2-points");
        if (c2Sec != null) {
            for (String key : c2Sec.getKeys(false)) {
                Location loc = LocationSerializerUtil.readLocation(cfg, "arena.clan2-points." + key);
                if (loc != null) clan2Points.add(loc);
            }
        }
    }

    /**
     * Сохраняет регион арены 5x5 и записывает слепок всех блоков арены в war-arena-snapshot.yml для авто-регенерации после боя.
     */
    @SuppressWarnings("deprecation")
    public void saveArenaRegionAndSnapshot(CuboidBox box) {
        this.arenaRegion = box;
        CustomConfig cc = extension.getCustomConfig("war-arena.yml");
        FileConfiguration cfg = cc.get();
        cfg.set("arena.region.world", box.getWorldName());
        cfg.set("arena.region.minX", box.getMinX());
        cfg.set("arena.region.minY", box.getMinY());
        cfg.set("arena.region.minZ", box.getMinZ());
        cfg.set("arena.region.maxX", box.getMaxX());
        cfg.set("arena.region.maxY", box.getMaxY());
        cfg.set("arena.region.maxZ", box.getMaxZ());
        cc.save();

        World world = Bukkit.getWorld(box.getWorldName());
        if (world == null) return;

        List<String> serialized = new ArrayList<>();
        for (int x = box.getMinX(); x <= box.getMaxX(); x++) {
            for (int y = box.getMinY(); y <= box.getMaxY(); y++) {
                for (int z = box.getMinZ(); z <= box.getMaxZ(); z++) {
                    Block b = world.getBlockAt(x, y, z);
                    serialized.add(x + ";" + y + ";" + z + ";" + b.getType().name() + ";" + b.getData());
                }
            }
        }

        CustomConfig snapCfg = extension.getCustomConfig("war-arena-snapshot.yml");
        snapCfg.get().set("world", world.getName());
        snapCfg.get().set("blocks", serialized);
        snapCfg.save();
    }

    /**
     * Полностью восстанавливает все сломанные и поставленные во время битвы блоки арены и очищает лежащий лут.
     */
    @SuppressWarnings("deprecation")
    public void regenerateArena() {
        removeAllBarrierCages();
        if (arenaRegion == null) return;

        World world = Bukkit.getWorld(arenaRegion.getWorldName());
        if (world == null) return;

        // Удаляем оставшиеся после 2-минутного лутания предметы на земле внутри арены
        for (Entity entity : world.getEntities()) {
            if (entity instanceof Item && arenaRegion.contains(entity.getLocation())) {
                entity.remove();
            }
        }

        CustomConfig snapCfg = extension.getCustomConfig("war-arena-snapshot.yml");
        List<String> serialized = snapCfg.get().getStringList("blocks");
        if (serialized == null || serialized.isEmpty()) return;

        for (String line : serialized) {
            String[] p = line.split(";");
            if (p.length < 5) continue;
            try {
                int x = Integer.parseInt(p[0]);
                int y = Integer.parseInt(p[1]);
                int z = Integer.parseInt(p[2]);
                Material mat = Material.matchMaterial(p[3]);
                byte data = Byte.parseByte(p[4]);
                if (mat != null) {
                    Block b = world.getBlockAt(x, y, z);
                    if (b.getType() != mat || b.getData() != data) {
                        b.setType(mat, false);
                        b.setData(data, false);
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    /**
     * Ставит барьерные блоки (BARRIER) по бокам и сверху вокруг каждой стартовой точки бойцов на 1 минуту перед началом боя.
     */
    @SuppressWarnings("deprecation")
    public void buildBarrierCagesAround(Iterable<Location> spawnLocations) {
        removeAllBarrierCages();
        int[][] offsets = {
                // Вокруг ног (y + 0)
                {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1},
                // Вокруг головы (y + 1)
                {1, 1, 0}, {-1, 1, 0}, {0, 1, 1}, {0, 1, -1},
                // Над головой (y + 2)
                {0, 2, 0}
        };

        for (Location spawn : spawnLocations) {
            if (spawn == null || spawn.getWorld() == null) continue;
            World w = spawn.getWorld();
            int bx = spawn.getBlockX();
            int by = spawn.getBlockY();
            int bz = spawn.getBlockZ();

            for (int[] off : offsets) {
                Block b = w.getBlockAt(bx + off[0], by + off[1], bz + off[2]);
                if (b.getType() == Material.AIR) {
                    Location bLoc = b.getLocation();
                    placedBarrierOriginalTypes.put(bLoc, b.getType());
                    placedBarrierOriginalData.put(bLoc, b.getData());
                    b.setType(Material.BARRIER, false);
                }
            }
        }
    }

    /**
     * Убирает все стартовые барьерные блоки по окончании 1-минутного отсчета.
     */
    @SuppressWarnings("deprecation")
    public void removeAllBarrierCages() {
        for (Map.Entry<Location, Material> entry : placedBarrierOriginalTypes.entrySet()) {
            Location loc = entry.getKey();
            if (loc.getWorld() != null) {
                Block b = loc.getBlock();
                b.setType(entry.getValue(), false);
                Byte data = placedBarrierOriginalData.get(loc);
                if (data != null) {
                    b.setData(data, false);
                }
            }
        }
        placedBarrierOriginalTypes.clear();
        placedBarrierOriginalData.clear();
    }

    public void setSpectatorPoint(Location loc) {
        this.spectatorPoint = loc.clone();
        CustomConfig cc = extension.getCustomConfig("war-arena.yml");
        LocationSerializerUtil.writeLocation(cc.get(), "arena.spectator-point", this.spectatorPoint);
        cc.save();
    }

    public int addClanSpawnPoint(int clanTeamNumber, Location loc) {
        List<Location> targetList = (clanTeamNumber == 1) ? clan1Points : clan2Points;
        if (targetList.size() >= 5) {
            return -1;
        }
        Location centered = new Location(loc.getWorld(), loc.getBlockX() + 0.5, loc.getBlockY(), loc.getBlockZ() + 0.5, loc.getYaw(), loc.getPitch());
        targetList.add(centered);

        CustomConfig cc = extension.getCustomConfig("war-arena.yml");
        String basePath = (clanTeamNumber == 1) ? "arena.clan1-points.p_" : "arena.clan2-points.p_";
        LocationSerializerUtil.writeLocation(cc.get(), basePath + targetList.size(), centered);
        cc.save();
        return targetList.size();
    }

    public void clearAllSpawnPoints() {
        clan1Points.clear();
        clan2Points.clear();
        CustomConfig cc = extension.getCustomConfig("war-arena.yml");
        cc.get().set("arena.clan1-points", null);
        cc.get().set("arena.clan2-points", null);
        cc.save();
    }

    public boolean isInWarArena(Location loc) {
        return arenaRegion != null && arenaRegion.contains(loc);
    }

    public CuboidBox getArenaRegion() { return arenaRegion; }
    public Location getSpectatorPoint() { return spectatorPoint != null ? spectatorPoint.clone() : null; }
    public List<Location> getClan1Points() { return Collections.unmodifiableList(clan1Points); }
    public List<Location> getClan2Points() { return Collections.unmodifiableList(clan2Points); }
}