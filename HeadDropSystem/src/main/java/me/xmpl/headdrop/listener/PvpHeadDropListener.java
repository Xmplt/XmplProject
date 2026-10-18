package me.xmpl.headdrop.listener;

import me.xmpl.headdrop.HeadDropExtension;
import me.xmpl.headdrop.service.HeadFactoryService;
import me.xmpl.headdrop.storage.HeadStatsStorage;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

public class PvpHeadDropListener implements Listener {
    private final HeadDropExtension extension;
    private final HeadFactoryService headFactory;
    private final HeadStatsStorage statsStorage;

    public PvpHeadDropListener(HeadDropExtension extension, HeadFactoryService headFactory, HeadStatsStorage statsStorage) {
        this.extension = extension;
        this.headFactory = headFactory;
        this.statsStorage = statsStorage;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerPvpDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = resolvePlayerKiller(victim);

        // 1. Выпадает СТРОГО только если игрока убил другой игрок (при обычной смерти от мобов/падения/лавы НЕ выпадает!)
        if (killer == null || killer.equals(victim)) {
            return;
        }

        FileConfiguration cfg = extension.getConfig();

        // 2. Проверка шанса выпадения
        if (!headFactory.rollDropChance(killer)) {
            return;
        }

        // 3. Создаем голову со скином жертвы (на голове написано ТОЛЬКО чья она, без имени убийцы!)
        ItemStack headItem = headFactory.buildHeadForVictim(victim);

        if (event.getKeepInventory()) {
            victim.getWorld().dropItemNaturally(victim.getLocation(), headItem);
        } else {
            event.getDrops().add(headItem);
        }

        statsStorage.recordHeadDrop(killer.getName(), victim.getName());
        // Никаких сообщений в чат об отрубании головы не пишем!
    }

    private Player resolvePlayerKiller(Player victim) {
        if (victim.getKiller() != null) {
            return victim.getKiller();
        }
        if (victim.getLastDamageCause() instanceof EntityDamageByEntityEvent) {
            Entity damager = ((EntityDamageByEntityEvent) victim.getLastDamageCause()).getDamager();
            if (damager instanceof Player) {
                return (Player) damager;
            }
            if (damager instanceof Projectile && ((Projectile) damager).getShooter() instanceof Player) {
                return (Player) ((Projectile) damager).getShooter();
            }
        }
        return null;
    }
}