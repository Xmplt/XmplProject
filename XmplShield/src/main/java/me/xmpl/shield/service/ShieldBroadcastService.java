package me.xmpl.shield.service;

import me.xmpl.lib.util.ActionBarUtil;
import me.xmpl.lib.util.ChatUtil;
import me.xmpl.lib.util.SoundParticleUtil;
import me.xmpl.shield.XmplShieldExtension;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public class ShieldBroadcastService {
    private final XmplShieldExtension extension;

    public ShieldBroadcastService(XmplShieldExtension extension) {
        this.extension = extension;
    }

    /**
     * Вызывается, когда щит игрока пробивают топором в бою
     */
    public void broadcastShieldDisabled(Player victim, Player attacker) {
        Location loc = victim.getLocation();
        double radius = extension.getConfig().getDouble("mechanics.broadcast-radius", 22.0);
        FileConfiguration soundCfg = extension.getCustomConfig("sounds.yml").get();

        // 1. Основной звук пробития щита для всех в радиусе
        if (soundCfg.getBoolean("shield-disable-sound.enabled", true)) {
            String sound = soundCfg.getString("shield-disable-sound.sound", "ITEM_SHIELD_BREAK");
            float volume = (float) soundCfg.getDouble("shield-disable-sound.volume", 1.5);
            float pitch = (float) soundCfg.getDouble("shield-disable-sound.pitch", 0.9);
            SoundParticleUtil.playSoundInRadius(loc, sound, radius, volume, pitch);

            // Дополнительный звуковой слой для четкости в замесе
            if (soundCfg.getBoolean("shield-disable-sound.extra-layer.enabled", true)) {
                String extraSound = soundCfg.getString("shield-disable-sound.extra-layer.sound", "ENTITY_ZOMBIE_BREAK_DOOR_WOOD");
                float extraVol = (float) soundCfg.getDouble("shield-disable-sound.extra-layer.volume", 0.55);
                float extraPitch = (float) soundCfg.getDouble("shield-disable-sound.extra-layer.pitch", 1.4);
                SoundParticleUtil.playSoundInRadius(loc, extraSound, radius, extraVol, extraPitch);
            }
        }

        // 2. Визуальные частицы и ActionBar
        playVisualsAndNotifications(victim, attacker, loc);
    }

    /**
     * Вызывается, когда щит полностью ломается по прочности (исчезает)
     */
    public void broadcastShieldDestroyed(Player victim) {
        Location loc = victim.getLocation();
        double radius = extension.getConfig().getDouble("mechanics.broadcast-radius", 22.0);
        FileConfiguration soundCfg = extension.getCustomConfig("sounds.yml").get();

        if (soundCfg.getBoolean("shield-destroy-sound.enabled", true)) {
            String sound = soundCfg.getString("shield-destroy-sound.sound", "ITEM_SHIELD_BREAK");
            float volume = (float) soundCfg.getDouble("shield-destroy-sound.volume", 2.0);
            float pitch = (float) soundCfg.getDouble("shield-destroy-sound.pitch", 0.7);
            SoundParticleUtil.playSoundInRadius(loc, sound, radius, volume, pitch);
        }

        playVisualsAndNotifications(victim, null, loc);
    }

    private void playVisualsAndNotifications(Player victim, Player attacker, Location loc) {
        FileConfiguration effCfg = extension.getCustomConfig("effects.yml").get();

        if (effCfg.getBoolean("particles.enabled", true)) {
            SoundParticleUtil.spawnParticles(
                    loc,
                    effCfg.getString("particles.type", "CRIT_MAGIC"),
                    effCfg.getInt("particles.count", 25),
                    effCfg.getDouble("particles.offset-x", 0.35),
                    effCfg.getDouble("particles.offset-y", 0.45),
                    effCfg.getDouble("particles.offset-z", 0.35),
                    effCfg.getDouble("particles.speed", 0.15)
            );
        }

        if (attacker != null) {
            if (effCfg.getBoolean("notifications.actionbar-attacker-enabled", true)) {
                String msg = ChatUtil.applyReplacements(effCfg.getString("notifications.actionbar-attacker"),
                        "{victim}", victim.getName(), "{attacker}", attacker.getName());
                ActionBarUtil.sendActionBar(attacker, msg);
            }
            if (effCfg.getBoolean("notifications.actionbar-victim-enabled", true)) {
                String msg = ChatUtil.applyReplacements(effCfg.getString("notifications.actionbar-victim"),
                        "{victim}", victim.getName(), "{attacker}", attacker.getName());
                ActionBarUtil.sendActionBar(victim, msg);
            }
        }
    }
}