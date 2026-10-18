package me.xmpl.killsystem.storage;

import me.xmpl.killsystem.KillSystemExtension;
import me.xmpl.killsystem.model.TopSignEntry;
import me.xmpl.lib.config.CustomConfig;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TopSignStorage {
    private final KillSystemExtension extension;
    private final Map<String, TopSignEntry> signs = new ConcurrentHashMap<>();

    public TopSignStorage(KillSystemExtension extension) {
        this.extension = extension;
        reload();
    }

    public void reload() {
        signs.clear();
        FileConfiguration cfg = extension.getCustomConfig("signs-data.yml").get();
        ConfigurationSection sec = cfg.getConfigurationSection("signs");
        if (sec == null) return;

        for (String id : sec.getKeys(false)) {
            int rank = sec.getInt(id + ".rank", 1);
            String world = sec.getString(id + ".world", "world");
            int x = sec.getInt(id + ".x");
            int y = sec.getInt(id + ".y");
            int z = sec.getInt(id + ".z");

            signs.put(id, new TopSignEntry(id, rank, world, x, y, z));
        }
    }

    private String makeKey(Location loc) {
        return loc.getWorld().getName() + "_" + loc.getBlockX() + "_" + loc.getBlockY() + "_" + loc.getBlockZ();
    }

    public TopSignEntry saveSign(Location loc, int rank) {
        String id = makeKey(loc);
        TopSignEntry entry = new TopSignEntry(id, rank, loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        signs.put(id, entry);

        CustomConfig customCfg = extension.getCustomConfig("signs-data.yml");
        FileConfiguration cfg = customCfg.get();
        cfg.set("signs." + id + ".rank", rank);
        cfg.set("signs." + id + ".world", entry.getWorldName());
        cfg.set("signs." + id + ".x", entry.getX());
        cfg.set("signs." + id + ".y", entry.getY());
        cfg.set("signs." + id + ".z", entry.getZ());
        customCfg.save();
        return entry;
    }

    public TopSignEntry removeSignAt(Location loc) {
        String id = makeKey(loc);
        TopSignEntry removed = signs.remove(id);
        if (removed != null) {
            CustomConfig customCfg = extension.getCustomConfig("signs-data.yml");
            customCfg.get().set("signs." + id, null);
            customCfg.save();
        }
        return removed;
    }

    public TopSignEntry getSignAt(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        return signs.get(makeKey(loc));
    }

    public Collection<TopSignEntry> getAllSigns() {
        return Collections.unmodifiableCollection(signs.values());
    }
}