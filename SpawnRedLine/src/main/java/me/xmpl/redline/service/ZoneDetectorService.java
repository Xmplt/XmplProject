package me.xmpl.redline.service;

import me.xmpl.lib.hook.WorldGuardHook;
import me.xmpl.redline.SpawnRedLineExtension;
import me.xmpl.redline.model.RedLineZone;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;

public class ZoneDetectorService {
    private final SpawnRedLineExtension extension;

    public ZoneDetectorService(SpawnRedLineExtension extension) {
        this.extension = extension;
    }

    public RedLineZone detectZone(Location loc) {
        if (loc == null || loc.getWorld() == null) return RedLineZone.WILDERNESS;

        FileConfiguration rgCfg = extension.getCustomConfig("regions.yml").get();
        String targetWorld = rgCfg.getString("worldguard-regions.world-name", "world");
        if (!loc.getWorld().getName().equalsIgnoreCase(targetWorld)) {
            return RedLineZone.WILDERNESS;
        }

        String safeRegion = rgCfg.getString("worldguard-regions.safe-spawn-region", "spawn");
        String pvpOuterRegion = rgCfg.getString("worldguard-regions.pvp-outer-region", "spawn_pvp");

        // 1. Сначала проверяем внутренний мирный регион спавна (до красной линии)
        if (WorldGuardHook.isLocationInRegion(loc, safeRegion)) {
            return RedLineZone.SAFE_SPAWN;
        }

        // 2. Если игрок не во внутреннем регионе, но находится в большом регионе за красной линией
        if (WorldGuardHook.isLocationInRegion(loc, pvpOuterRegion)) {
            return RedLineZone.PVP_RING;
        }

        // 3. Иначе игрок убежал дальше в открытый мир
        return RedLineZone.WILDERNESS;
    }
}