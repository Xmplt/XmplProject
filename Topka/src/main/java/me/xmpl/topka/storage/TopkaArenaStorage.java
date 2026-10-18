package me.xmpl.topka.storage;

import me.xmpl.lib.config.CustomConfig;
import me.xmpl.lib.hook.WorldGuardHook;
import me.xmpl.lib.util.CuboidBox;
import me.xmpl.lib.util.LocationSerializerUtil;
import me.xmpl.topka.TopkaExtension;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

public class TopkaArenaStorage {
    private final TopkaExtension extension;
    private CuboidBox pvpRegion;
    private Location chest1Location;
    private Location chest2Location;
    private Location holo1Location;
    private Location holo2Location;
    private Location voteEmeraldBlockLocation;

    public TopkaArenaStorage(TopkaExtension extension) {
        this.extension = extension;
        reload();
    }

    public void reload() {
        FileConfiguration cfg = extension.getCustomConfig("arena-data.yml").get();
        if (cfg.isConfigurationSection("arena.pvp-region")) {
            String world = cfg.getString("arena.pvp-region.world", "world");
            int minX = cfg.getInt("arena.pvp-region.minX");
            int minY = cfg.getInt("arena.pvp-region.minY");
            int minZ = cfg.getInt("arena.pvp-region.minZ");
            int maxX = cfg.getInt("arena.pvp-region.maxX");
            int maxY = cfg.getInt("arena.pvp-region.maxY");
            int maxZ = cfg.getInt("arena.pvp-region.maxZ");
            this.pvpRegion = new CuboidBox("topka_pvp", world, minX, minY, minZ, maxX, maxY, maxZ);
        } else {
            this.pvpRegion = null;
        }

        this.chest1Location = LocationSerializerUtil.readLocation(cfg, "arena.chest1");
        this.chest2Location = LocationSerializerUtil.readLocation(cfg, "arena.chest2");
        this.holo1Location = LocationSerializerUtil.readLocation(cfg, "arena.holo1");
        this.holo2Location = LocationSerializerUtil.readLocation(cfg, "arena.holo2");
        this.voteEmeraldBlockLocation = LocationSerializerUtil.readLocation(cfg, "arena.vote-emerald-block");

        // Если раньше на точке голосования стоял физический изумрудный блок — очищаем его в AIR!
        if (this.voteEmeraldBlockLocation != null && this.voteEmeraldBlockLocation.getWorld() != null) {
            if (this.voteEmeraldBlockLocation.getBlock().getType() == Material.EMERALD_BLOCK) {
                this.voteEmeraldBlockLocation.getBlock().setType(Material.AIR);
            }
        }
    }

    public void savePvpRegion(CuboidBox box) {
        this.pvpRegion = box;
        CustomConfig cc = extension.getCustomConfig("arena-data.yml");
        FileConfiguration cfg = cc.get();
        cfg.set("arena.pvp-region.world", box.getWorldName());
        cfg.set("arena.pvp-region.minX", box.getMinX());
        cfg.set("arena.pvp-region.minY", box.getMinY());
        cfg.set("arena.pvp-region.minZ", box.getMinZ());
        cfg.set("arena.pvp-region.maxX", box.getMaxX());
        cfg.set("arena.pvp-region.maxY", box.getMaxY());
        cfg.set("arena.pvp-region.maxZ", box.getMaxZ());
        cc.save();
    }

    public void setChestLocation(int number, Location loc) {
        Location blockLoc = new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        CustomConfig cc = extension.getCustomConfig("arena-data.yml");
        if (number == 1) {
            this.chest1Location = blockLoc;
            this.holo1Location = null;
            cc.get().set("arena.holo1", null);
            LocationSerializerUtil.writeLocation(cc.get(), "arena.chest1", blockLoc);
        } else {
            this.chest2Location = blockLoc;
            this.holo2Location = null;
            cc.get().set("arena.holo2", null);
            LocationSerializerUtil.writeLocation(cc.get(), "arena.chest2", blockLoc);
        }
        cc.save();
    }

