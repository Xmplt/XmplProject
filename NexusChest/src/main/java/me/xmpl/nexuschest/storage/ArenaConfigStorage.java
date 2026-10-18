package me.xmpl.nexuschest.storage;

import me.xmpl.lib.config.CustomConfig;
import me.xmpl.lib.util.CuboidBox;
import me.xmpl.lib.util.LocationSerializerUtil;
import me.xmpl.nexuschest.NexusChestExtension;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

public class ArenaConfigStorage {
    private final NexusChestExtension extension;
    private CuboidBox arenaRegion;
    private Location enterLocation;
    private Location expelSpawnLocation;
    private Location chest1Location;
    private Location chest2Location;

    public ArenaConfigStorage(NexusChestExtension extension) {
        this.extension = extension;
        reload();
    }

    public void reload() {
        FileConfiguration cfg = extension.getCustomConfig("arena.yml").get();

        if (cfg.isConfigurationSection("arena.region")) {
            String world = cfg.getString("arena.region.world", "world");
            int minX = cfg.getInt("arena.region.minX");
            int minY = cfg.getInt("arena.region.minY");
            int minZ = cfg.getInt("arena.region.minZ");
            int maxX = cfg.getInt("arena.region.maxX");
            int maxY = cfg.getInt("arena.region.maxY");
            int maxZ = cfg.getInt("arena.region.maxZ");
            this.arenaRegion = new CuboidBox("nexus_arena", world, minX, minY, minZ, maxX, maxY, maxZ);
        } else {
            this.arenaRegion = null;
        }

        this.enterLocation = LocationSerializerUtil.readLocation(cfg, "arena.enter-location");
        this.expelSpawnLocation = LocationSerializerUtil.readLocation(cfg, "arena.spawn-location");
        this.chest1Location = LocationSerializerUtil.readLocation(cfg, "arena.chest1");
        this.chest2Location = LocationSerializerUtil.readLocation(cfg, "arena.chest2");
    }

    public void saveArenaRegion(CuboidBox box) {
        this.arenaRegion = box;
        CustomConfig cc = extension.getCustomConfig("arena.yml");
        FileConfiguration cfg = cc.get();
        cfg.set("arena.region.world", box.getWorldName());
        cfg.set("arena.region.minX", box.getMinX());
        cfg.set("arena.region.minY", box.getMinY());
        cfg.set("arena.region.minZ", box.getMinZ());
        cfg.set("arena.region.maxX", box.getMaxX());
        cfg.set("arena.region.maxY", box.getMaxY());
        cfg.set("arena.region.maxZ", box.getMaxZ());
        cc.save();
    }

    public void setEnterLocation(Location loc) {
        this.enterLocation = loc.clone();
        CustomConfig cc = extension.getCustomConfig("arena.yml");
        LocationSerializerUtil.writeLocation(cc.get(), "arena.enter-location", this.enterLocation);
        cc.save();
    }

    public void setExpelSpawnLocation(Location loc) {
        this.expelSpawnLocation = loc.clone();
        CustomConfig cc = extension.getCustomConfig("arena.yml");
        LocationSerializerUtil.writeLocation(cc.get(), "arena.spawn-location", this.expelSpawnLocation);
        cc.save();
    }

    public void setChestLocation(int number, Location loc) {
        Location blockLoc = new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        CustomConfig cc = extension.getCustomConfig("arena.yml");
        if (number == 1) {
            this.chest1Location = blockLoc;
            LocationSerializerUtil.writeLocation(cc.get(), "arena.chest1", blockLoc);
        } else {
            this.chest2Location = blockLoc;
            LocationSerializerUtil.writeLocation(cc.get(), "arena.chest2", blockLoc);
        }
        cc.save();
    }

    public boolean isInArenaRegion(Location loc) {
        return arenaRegion != null && arenaRegion.contains(loc);
    }

    public int getChestNumberAt(Location loc) {
        if (loc == null || loc.getWorld() == null) return 0;
        if (matchesBlock(chest1Location, loc)) return 1;
        if (matchesBlock(chest2Location, loc)) return 2;
        return 0;
    }

    private boolean matchesBlock(Location a, Location b) {
        if (a == null || b == null || a.getWorld() == null || b.getWorld() == null) return false;
        return a.getWorld().getName().equalsIgnoreCase(b.getWorld().getName())
                && a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }

    public Location getEnterLocation() { return enterLocation != null ? enterLocation.clone() : null; }
    public Location getExpelSpawnLocation() {
        if (expelSpawnLocation != null) return expelSpawnLocation.clone();
        World w = !Bukkit.getWorlds().isEmpty() ? Bukkit.getWorlds().get(0) : null;
        return w != null ? w.getSpawnLocation() : null;
    }
    public Location getChest1Location() { return chest1Location != null ? chest1Location.clone() : null; }
    public Location getChest2Location() { return chest2Location != null ? chest2Location.clone() : null; }
}