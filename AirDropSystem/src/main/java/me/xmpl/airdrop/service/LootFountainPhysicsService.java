package me.xmpl.airdrop.service;

import me.xmpl.airdrop.AirDropExtension;
import me.xmpl.lib.util.BlockAndGlowUtil;
import me.xmpl.lib.util.RandomUtil;
import me.xmpl.lib.util.SoundParticleUtil;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

public class LootFountainPhysicsService {
    private final AirDropExtension extension;

    public LootFountainPhysicsService(AirDropExtension extension) {
        this.extension = extension;
    }

    public void launchSingleItemFromChest(Location chestLoc, String worldKey, ItemStack itemStack) {
        if (chestLoc == null || chestLoc.getWorld() == null || itemStack == null) return;

        FileConfiguration mainCfg = extension.getConfig();
        FileConfiguration wCfg = extension.getCustomConfig("worlds.yml").get();

        // Высота подъема над сундуком перед разлетом (по умолчанию 6.0 блоков)
        double targetRiseHeight = mainCfg.getDouble("drop-physics.rise-height-blocks", 6.0);
        double configuredVelY = mainCfg.getDouble("drop-physics.rise-up-velocity-y", 0.45);
        final double riseVelY = Math.max(0.45, configuredVelY);
        final int maxRiseTicks = Math.max(15, mainCfg.getInt("drop-physics.rise-up-ticks", 15));
        boolean whiteGlow = mainCfg.getBoolean("drop-physics.white-glow-enabled", true);

        String throwPath = "worlds." + worldKey + ".drop-throw.";
        double minBlocks = wCfg.getDouble(throwPath + "min-distance-blocks", 15.0);
        double maxBlocks = wCfg.getDouble(throwPath + "max-distance-blocks", 26.0);
        double verticalBoost = wCfg.getDouble(throwPath + "arc-vertical-boost", 0.48);

        Location spawnOrigin = chestLoc.clone().add(0.5, 1.05, 0.5);
        final double targetPeakY = chestLoc.getY() + 1.0 + targetRiseHeight;

        Item itemEntity = chestLoc.getWorld().dropItem(spawnOrigin, itemStack);
        itemEntity.setPickupDelay(25);

        if (whiteGlow) {
            BlockAndGlowUtil.applyWhiteGlow(itemEntity);
        }

        // Этап 1: Поднимаем предмет вертикально вверх на ~6 блоков над сундуком
        itemEntity.setVelocity(new Vector(0.0, riseVelY, 0.0));
        SoundParticleUtil.playSoundInRadius(spawnOrigin, Sound.ENTITY_ITEM_PICKUP.name(), 25.0, 1.2f, 1.4f);

        new BukkitRunnable() {
            int ticks = 0;
            boolean launchedOutward = false;

            @Override
            public void run() {
                if (!itemEntity.isValid() || itemEntity.isDead()) {
                    cancel();
                    return;
                }

                ticks++;

                if (!launchedOutward) {
                    boolean reachedHeight = itemEntity.getLocation().getY() >= targetPeakY;
                    if (!reachedHeight && ticks < maxRiseTicks) {
                        // Поддерживаем вертикальный подъем строго вверх по центру сундука до высоты 6 блоков
                        itemEntity.setVelocity(new Vector(0.0, riseVelY, 0.0));
                        chestLoc.getWorld().spawnParticle(Particle.FIREWORKS_SPARK, itemEntity.getLocation(), 1, 0.03, 0.03, 0.03, 0.01);
                        return;
                    }

                    // Этап 2: В верхней точке (6 блоков над сундуком) выстреливаем предмет в случайную сторону!
                    launchedOutward = true;
                    double angle = ThreadLocalRandom.current().nextDouble(0.0, Math.PI * 2.0);
                    double targetDist = RandomUtil.nextDouble(minBlocks, maxBlocks);

                    double horizontalSpeed = targetDist * 0.052;
                    double vx = Math.cos(angle) * horizontalSpeed;
                    double vz = Math.sin(angle) * horizontalSpeed;

                    itemEntity.setVelocity(new Vector(vx, verticalBoost, vz));
                    SoundParticleUtil.playSoundInRadius(itemEntity.getLocation(), Sound.ENTITY_SHULKER_SHOOT.name(), 30.0, 1.3f, 1.2f);
                }

                if (itemEntity.isOnGround() || ticks > 120) {
                    cancel();
                    return;
                }

                chestLoc.getWorld().spawnParticle(Particle.FIREWORKS_SPARK, itemEntity.getLocation(), 1, 0.05, 0.05, 0.05, 0.01);
            }
        }.runTaskTimer(extension.getCore(), 1L, 1L);
    }
}