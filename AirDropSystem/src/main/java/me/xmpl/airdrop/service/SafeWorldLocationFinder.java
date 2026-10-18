package me.xmpl.airdrop.service;

import me.xmpl.airdrop.AirDropExtension;
import me.xmpl.lib.hook.WorldGuardHook;
import me.xmpl.lib.util.RandomUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;

public class SafeWorldLocationFinder {
    private final AirDropExtension extension;
    private boolean searchInProgress = false;

    public SafeWorldLocationFinder(AirDropExtension extension) {
        this.extension = extension;
    }

    public boolean isSearchInProgress() {
        return searchInProgress;
    }

    public void findRandomWorldAndLocationNonBlocking(String lastWorldKey, BiConsumer<String, Location> callback) {
        if (searchInProgress) return;

        FileConfiguration wCfg = extension.getCustomConfig("worlds.yml").get();
        List<String> enabledWorlds = new ArrayList<>(wCfg.getStringList("enabled-worlds"));
        enabledWorlds.removeIf(wName -> Bukkit.getWorld(wName) == null);

        if (enabledWorlds.isEmpty()) {
            callback.accept(null, null);
            return;
        }

        Collections.shuffle(enabledWorlds);
        if (enabledWorlds.size() > 1 && enabledWorlds.get(0).equalsIgnoreCase(lastWorldKey)) {
            String first = enabledWorlds.remove(0);
            enabledWorlds.add(first);
        }

        int attemptsPerTick = Math.max(1, extension.getConfig().getInt("settings.search-attempts-per-tick", 2));
        int maxAttemptsPerWorld = Math.max(10, extension.getConfig().getInt("settings.max-attempts-per-world", 30));

        searchInProgress = true;

        new BukkitRunnable() {
            int currentWorldIndex = 0;
            int attemptsInCurrentWorld = 0;

            @Override
            public void run() {
                if (currentWorldIndex >= enabledWorlds.size()) {
                    searchInProgress = false;
                    cancel();
                    callback.accept(null, null);
                    return;
                }

                String worldName = enabledWorlds.get(currentWorldIndex);
                World world = Bukkit.getWorld(worldName);
                if (world == null) {
                    currentWorldIndex++;
                    attemptsInCurrentWorld = 0;
                    return;
                }

                for (int i = 0; i < attemptsPerTick; i++) {
                    attemptsInCurrentWorld++;
                    Location found = trySingleRandomCoordinate(world, worldName, wCfg);
                    if (found != null) {
                        searchInProgress = false;
                        cancel();
                        callback.accept(worldName, found);
                        return;
                    }

                    if (attemptsInCurrentWorld >= maxAttemptsPerWorld) {
                        currentWorldIndex++;
                        attemptsInCurrentWorld = 0;
                        break;
                    }
                }
            }
        }.runTaskTimer(extension.getCore(), 1L, 1L);
    }

    private Location trySingleRandomCoordinate(World world, String worldName, FileConfiguration wCfg) {
        String path = "worlds." + worldName + ".coordinates";

        int minX = wCfg.getInt(path + ".min-x", -1500);
        int maxX = wCfg.getInt(path + ".max-x", 1500);
        int minZ = wCfg.getInt(path + ".min-z", -1500);
        int maxZ = wCfg.getInt(path + ".max-z", 1500);
        int forbiddenRadius = wCfg.getInt(path + ".forbidden-center-radius", 250);
        long forbiddenSq = (long) forbiddenRadius * forbiddenRadius;

        int x = RandomUtil.nextInt(minX, maxX);
        int z = RandomUtil.nextInt(minZ, maxZ);

        if (forbiddenRadius > 0 && ((long) x * x + (long) z * z) < forbiddenSq) {
            return null;
        }

        if (!world.getWorldBorder().isInside(new Location(world, x, 64, z))) {
            return null;
        }

        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            world.loadChunk(chunkX, chunkZ, true);
        }

        Location candidate;
        if (world.getEnvironment() == World.Environment.NETHER) {
            candidate = checkNetherSafeSpot(world, x, z);
        } else if (world.getEnvironment() == World.Environment.THE_END) {
            candidate = checkEndSafeSpot(world, x, z);
        } else {
            candidate = checkOverworldSurfaceSpot(world, x, z);
        }

