package me.xmpl.wood.storage;

import me.xmpl.lib.config.CustomConfig;
import me.xmpl.lib.util.CuboidBox;
import me.xmpl.wood.WoodSystemExtension;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WoodRegionStorage {
    private final WoodSystemExtension extension;
    private final Map<String, CuboidBox> regions = new ConcurrentHashMap<>();

    public WoodRegionStorage(WoodSystemExtension extension) {
        this.extension = extension;
        reload();
    }

    public void reload() {
        regions.clear();
        FileConfiguration cfg = extension.getCustomConfig("regions.yml").get();
        ConfigurationSection sec = cfg.getConfigurationSection("regions");
        if (sec == null) return;

        for (String key : sec.getKeys(false)) {
            String world = sec.getString(key + ".world", "world");
            int minX = sec.getInt(key + ".minX");
            int minY = sec.getInt(key + ".minY");
            int minZ = sec.getInt(key + ".minZ");
            int maxX = sec.getInt(key + ".maxX");
            int maxY = sec.getInt(key + ".maxY");
            int maxZ = sec.getInt(key + ".maxZ");

            CuboidBox box = new CuboidBox(key, world, minX, minY, minZ, maxX, maxY, maxZ);
            regions.put(key.toLowerCase(), box);
        }
    }

    public void saveRegion(CuboidBox box) {
        String key = box.getName().toLowerCase();
        regions.put(key, box);

        CustomConfig customCfg = extension.getCustomConfig("regions.yml");
        FileConfiguration cfg = customCfg.get();
        String path = "regions." + box.getName();
        cfg.set(path + ".world", box.getWorldName());
        cfg.set(path + ".minX", box.getMinX());
        cfg.set(path + ".minY", box.getMinY());
        cfg.set(path + ".minZ", box.getMinZ());
        cfg.set(path + ".maxX", box.getMaxX());
        cfg.set(path + ".maxY", box.getMaxY());
        cfg.set(path + ".maxZ", box.getMaxZ());
        customCfg.save();
    }

    public boolean removeRegion(String name) {
        CuboidBox removed = regions.remove(name.toLowerCase());
        if (removed == null) return false;

        CustomConfig customCfg = extension.getCustomConfig("regions.yml");
        customCfg.get().set("regions." + removed.getName(), null);
        customCfg.save();
        return true;
    }

    public boolean isInAnyWoodRegion(Location loc) {
        if (loc == null) return false;
        for (CuboidBox box : regions.values()) {
            if (box.contains(loc)) {
                return true;
            }
        }
        return false;
    }

    public Collection<CuboidBox> getRegions() {
        return Collections.unmodifiableCollection(regions.values());
    }
}