    public void setCustomHoloLocation(int number, Location loc) {
        Location blockLoc = new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        CustomConfig cc = extension.getCustomConfig("arena-data.yml");
        if (number == 1) {
            this.holo1Location = blockLoc;
            LocationSerializerUtil.writeLocation(cc.get(), "arena.holo1", blockLoc);
        } else {
            this.holo2Location = blockLoc;
            LocationSerializerUtil.writeLocation(cc.get(), "arena.holo2", blockLoc);
        }
        cc.save();
    }

    public void removeChest2() {
        this.chest2Location = null;
        this.holo2Location = null;
        CustomConfig cc = extension.getCustomConfig("arena-data.yml");
        cc.get().set("arena.chest2", null);
        cc.get().set("arena.holo2", null);
        cc.save();
    }

    /**
     * Сохраняет только координаты голограммы голосования БЕЗ установки физического блока в мире.
     */
    public void setVoteEmeraldBlockLocation(Location loc) {
        if (this.voteEmeraldBlockLocation != null && this.voteEmeraldBlockLocation.getWorld() != null) {
            if (this.voteEmeraldBlockLocation.getBlock().getType() == Material.EMERALD_BLOCK) {
                this.voteEmeraldBlockLocation.getBlock().setType(Material.AIR);
            }
        }
        Location blockLoc = new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        if (blockLoc.getBlock().getType() == Material.EMERALD_BLOCK) {
            blockLoc.getBlock().setType(Material.AIR);
        }
        this.voteEmeraldBlockLocation = blockLoc;

        CustomConfig cc = extension.getCustomConfig("arena-data.yml");
        LocationSerializerUtil.writeLocation(cc.get(), "arena.vote-emerald-block", blockLoc);
        cc.save();
    }

    public boolean isInPvpRegion(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        if (pvpRegion != null && pvpRegion.contains(loc)) {
            return true;
        }
        FileConfiguration rgCfg = extension.getCustomConfig("region-rules.yml").get();
        String wgRegionName = rgCfg.getString("region.worldguard-region", "");
        String wgWorldName = rgCfg.getString("region.world-name", "");
        if (wgRegionName != null && !wgRegionName.trim().isEmpty()) {
            if (wgWorldName != null && !wgWorldName.isEmpty() && !loc.getWorld().getName().equalsIgnoreCase(wgWorldName)) {
                return false;
            }
            return WorldGuardHook.isLocationInRegion(loc, wgRegionName.trim());
        }
        return false;
    }

    public boolean hasTwoChests() {
        return chest1Location != null && chest2Location != null;
    }

    public int getChestNumberAt(Location loc) {
        if (loc == null || loc.getWorld() == null) return false ? 1 : 0;
        if (matchesBlock(chest1Location, loc)) return 1;
        if (matchesBlock(chest2Location, loc)) return 2;
        return 0;
    }

    public boolean isVoteEmeraldBlock(Location loc) {
        return matchesBlock(voteEmeraldBlockLocation, loc);
    }

    private boolean matchesBlock(Location a, Location b) {
        if (a == null || b == null || a.getWorld() == null || b.getWorld() == null) return false;
        return a.getWorld().getName().equalsIgnoreCase(b.getWorld().getName())
                && a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }

    public CuboidBox getPvpRegion() { return pvpRegion; }
    public Location getChest1Location() { return chest1Location != null ? chest1Location.clone() : null; }
    public Location getChest2Location() { return chest2Location != null ? chest2Location.clone() : null; }
    public Location getHolo1BaseLocation() {
        if (holo1Location != null) return holo1Location.clone();
        return getChest1Location();
    }
    public Location getHolo2BaseLocation() {
        if (holo2Location != null) return holo2Location.clone();
        return getChest2Location();
    }
    public Location getVoteEmeraldBlockLocation() { return voteEmeraldBlockLocation != null ? voteEmeraldBlockLocation.clone() : null; }
}