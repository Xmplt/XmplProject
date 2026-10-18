package me.xmpl.moneydrop.service;

import me.xmpl.moneydrop.MoneyDropExtension;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.List;

public class SpawnerTagService {
    private static final String SPAWNER_META_KEY = "xmpl_spawner_mob";
    private final MoneyDropExtension extension;

    public SpawnerTagService(MoneyDropExtension extension) {
        this.extension = extension;
    }

    public boolean shouldBlockSpawnReason(SpawnReason reason) {
        FileConfiguration cfg = extension.getCustomConfig("blacklisted-entities.yml").get();
        if (!cfg.getBoolean("spawner-protection.block-spawner-mobs", true)) {
            return false;
        }
        List<String> blocked = cfg.getStringList("spawner-protection.blocked-spawn-reasons");
        for (String s : blocked) {
            if (s.equalsIgnoreCase(reason.name())) {
                return true;
            }
        }
        return false;
    }

    public void markAsSpawnerMob(Entity entity) {
        if (entity != null) {
            entity.setMetadata(SPAWNER_META_KEY, new FixedMetadataValue(extension.getCore(), true));
        }
    }

    public boolean isSpawnerMob(Entity entity) {
        if (entity == null) return false;
        FileConfiguration cfg = extension.getCustomConfig("blacklisted-entities.yml").get();
        if (!cfg.getBoolean("spawner-protection.block-spawner-mobs", true)) {
            return false;
        }
        return entity.hasMetadata(SPAWNER_META_KEY);
    }
}