package me.xmpl.funeffects.service;

import me.xmpl.funeffects.FunEffectsExtension;
import me.xmpl.lib.util.ChatUtil;
import me.xmpl.lib.util.SoundParticleUtil;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.ShulkerBullet;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class FartService {
    public static final String META_FART_SHOOTER = "xmpl_fart_shooter";

    private final FunEffectsExtension extension;

    public FartService(FunEffectsExtension extension) {
        this.extension = extension;
    }

    public void performFart(Player player) {
        FileConfiguration cfg = extension.getCustomConfig("fart.yml").get();
        if (cfg.getBoolean("require-permission", false)
                && !player.hasPermission(cfg.getString("permission", "funeffects.fart"))) {
            ChatUtil.sendMessage(player, extension.getConfig().getString("messages.no-permission"));
            return;
        }

        int burstShots = cfg.getInt("mechanics.burst-shots-before-cooldown", 2);
        int cdSeconds = cfg.getInt("mechanics.cooldown-seconds", 18);

        int remCd = extension.getCooldownTracker().tryUse(player.getUniqueId(), "FART", burstShots, cdSeconds);
        if (remCd > 0) {
            ChatUtil.sendMessage(player, cfg.getString("messages.cooldown"), "{time}", remCd);
            return;
        }

        double soundRadius = cfg.getDouble("mechanics.sound-radius", 15.0);
        String sound = cfg.getString("mechanics.sound", "ENTITY_SLIME_SQUISH");
        float pitch = (float) cfg.getDouble("mechanics.pitch", 0.5);
        SoundParticleUtil.playSoundInRadius(player.getLocation(), sound, soundRadius, 1.5f, pitch);

        ChatUtil.sendMessage(player, cfg.getString("messages.fart-used", "&eВы пукнули!"));

        Location spawnLoc = player.getEyeLocation().subtract(0, 0.3, 0)
                .add(player.getLocation().getDirection().normalize().multiply(0.8));
        Vector velocity = player.getEyeLocation().getDirection().normalize()
                .multiply(cfg.getDouble("mechanics.bullet-speed", 0.95));

        ShulkerBullet bullet = player.getWorld().spawn(spawnLoc, ShulkerBullet.class);
        bullet.setShooter(player);
        bullet.setMetadata(META_FART_SHOOTER, new FixedMetadataValue(extension.getCore(), player.getName()));

        int maxTicks = cfg.getInt("mechanics.max-flight-ticks", 35);
        List<String> particles = cfg.getStringList("mechanics.explosion-particles");

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                ticks++;
                if (!bullet.isValid() || bullet.isDead() || ticks >= maxTicks) {
                    detonateFartBullet(bullet.getLocation(), player);
                    if (!bullet.isDead()) bullet.remove();
                    cancel();
                    return;
                }

                bullet.setVelocity(velocity);
                for (String p : particles) {
                    SoundParticleUtil.spawnParticles(bullet.getLocation().subtract(0, 0.8, 0), p, 2, 0.15, 0.15, 0.15, 0.02);
                }

                // Если подлетел вплотную к другому игроку — взрываем сразу до ванильного удара шалкера!
                for (Entity near : bullet.getNearbyEntities(0.9, 0.9, 0.9)) {
                    if (near instanceof Player && !near.equals(player)) {
                        detonateFartBullet(bullet.getLocation(), player);
                        bullet.remove();
                        cancel();
                        return;
                    }
                }
            }
        }.runTaskTimer(extension.getCore(), 1L, 1L);
    }

    public void detonateFartBullet(Location loc, Player shooter) {
        if (loc == null || loc.getWorld() == null) return;
        FileConfiguration cfg = extension.getCustomConfig("fart.yml").get();

        double soundRadius = cfg.getDouble("mechanics.sound-radius", 15.0);
        SoundParticleUtil.playSoundInRadius(loc, cfg.getString("mechanics.sound", "ENTITY_SLIME_SQUISH"), soundRadius, 1.6f, 0.4f);

        for (String p : cfg.getStringList("mechanics.explosion-particles")) {
            SoundParticleUtil.spawnParticles(loc.clone().subtract(0, 0.5, 0), p, 18, 0.6, 0.6, 0.6, 0.05);
        }

        double blastRadius = cfg.getDouble("mechanics.blast-radius", 2.8);
        double damage = cfg.getDouble("mechanics.hit-damage", 0.5);
        Set<UUID> hitPlayers = new HashSet<>();

        for (Entity entity : loc.getWorld().getNearbyEntities(loc, blastRadius, blastRadius, blastRadius)) {
            if (!(entity instanceof Player)) continue;
            Player victim = (Player) entity;
            if (victim.equals(shooter) || hitPlayers.contains(victim.getUniqueId())) continue;

            hitPlayers.add(victim.getUniqueId());
            if (damage > 0) {
                victim.damage(damage, shooter);
            }

            if (shooter != null && shooter.isOnline()) {
                ChatUtil.sendMessage(shooter, cfg.getString("messages.fart-hit-shooter"), "{target}", victim.getName());
            }
            ChatUtil.sendMessage(victim, cfg.getString("messages.fart-hit-victim"),
                    "{shooter}", shooter != null ? shooter.getName() : "Неизвестный");
        }
    }
}