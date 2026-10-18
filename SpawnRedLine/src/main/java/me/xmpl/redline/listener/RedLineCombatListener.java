package me.xmpl.redline.listener;

import me.xmpl.redline.SpawnRedLineExtension;
import me.xmpl.redline.model.RedLineZone;
import me.xmpl.redline.service.RedLineMarkService;
import me.xmpl.redline.service.ZoneDetectorService;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.projectiles.ProjectileSource;

public class RedLineCombatListener implements Listener {
    private final SpawnRedLineExtension extension;
    private final RedLineMarkService markService;
    private final ZoneDetectorService zoneDetector;

    public RedLineCombatListener(SpawnRedLineExtension extension,
                                 RedLineMarkService markService,
                                 ZoneDetectorService zoneDetector) {
        this.extension = extension;
        this.markService = markService;
        this.zoneDetector = zoneDetector;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPvpInSpawnZones(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player victim = (Player) event.getEntity();
        Player attacker = extractAttacker(event.getDamager());
        if (attacker == null || attacker.equals(victim)) return;

        // Если хелпер в ванише бьет палкой хелпера — не вмешиваемся (это обрабатывает XmplEss)
        if (attacker.hasMetadata("xmpl_vanished")) return;

        RedLineZone victimZone = zoneDetector.detectZone(victim.getLocation());
        RedLineZone attackerZone = zoneDetector.detectZone(attacker.getLocation());

        // Если ни один из игроков не находится в регионах спавна — работает обычное ПвП сервера
        if (victimZone == RedLineZone.WILDERNESS && attackerZone == RedLineZone.WILDERNESS) {
            return;
        }

        // Если хотя бы один из игроков находится во внутреннем мирном спавне (SAFE_SPAWN):
        if (victimZone == RedLineZone.SAFE_SPAWN || attackerZone == RedLineZone.SAFE_SPAWN) {
            boolean victimMarked = markService.isMarkedAndVulnerable(victim);
            boolean attackerMarked = markService.isMarkedAndVulnerable(attacker);

            // Драться на спавне (или через границу красной линии) могут СТРОГО ТОЛЬКО ДВА ИГРОКА С МЕТКОЙ!
            if (victimMarked && attackerMarked) {
                event.setCancelled(false); // Пробиваем флаг pvp: deny от WorldGuard на спавне!

                if (extension.getConfig().getBoolean("mechanics.reset-timer-on-marked-hit", false)) {
                    int sec = extension.getConfig().getInt("mechanics.vulnerability-seconds", 20);
                    if (victimZone == RedLineZone.SAFE_SPAWN) {
                        markService.getData(victim).startSafeSpawnCountdown(sec);
                    }
                    if (attackerZone == RedLineZone.SAFE_SPAWN) {
                        markService.getData(attacker).startSafeSpawnCountdown(sec);
                    }
                }
            } else {
                // Если хотя бы у одного нет метки — бить нельзя!
                event.setCancelled(true);
            }
        }
    }

    private Player extractAttacker(Entity damager) {
        if (damager instanceof Player) {
            return (Player) damager;
        }
        if (damager instanceof Projectile && extension.getConfig().getBoolean("mechanics.allow-projectiles-between-marked", true)) {
            ProjectileSource shooter = ((Projectile) damager).getShooter();
            if (shooter instanceof Player) {
                return (Player) shooter;
            }
        }
        return null;
    }
}