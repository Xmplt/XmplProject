package me.xmpl.killsystem.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public class TopSignEntry {
    private final String id;
    private final int rank; // Место в топе: 1, 2, ... 100 и т.д.
    private final String worldName;
    private final int x, y, z;

    public TopSignEntry(String id, int rank, String worldName, int x, int y, int z) {
        this.id = id;
        this.rank = rank;
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Location toLocation() {
        World w = Bukkit.getWorld(worldName);
        return w != null ? new Location(w, x, y, z) : null;
    }

    public boolean matchesBlock(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        return loc.getWorld().getName().equalsIgnoreCase(worldName)
                && loc.getBlockX() == x
                && loc.getBlockY() == y
                && loc.getBlockZ() == z;
    }

    public String getId() { return id; }
    public int getRank() { return rank; }
    public String getWorldName() { return worldName; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
}