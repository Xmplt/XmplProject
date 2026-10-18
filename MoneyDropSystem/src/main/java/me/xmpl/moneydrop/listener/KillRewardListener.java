package me.xmpl.moneydrop.listener;

import me.xmpl.moneydrop.service.MobRewardService;
import me.xmpl.moneydrop.service.PvpStealService;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

public class KillRewardListener implements Listener {
    private final MobRewardService mobRewardService;
    private final PvpStealService pvpStealService;

    public KillRewardListener(MobRewardService mobRewardService, PvpStealService pvpStealService) {
        this.mobRewardService = mobRewardService;
        this.pvpStealService = pvpStealService;
    }

    // 1. Смерть моба или животного
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player) return; // Игроки обрабатываются отдельно в onPlayerDeath

        Player killer = entity.getKiller();
        if (killer == null) return;

        mobRewardService.handleMobKill(killer, entity);
    }

    // 2. Смерть игрока в ПвП
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null) return;

        pvpStealService.handlePlayerKill(killer, victim);
    }
}