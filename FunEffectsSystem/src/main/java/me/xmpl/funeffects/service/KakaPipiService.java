package me.xmpl.funeffects.service;

import me.xmpl.funeffects.FunEffectsExtension;
import me.xmpl.funeffects.model.HazardDropItem;
import me.xmpl.funeffects.model.HazardDropItem.HazardType;
import me.xmpl.lib.util.ChatUtil;
import me.xmpl.lib.util.RandomUtil;
import me.xmpl.lib.util.SoundParticleUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class KakaPipiService {
    // Точный радиус наступания ступнями на лежащий на полу блок бетона (0.32 блока по X/Z)
    private static final double STEP_RADIUS_XZ_SQUARED = 0.32 * 0.32;
    private static final double STEP_MAX_DIFF_Y = 0.35;
    // Расстояние, на которое владелец должен отойти от своего предмета, прежде чем сможет сам в него наступить
    private static final double OWNER_LEAVE_RADIUS_XZ_SQUARED = 0.85 * 0.85;

    private final FunEffectsExtension extension;
    private final List<HazardDropItem> activeDrops = Collections.synchronizedList(new ArrayList<>());
    private final Set<UUID> currentlyPoopingPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> currentlyPeeingPlayers = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> stepMessageCooldowns = new ConcurrentHashMap<>();

    public KakaPipiService(FunEffectsExtension extension) {
        this.extension = extension;
    }

    public boolean isCurrentlyPooping(UUID uuid) {
        return currentlyPoopingPlayers.contains(uuid);
    }

    public void cancelPoopingBecauseMoved(Player player) {
        if (currentlyPoopingPlayers.remove(player.getUniqueId())) {
            String msg = extension.getCustomConfig("kaka.yml").get().getString("messages.cancelled-moved");
            ChatUtil.sendMessage(player, msg);
        }
    }

    public void startGroundStepCheckTask() {
        // Каждые 3 тика проверяем только ЛЕЖАЩИЕ на земле предметы и точное наступание ногами
        extension.runTaskTimer(() -> {
            long now = System.currentTimeMillis();
            synchronized (activeDrops) {
                Iterator<HazardDropItem> it = activeDrops.iterator();
                while (it.hasNext()) {
                    HazardDropItem drop = it.next();
                    Item item = drop.getItemEntity();
                    if (item == null || !item.isValid() || item.isDead() || now >= drop.getExpireAtMillis()) {
                        if (item != null && !item.isDead()) item.remove();
                        it.remove();
                        continue;
                    }

                    // Пока предмет летит в воздухе или только что появился — наступить в него нельзя!
                    if (!item.isOnGround() || (now - drop.getSpawnedAtMillis()) < 450L) {
                        continue;
                    }

                    Location itemLoc = item.getLocation();
                    for (Player player : itemLoc.getWorld().getPlayers()) {
                        if (!player.isOnline() || player.isDead()) continue;

                        Location pLoc = player.getLocation();
                        double dx = pLoc.getX() - itemLoc.getX();
                        double dz = pLoc.getZ() - itemLoc.getZ();
                        double distXzSq = dx * dx + dz * dz;
                        double diffY = Math.abs(pLoc.getY() - itemLoc.getY());

                        boolean isOwner = player.getName().equalsIgnoreCase(drop.getOwnerName());
                        if (isOwner) {
                            // Если владелец еще в процессе /kaka или /pipi — не триггерим на него
                            if (currentlyPoopingPlayers.contains(player.getUniqueId())
                                    || currentlyPeeingPlayers.contains(player.getUniqueId())) {
                                continue;
                            }
                            // Владелец должен сначала отойти от лежащего куска, прежде чем сможет сам в него наступить
                            if (!drop.isOwnerLeftArea()) {
                                if (distXzSq >= OWNER_LEAVE_RADIUS_XZ_SQUARED) {
                                    drop.setOwnerLeftArea(true);
                                }
                                continue;
                            }
                        }

                        // Строгая проверка: ноги игрока наступили прямо на лежащий предмет
                        if (distXzSq <= STEP_RADIUS_XZ_SQUARED && diffY <= STEP_MAX_DIFF_Y) {
                            handlePlayerSteppedOn(player, drop);
                        }
                    }
                }
            }
        }, 3L, 3L);
    }

    private void handlePlayerSteppedOn(Player victim, HazardDropItem drop) {
        FileConfiguration cfg = extension.getCustomConfig(drop.getType() == HazardType.KAKA ? "kaka.yml" : "pipi.yml").get();
        int stepCdSec = cfg.getInt("mechanics.step-message-cooldown-seconds", 2);

        String cdKey = victim.getUniqueId() + ":" + drop.getType().name() + ":" + drop.getOwnerName().toLowerCase();
        long now = System.currentTimeMillis();
        Long lastNotified = stepMessageCooldowns.get(cdKey);
        if (lastNotified != null && (now - lastNotified) < (stepCdSec * 1000L)) {
            return;
        }
        stepMessageCooldowns.put(cdKey, now);

        if (victim.getName().equalsIgnoreCase(drop.getOwnerName())) {
            ChatUtil.sendMessage(victim, cfg.getString("messages.stepped-own"));
        } else {
            ChatUtil.sendMessage(victim, cfg.getString("messages.stepped-other-victim"), "{owner}", drop.getOwnerName());
            Player owner = Bukkit.getPlayerExact(drop.getOwnerName());
            if (owner != null && owner.isOnline()) {
                ChatUtil.sendMessage(owner, cfg.getString("messages.stepped-other-owner"), "{victim}", victim.getName());
            }
        }
    }

    /**
     * Вычисляет угол поворота ТЕЛА (туловища) модели игрока, а не камеры:
     * когда игрок стоит на месте и крутит камерой (в том числе в F5), в Minecraft туловище
     * имеет мертвую зону ±50 градусов от головы и не крутится вместе с прицелом.
     */
    private float updateBodyYaw(Player player, float currentBodyYaw) {
        try {
            Object entityPlayer = player.getClass().getMethod("getHandle").invoke(player);
            Field renderYawOffsetField = findField(entityPlayer.getClass(), "aN");
            if (renderYawOffsetField != null) {
                float nmsBodyYaw = renderYawOffsetField.getFloat(entityPlayer);
                if (!Float.isNaN(nmsBodyYaw)) {
                    currentBodyYaw = nmsBodyYaw;
                }
            }
        } catch (Exception ignored) {}

        float headYaw = player.getLocation().getYaw();
        float delta = wrapDegrees(headYaw - currentBodyYaw);
        // Точная формула поворота туловища стоящего игрока в клиенте Minecraft 1.12.2
        if (delta < -50.0f) {
            currentBodyYaw = headYaw + 50.0f;
        } else if (delta > 50.0f) {
            currentBodyYaw = headYaw - 50.0f;
        }
        return currentBodyYaw;
    }

    private Field findField(Class<?> clazz, String name) {
        Class<?> curr = clazz;
        while (curr != null && curr != Object.class) {
            try {
                Field f = curr.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e) {
                curr = curr.getSuperclass();
            }
        }
        return null;
    }

    private float wrapDegrees(float value) {
        value = value % 360.0f;
        if (value >= 180.0f) value -= 360.0f;
        if (value < -180.0f) value += 360.0f;
        return value;
    }

    private Vector getHorizontalDirectionFromYaw(float yawDegrees) {
        double rad = Math.toRadians(yawDegrees);
        return new Vector(-Math.sin(rad), 0.0, Math.cos(rad));
    }

    /**
     * Запуск команды /kaka: спавнит коричневый бетон строго с задней стороны модели туловища игрока.
     */
    public void startKaka(Player player) {
        FileConfiguration cfg = extension.getCustomConfig("kaka.yml").get();
        if (cfg.getBoolean("require-permission", true)
                && !player.hasPermission(cfg.getString("permission", "funeffects.kaka"))) {
            ChatUtil.sendMessage(player, cfg.getString("messages.no-permission"));
            return;
        }

        int cdSec = cfg.getInt("mechanics.cooldown-seconds", 30);
        int remCd = extension.getCooldownTracker().tryUse(player.getUniqueId(), "KAKA", 1, cdSec);
        if (remCd > 0) {
            ChatUtil.sendMessage(player, cfg.getString("messages.cooldown"), "{time}", remCd);
            return;
        }

        double radius = cfg.getDouble("mechanics.broadcast-radius", 15.0);
        ChatUtil.sendMessage(player, cfg.getString("messages.started-self"));
        broadcastToNearby(player, radius, cfg.getString("messages.started-nearby"), "{player}", player.getName());

        int totalPieces = RandomUtil.nextInt(cfg.getInt("mechanics.min-pieces", 3), cfg.getInt("mechanics.max-pieces", 8));
        long intervalTicks = cfg.getLong("mechanics.drop-interval-ticks", 20L);
        int staySeconds = cfg.getInt("mechanics.stay-on-ground-seconds", 12);
        String sound = cfg.getString("mechanics.sound", "ENTITY_ITEM_PICKUP");
        float pitch = (float) cfg.getDouble("mechanics.pitch", 0.6);

        UUID uuid = player.getUniqueId();
        currentlyPoopingPlayers.add(uuid);
        final float initialBodyYaw = player.getLocation().getYaw();

        new BukkitRunnable() {
            int dropped = 0;
            float trackedBodyYaw = initialBodyYaw;

            @Override
            public void run() {
                if (!player.isOnline() || !currentlyPoopingPlayers.contains(uuid) || dropped >= totalPieces) {
                    currentlyPoopingPlayers.remove(uuid);
                    cancel();
                    return;
                }

                dropped++;
                trackedBodyYaw = updateBodyYaw(player, trackedBodyYaw);

                // Вектор строго позади туловища модели игрока (вплотную к спине/пятой точке, а не в воздухе)
                Vector bodyBackUnit = getHorizontalDirectionFromYaw(trackedBodyYaw).multiply(-1.0);
                double spawnHeight = player.isSneaking() ? 0.16 : 0.22;
                Location dropLoc = player.getLocation().add(0, spawnHeight, 0).add(bodyBackUnit.clone().multiply(0.18));

                ItemStack brownConcrete = new ItemStack(Material.CONCRETE, 1, (short) 12);
                Item item = player.getWorld().dropItem(dropLoc, brownConcrete);
                item.setPickupDelay(Integer.MAX_VALUE);
                item.setCustomName(UUID.randomUUID().toString());
                item.setCustomNameVisible(false);
                // Падает аккуратно вниз из-под задней части модели без вылета в воздух
                item.setVelocity(bodyBackUnit.multiply(0.05).setY(-0.08));

                SoundParticleUtil.playSoundInRadius(dropLoc, sound, radius, 1.3f, pitch);
                activeDrops.add(new HazardDropItem(item, player.getName(), HazardType.KAKA,
                        System.currentTimeMillis() + (staySeconds * 1000L)));

                if (dropped >= totalPieces) {
                    currentlyPoopingPlayers.remove(uuid);
                    cancel();
                }
            }
        }.runTaskTimer(extension.getCore(), 5L, intervalTicks);
    }

    /**
     * Запуск команды /pipi: струя желтого бетона спереди из области таза по горизонтальному направлению тела.
     */
    public void startPipi(Player player) {
        FileConfiguration cfg = extension.getCustomConfig("pipi.yml").get();
        if (cfg.getBoolean("require-permission", false)
                && !player.hasPermission(cfg.getString("permission", "funeffects.pipi"))) {
            ChatUtil.sendMessage(player, cfg.getString("messages.no-permission"));
            return;
        }

        int cdSec = cfg.getInt("mechanics.cooldown-seconds", 25);
        int remCd = extension.getCooldownTracker().tryUse(player.getUniqueId(), "PIPI", 1, cdSec);
        if (remCd > 0) {
            ChatUtil.sendMessage(player, cfg.getString("messages.cooldown"), "{time}", remCd);
            return;
        }

        double radius = cfg.getDouble("mechanics.broadcast-radius", 15.0);
        ChatUtil.sendMessage(player, cfg.getString("messages.started-self"));
        broadcastToNearby(player, radius, cfg.getString("messages.started-nearby"), "{player}", player.getName());

        int totalPieces = cfg.getInt("mechanics.total-pieces", 50);
        long intervalTicks = cfg.getLong("mechanics.drop-interval-ticks", 2L);
        int staySeconds = cfg.getInt("mechanics.stay-on-ground-seconds", 8);
        String sound = cfg.getString("mechanics.sound", "BLOCK_WATER_AMBIENT");
        float pitch = (float) cfg.getDouble("mechanics.pitch", 1.2);

        UUID uuid = player.getUniqueId();
        currentlyPeeingPlayers.add(uuid);

        new BukkitRunnable() {
            int dropped = 0;

            @Override
            public void run() {
                if (!player.isOnline() || dropped >= totalPieces) {
                    currentlyPeeingPlayers.remove(uuid);
                    cancel();
                    return;
                }

                dropped++;
                Vector horizForward = getHorizontalDirectionFromYaw(player.getLocation().getYaw());
                Location dropLoc = player.getLocation().add(0, 0.58, 0).add(horizForward.clone().multiply(0.22));

                ItemStack yellowConcrete = new ItemStack(Material.CONCRETE, 1, (short) 4);
                Item item = player.getWorld().dropItem(dropLoc, yellowConcrete);
                item.setPickupDelay(Integer.MAX_VALUE);
                item.setCustomName(UUID.randomUUID().toString());
                item.setCustomNameVisible(false);
                item.setVelocity(horizForward.multiply(0.38).setY(0.08));

                if (dropped % 5 == 1) {
                    SoundParticleUtil.playSoundInRadius(dropLoc, sound, radius, 1.2f, pitch);
                }

                activeDrops.add(new HazardDropItem(item, player.getName(), HazardType.PIPI,
                        System.currentTimeMillis() + (staySeconds * 1000L)));

                if (dropped >= totalPieces) {
                    currentlyPeeingPlayers.remove(uuid);
                    cancel();
                }
            }
        }.runTaskTimer(extension.getCore(), 1L, intervalTicks);
    }

    private void broadcastToNearby(Player origin, double radius, String message, Object... replacements) {
        double rSq = radius * radius;
        for (Player other : origin.getWorld().getPlayers()) {
            if (other.equals(origin)) continue;
            if (other.getLocation().distanceSquared(origin.getLocation()) <= rSq) {
                ChatUtil.sendMessage(other, message, replacements);
            }
        }
    }

    public void cleanupAllDrops() {
        synchronized (activeDrops) {
            for (HazardDropItem drop : activeDrops) {
                if (drop.getItemEntity() != null && !drop.getItemEntity().isDead()) {
                    drop.getItemEntity().remove();
                }
            }
            activeDrops.clear();
        }
        currentlyPoopingPlayers.clear();
        currentlyPeeingPlayers.clear();
    }
}