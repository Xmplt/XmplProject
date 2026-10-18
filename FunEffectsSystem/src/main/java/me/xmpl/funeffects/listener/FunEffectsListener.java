package me.xmpl.funeffects.listener;

import me.xmpl.funeffects.FunEffectsExtension;
import me.xmpl.funeffects.model.SpitStyle;
import me.xmpl.funeffects.service.FartService;
import me.xmpl.funeffects.service.SpitService;
import me.xmpl.funeffects.service.SpitService.SpitMenuHolder;
import me.xmpl.lib.util.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LlamaSpit;
import org.bukkit.entity.Player;
import org.bukkit.entity.ShulkerBullet;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.potion.PotionEffectType;

public class FunEffectsListener implements Listener {
    private final FunEffectsExtension extension;

    public FunEffectsListener(FunEffectsExtension extension) {
        this.extension = extension;
    }

    // 1. Обработка попадания снаряда плевка (LlamaSpit) или пука (ShulkerBullet) в игрока
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onProjectileDamage(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();

        // Попадание плевка ламы
        if (damager instanceof LlamaSpit && damager.hasMetadata(SpitService.META_SPIT_SHOOTER)) {
            String shooterName = damager.getMetadata(SpitService.META_SPIT_SHOOTER).get(0).asString();
            String styleId = damager.hasMetadata(SpitService.META_SPIT_STYLE)
                    ? damager.getMetadata(SpitService.META_SPIT_STYLE).get(0).asString() : "DEFAULT";

            FileConfiguration spitCfg = extension.getCustomConfig("spits.yml").get();
            double dmg = spitCfg.getDouble("mechanics.hit-damage", 0.5);
            event.setDamage(dmg);

            if (event.getEntity() instanceof Player) {
                Player victim = (Player) event.getEntity();
                if (victim.getName().equalsIgnoreCase(shooterName)) {
                    event.setCancelled(true);
                    return;
                }

                ChatUtil.sendMessage(victim, spitCfg.getString("messages.spit-hit-victim"), "{shooter}", shooterName);
                Player shooter = Bukkit.getPlayerExact(shooterName);
                if (shooter != null && shooter.isOnline()) {
                    ChatUtil.sendMessage(shooter, spitCfg.getString("messages.spit-hit-shooter"), "{target}", victim.getName());
                }

                SpitStyle style = extension.getSpitService().getStyleById(styleId);
                extension.getSpitService().spawnGroundPersistenceParticles(victim.getLocation().add(0, 1.0, 0), style);
            }
            return;
        }

        // Попадание снаряда шалкера от /fart -> отменяем ванильный удар и левитацию, вызываем наш взрыв!
        if (damager instanceof ShulkerBullet && damager.hasMetadata(FartService.META_FART_SHOOTER)) {
            event.setCancelled(true);
            String shooterName = damager.getMetadata(FartService.META_FART_SHOOTER).get(0).asString();
            Player shooter = Bukkit.getPlayerExact(shooterName);
            extension.getFartService().detonateFartBullet(damager.getLocation(), shooter);
            damager.remove();

            if (event.getEntity() instanceof Player) {
                Player victim = (Player) event.getEntity();
                extension.runTask(() -> victim.removePotionEffect(PotionEffectType.LEVITATION));
            }
        }
    }

    @EventHandler
    public void onProjectileHitBlock(ProjectileHitEvent event) {
        if (event.getEntity() instanceof ShulkerBullet && event.getEntity().hasMetadata(FartService.META_FART_SHOOTER)) {
            String shooterName = event.getEntity().getMetadata(FartService.META_FART_SHOOTER).get(0).asString();
            Player shooter = Bukkit.getPlayerExact(shooterName);
            extension.getFartService().detonateFartBullet(event.getEntity().getLocation(), shooter);
            event.getEntity().remove();
        }
    }

    // 2. Запрет двигаться во время процесса /kaka
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMoveWhilePooping(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!extension.getKakaPipiService().isCurrentlyPooping(player.getUniqueId())) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        if (from.getBlockX() != to.getBlockX() || from.getBlockY() != to.getBlockY() || from.getBlockZ() != to.getBlockZ()) {
            extension.getKakaPipiService().cancelPoopingBecauseMoved(player);
        }
    }

    // 3. Обработка кликов в меню /spitlist
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSpitGuiClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Inventory top = event.getView().getTopInventory();
        if (top == null || !(top.getHolder() instanceof SpitMenuHolder)) return;

        event.setCancelled(true);
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(top)) return;

        Player player = (Player) event.getWhoClicked();
        SpitMenuHolder holder = (SpitMenuHolder) top.getHolder();
        FileConfiguration spitCfg = extension.getCustomConfig("spits.yml").get();
        int slot = event.getSlot();

        if (slot == spitCfg.getInt("gui.prev-page-slot", 18) && holder.page > 1) {
            extension.getSpitService().openSpitListGui(player, holder.page - 1);
            return;
        }
        if (slot == spitCfg.getInt("gui.next-page-slot", 26)) {
            extension.getSpitService().openSpitListGui(player, holder.page + 1);
            return;
        }

        SpitStyle clickedStyle = extension.getSpitService().getStyleByPageAndSlot(holder.page, slot);
        if (clickedStyle == null) return;

        if (!extension.getSpitService().hasStyleUnlocked(player, clickedStyle)) {
            ChatUtil.sendMessage(player, spitCfg.getString("messages.style-locked"));
            return;
        }

        extension.getPlayerStorage().setSelectedSpitId(player.getName(), clickedStyle.getId());
        ChatUtil.sendMessage(player, spitCfg.getString("messages.style-selected"), "{style}", clickedStyle.getDisplayName());
        extension.getSpitService().openSpitListGui(player, holder.page);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSpitGuiDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top != null && top.getHolder() instanceof SpitMenuHolder) {
            event.setCancelled(true);
        }
    }
}