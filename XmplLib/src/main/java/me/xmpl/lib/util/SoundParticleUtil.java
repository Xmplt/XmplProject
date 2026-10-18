package me.xmpl.lib.util;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;

public final class SoundParticleUtil {
    private SoundParticleUtil() {}

    /**
     * Синхронно проигрывает звук всем игрокам в точном радиусе от точки события
     */
    public static void playSoundInRadius(Location origin, String soundName, double radius, float volume, float pitch) {
        if (origin == null || origin.getWorld() == null || soundName == null) return;

        Sound sound;
        try {
            sound = Sound.valueOf(soundName.toUpperCase());
        } catch (IllegalArgumentException e) {
            sound = Sound.ITEM_SHIELD_BREAK;
        }

        World world = origin.getWorld();
        double radiusSquared = radius * radius;

        for (Player listener : world.getPlayers()) {
            if (listener.getLocation().distanceSquared(origin) <= radiusSquared) {
                // Воспроизводим звук в координатах жертвы персонально для каждого игрока в радиусе (категория PLAYERS)
                listener.playSound(origin, sound, SoundCategory.PLAYERS, volume, pitch);
            }
        }
    }

    /**
     * Спавнит частицы в точке пробития щита для визуальной читаемости в файте
     */
    public static void spawnParticles(Location origin, String particleName, int count, double offsetX, double offsetY, double offsetZ, double extra) {
        if (origin == null || origin.getWorld() == null || particleName == null) return;

        try {
            Particle particle = Particle.valueOf(particleName.toUpperCase());
            origin.getWorld().spawnParticle(particle, origin.clone().add(0, 1.0, 0), count, offsetX, offsetY, offsetZ, extra);
        } catch (IllegalArgumentException ignored) {}
    }
}