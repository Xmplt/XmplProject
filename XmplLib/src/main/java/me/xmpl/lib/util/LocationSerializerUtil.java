package me.xmpl.lib.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

public final class LocationSerializerUtil {
    private LocationSerializerUtil() {}

    public static void writeLocation(FileConfiguration cfg, String path, Location loc) {
        if (cfg == null || path == null || loc == null || loc.getWorld() == null) return;
        cfg.set(path + ".world", loc.getWorld().getName());
        cfg.set(path + ".x", loc.getX());
        cfg.set(path + ".y", loc.getY());
        cfg.set(path + ".z", loc.getZ());
        cfg.set(path + ".yaw", (double) loc.getYaw());
        cfg.set(path + ".pitch", (double) loc.getPitch());
    }

    public static Location readLocation(FileConfiguration cfg, String path) {
        if (cfg == null || path == null || !cfg.isConfigurationSection(path)) return null;
        String worldName = cfg.getString(path + ".world");
        if (worldName == null) return null;

        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;

        double x = cfg.getDouble(path + ".x");
        double y = cfg.getDouble(path + ".y");
        double z = cfg.getDouble(path + ".z");
        float yaw = (float) cfg.getDouble(path + ".yaw");
        float pitch = (float) cfg.getDouble(path + ".pitch");

        return new Location(world, x, y, z, yaw, pitch);
    }
}