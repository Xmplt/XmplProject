package me.xmpl.nexusclans.listener;

import me.xmpl.lib.util.ChatUtil;
import me.xmpl.nexusclans.NexusClansExtension;
import me.xmpl.nexusclans.model.ClanData;
import me.xmpl.nexusclans.model.ClanMember;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.projectiles.ProjectileSource;

public class ClanCombatAndRideListener implements Listener {
    private final NexusClansExtension extension;

    public ClanCombatAndRideListener(NexusClansExtension extension) {
        this.extension = extension;
    }

    // 1. Проверка дружественного огня между соклановцами и союзными кланами
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player victim = (Player) event.getEntity();
        Player attacker = extractAttacker(event.getDamager());
        if (attacker == null || attacker.equals(victim)) return;

        ClanData attClan = extension.getMainStorage().getPlayerClan(attacker.getName());
        ClanData vicClan = extension.getMainStorage().getPlayerClan(victim.getName());
        if (attClan == null || vicClan == null) return;

        // Внутри одного клана
        if (attClan.getClanKey().equalsIgnoreCase(vicClan.getClanKey())) {
            if (!attClan.isPvpEnabled()) {
                event.setCancelled(true);
            }
            return;
        }

        // Между союзными кланами (если хотя бы один клан включил teampvpon — урон проходит!)
        if (extension.getAlliancesStorage().areAllies(attClan.getClanKey(), vicClan.getClanKey())) {
            if (!extension.getAlliancesStorage().isEffectiveAllyPvpAllowed(attClan.getClanKey(), vicClan.getClanKey())) {
                event.setCancelled(true);
            }
        }
    }

    // 2. Начисление +1 и списание -1 уникальных очков клана при убийстве игрока из другого клана
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer != null && !killer.equals(victim)) {
            extension.getSmartPointsService().handleUniquePvpKillBetweenClans(killer, victim);
        }
    }

    // 3. Посадка на голову соклановца или союзника (ПКМ или Shift + ПКМ по игроку)
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!(event.getRightClicked() instanceof Player)) return;

        Player clicker = event.getPlayer();
        Player clicked = (Player) event.getRightClicked();
        if (extension.getGlowAndRideService().handlePlayerInteractEntity(clicker, clicked)) {
            event.setCancelled(true);
        }
    }

    // 4. Бросок сидящего на голове игрока при нажатии Shift + ПКМ в воздух или блок
    @EventHandler(priority = EventPriority.HIGH)
    public void onShiftRightClickThrow(PlayerInteractEvent event) {
        if (event.getHand() == EquipmentSlot.OFF_HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        if (player.isSneaking() && !player.getPassengers().isEmpty()) {
            if (extension.getGlowAndRideService().tryThrowPassengerFromHead(player)) {
                event.setCancelled(true);
            }
        }
    }

    // 5. Обновление lastSeen, показ оффлайн-кика и включение пакетной подсветки при заходе
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        ClanMember m = extension.getMainStorage().getMember(player.getName());
        if (m != null) {
            m.setPlayerName(player.getName());
            m.setLastSeenMillis(System.currentTimeMillis());
            extension.getMainStorage().saveMemberAsync(m);
        }

        String kickReason = extension.getMainStorage().consumeOfflineKickNotice(player.getName());
        if (kickReason != null) {
            String msg = extension.getClanCoreService().getMsgCfg().getString("messages.kick-offline-join-notice");
            ChatUtil.sendMessage(player, msg, "{reason}", kickReason);
        }

        extension.runTask(() -> extension.getGlowAndRideService().refreshPlayerGlow(player));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        player.eject();
        if (player.getVehicle() != null) {
            player.leaveVehicle();
        }
        ClanMember m = extension.getMainStorage().getMember(player.getName());
        if (m != null) {
            m.setLastSeenMillis(System.currentTimeMillis());
            extension.getMainStorage().saveMemberAsync(m);
        }
        extension.getClanCoreService().clearPendingOnQuit(player.getUniqueId());
    }

    private Player extractAttacker(Entity damager) {
        if (damager instanceof Player) return (Player) damager;
        if (damager instanceof Projectile) {
            ProjectileSource src = ((Projectile) damager).getShooter();
            if (src instanceof Player) return (Player) src;
        }
        return null;
    }
}