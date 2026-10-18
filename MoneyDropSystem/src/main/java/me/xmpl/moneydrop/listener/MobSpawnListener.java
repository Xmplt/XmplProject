package me.xmpl.moneydrop.listener;

import me.xmpl.moneydrop.service.SpawnerTagService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;

public class MobSpawnListener implements Listener {
    private final SpawnerTagService spawnerTagService;

    public MobSpawnListener(SpawnerTagService spawnerTagService) {
        this.spawnerTagService = spawnerTagService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (spawnerTagService.shouldBlockSpawnReason(event.getSpawnReason())) {
            spawnerTagService.markAsSpawnerMob(event.getEntity());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawnerSpawn(SpawnerSpawnEvent event) {
        spawnerTagService.markAsSpawnerMob(event.getEntity());
    }
}