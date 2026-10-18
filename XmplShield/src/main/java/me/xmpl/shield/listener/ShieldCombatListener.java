package me.xmpl.shield.listener;

import me.xmpl.shield.XmplShieldExtension;
import me.xmpl.shield.service.ShieldBroadcastService;
import me.xmpl.shield.service.ShieldMechanicService;
import me.xmpl.shield.storage.ShieldStatsStorage;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

public class ShieldCombatListener implements Listener {
    private final XmplShieldExtension extension;
    private final ShieldMechanicService mechanicService;
    private final ShieldBroadcastService broadcastService;
    private final ShieldStatsStorage statsStorage;

    public ShieldCombatListener(XmplShieldExtension extension,
                                ShieldMechanicService mechanicService,
                                ShieldBroadcastService broadcastService,
                                ShieldStatsStorage statsStorage) {
        this.extension = extension;
        this.mechanicService = mechanicService;
        this.broadcastService = broadcastService;
        this.statsStorage = statsStorage;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onShieldHitByAxe(EntityDamageByEntityEvent event) {
        if (extension.getConfig().getBoolean("mechanics.respect-cancelled-damage", true) && event.isCancelled()) {
            return;
        }
        if (!extension.getConfig().getBoolean("mechanics.disable-shield-on-axe-hit", true)) {
            return;
        }

        if (!(event.getEntity() instanceof Player) || !(event.getDamager() instanceof Player)) {
            return;
        }

        Player victim = (Player) event.getEntity();
        Player attacker = (Player) event.getDamager();
        if (victim.equals(attacker)) return;

        // 1. Проверяем, держит ли атакующий топор
        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        if (!mechanicService.isAxe(weapon)) return;

        // 2. Проверяем, стоит ли жертва в блоке со щитом (или поглотил ли щит урон в этом ударе)
        boolean blockedDamage = event.getDamage(EntityDamageByEntityEvent.DamageModifier.BLOCKING) < 0;
        if (!blockedDamage && !mechanicService.isActivelyBlockingWithShield(victim)) {
            return;
        }

        // 3. Сбиваем щит, ставим кулдаун и синхронно проигрываем звук ВСЕМ игрокам в радиусе!
        mechanicService.applyShieldDisableState(victim);
        broadcastService.broadcastShieldDisabled(victim, attacker);
        statsStorage.recordShieldBreak(attacker.getName(), victim.getName());
    }
}