        if (candidate != null && isAreaFreeOfPlayerRegions(candidate, 4)) {
            return candidate;
        }
        return null;
    }

    private Location checkOverworldSurfaceSpot(World world, int x, int z) {
        int highestY = world.getHighestBlockYAt(x, z);
        if (highestY < 55 || highestY > 240) return null;

        int y = highestY;
        Block ground = world.getBlockAt(x, y - 1, z);

        int guard = 0;
        while (isFoliageOrAir(ground.getType()) && y > 50 && guard < 25) {
            y--;
            ground = world.getBlockAt(x, y - 1, z);
            guard++;
        }

        if (isHazardousGround(ground.getType())) return null;

        Location chestLoc = new Location(world, x, y, z);
        if (!hasClearAirAbove(chestLoc, 8)) return null;
        if (chestLoc.getBlock().getLightFromSky() < 14) return null;

        return chestLoc;
    }

    private Location checkNetherSafeSpot(World world, int x, int z) {
        for (int y = 96; y > 35; y--) {
            Block airBlock = world.getBlockAt(x, y, z);
            Block ground = world.getBlockAt(x, y - 1, z);

            if (airBlock.getType() == Material.AIR && ground.getType().isSolid() && !isHazardousGround(ground.getType())) {
                Location chestLoc = new Location(world, x, y, z);
                if (!hasClearAirAbove(chestLoc, 8)) continue;

                if (isSafeFromNetherLavaCliff(world, x, y - 1, z, 4)) {
                    return chestLoc;
                }
            }
        }
        return null;
    }

    private boolean isSafeFromNetherLavaCliff(World world, int cx, int groundY, int cz, int checkRadius) {
        for (int dx = -checkRadius; dx <= checkRadius; dx += 2) {
            for (int dz = -checkRadius; dz <= checkRadius; dz += 2) {
                boolean foundSolidFloor = false;
                for (int dy = 2; dy >= -3; dy--) {
                    Material m = world.getBlockAt(cx + dx, groundY + dy, cz + dz).getType();
                    if (m.name().contains("LAVA")) return false;
                    if (m.isSolid()) {
                        foundSolidFloor = true;
                        break;
                    }
                }
                if (!foundSolidFloor) return false;
            }
        }
        return true;
    }

    private Location checkEndSafeSpot(World world, int x, int z) {
        int y = world.getHighestBlockYAt(x, z);
        if (y < 48 || y > 120) return null;

        Block ground = world.getBlockAt(x, y - 1, z);
        if (ground.getType() != Material.ENDER_STONE) return null;

        int edgeCheck = 7;
        if (world.getHighestBlockYAt(x + edgeCheck, z) < 45) return null;
        if (world.getHighestBlockYAt(x - edgeCheck, z) < 45) return null;
        if (world.getHighestBlockYAt(x, z + edgeCheck) < 45) return null;
        if (world.getHighestBlockYAt(x, z - edgeCheck) < 45) return null;

        Location chestLoc = new Location(world, x, y, z);
        return hasClearAirAbove(chestLoc, 8) ? chestLoc : null;
    }

    private boolean isAreaFreeOfPlayerRegions(Location center, int radius) {
        for (int dx = -radius; dx <= radius; dx += radius) {
            for (int dz = -radius; dz <= radius; dz += radius) {
                if (!WorldGuardHook.isLocationFreeOfRegions(center.clone().add(dx, 0, dz))) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean isFoliageOrAir(Material m) {
        String n = m.name();
        return m == Material.AIR || n.contains("LEAVES") || n.contains("LOG") || n.contains("VINE");
    }

    private boolean isHazardousGround(Material m) {
        if (!m.isSolid()) return true;
        String n = m.name();
        return n.contains("WATER") || n.contains("LAVA") || n.contains("FIRE")
                || n.contains("MAGMA") || n.contains("CACTUS") || n.contains("LEAVES")
                || n.contains("FENCE") || n.contains("WALL");
    }

    private boolean hasClearAirAbove(Location base, int height) {
        for (int dy = 0; dy < height; dy++) {
            if (base.clone().add(0, dy, 0).getBlock().getType() != Material.AIR) {
                return false;
            }
        }
        return true;
    }
}