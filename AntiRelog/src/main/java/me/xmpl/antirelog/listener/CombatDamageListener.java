package me.xmpl.antirelog.listener;

import me.xmpl.antirelog.AntiRelogExtension;
import me.xmpl.antirelog.service.CombatManager;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;

public class CombatDamageListener implements Listener {
    private final AntiRelogExtension extension;
    private final CombatManager combatManager;

    public CombatDamageListener(AntiRelogExtension extension, CombatManager combatManager) {
        this.extension = extension;
        this.combatManager = combatManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDamageByPlayer(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        if (event.getFinalDamage() <= 0.0) return;

        Player victim = (Player) event.getEntity();
        Player attacker = extractAttacker(event.getDamager());

        if (attacker == null || attacker.equals(victim)) return;

        // Если хелпер в ВАНИШЕ бьет палкой на отдачу — режим ПвП НЕ включается!
        // Если он бьет без ваниша — ПвП включится.
        if (attacker.hasMetadata("xmpl_vanished")) {
            ItemStack hand = attacker.getInventory().getItemInMainHand();
            if (hand != null && hand.getType() == Material.STICK && hand.hasItemMeta() && hand.getItemMeta().hasLore()) {
                return;
            }
        }

        combatManager.tagPlayer(victim, attacker.getName());
        combatManager.tagPlayer(attacker, victim.getName());
    }

    private Player extractAttacker(Entity damager) {
        if (damager instanceof Player) {
            return (Player) damager;
        }
        if (damager instanceof Projectile && extension.getConfig().getBoolean("pvp.tag-on-projectiles", true)) {
            Projectile projectile = (Projectile) damager;
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player) {
                return (Player) shooter;
            }
        }
        return null;
    }
}