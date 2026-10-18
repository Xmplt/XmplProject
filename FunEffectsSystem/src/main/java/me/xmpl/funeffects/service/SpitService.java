package me.xmpl.funeffects.service;

import me.xmpl.funeffects.FunEffectsExtension;
import me.xmpl.funeffects.model.SpitStyle;
import me.xmpl.lib.util.ChatUtil;
import me.xmpl.lib.util.ItemBuilder;
import me.xmpl.lib.util.SoundParticleUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LlamaSpit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class SpitService {
    public static final String META_SPIT_SHOOTER = "xmpl_spit_shooter";
    public static final String META_SPIT_STYLE = "xmpl_spit_style";

    public static class SpitMenuHolder implements InventoryHolder {
        public final int page;
        public SpitMenuHolder(int page) { this.page = page; }
        @Override public Inventory getInventory() { return null; }
    }

    private final FunEffectsExtension extension;
    private final Map<String, SpitStyle> styles = new LinkedHashMap<>();
    private int maxPage = 1;

    public SpitService(FunEffectsExtension extension) {
        this.extension = extension;
        reload();
    }

    public void reload() {
        styles.clear();
        maxPage = 1;
        FileConfiguration cfg = extension.getCustomConfig("spits.yml").get();
        ConfigurationSection sec = cfg.getConfigurationSection("styles");
        if (sec == null) return;

        for (String key : sec.getKeys(false)) {
            int page = Math.max(1, sec.getInt(key + ".page", 1));
            if (page > maxPage) maxPage = page;

            int slot = sec.getInt(key + ".slot", 10);
            boolean defUnlocked = sec.getBoolean(key + ".default-unlocked", false);
            String perm = sec.getString(key + ".permission", "");
            Material mat = Material.matchMaterial(sec.getString(key + ".icon", "GHAST_TEAR"));
            if (mat == null) mat = Material.GHAST_TEAR;

            String name = sec.getString(key + ".display-name", "&fПлевок");
            List<String> desc = sec.getStringList(key + ".description");
            String sound = sec.getString(key + ".sound", "ENTITY_LLAMA_SPIT");
            float pitch = (float) sec.getDouble(key + ".pitch", 1.0);
            List<String> particles = sec.getStringList(key + ".particles");

            styles.put(key.toUpperCase(), new SpitStyle(
                    key.toUpperCase(), page, slot, defUnlocked, perm, mat, name, desc, sound, pitch, particles
            ));
        }
    }

    public boolean hasStyleUnlocked(Player player, SpitStyle style) {
        if (style.isDefaultUnlocked() || style.getPermission() == null || style.getPermission().isEmpty()) {
            return true;
        }
        return player.hasPermission(style.getPermission());
    }

    public SpitStyle getActiveStyleFor(Player player) {
        String id = extension.getPlayerStorage().getSelectedSpitId(player.getName());
        SpitStyle style = styles.get(id.toUpperCase());
        if (style != null && hasStyleUnlocked(player, style)) {
            return style;
        }
        return styles.values().stream().findFirst().orElse(null);
    }

    public void performSpit(Player player) {
        FileConfiguration cfg = extension.getCustomConfig("spits.yml").get();
        int burstShots = cfg.getInt("mechanics.burst-shots-before-cooldown", 4);
        int cdSeconds = cfg.getInt("mechanics.cooldown-seconds", 15);

        int remCd = extension.getCooldownTracker().tryUse(player.getUniqueId(), "SPIT", burstShots, cdSeconds);
        if (remCd > 0) {
            ChatUtil.sendMessage(player, cfg.getString("messages.cooldown"), "{time}", remCd);
            return;
        }

        SpitStyle style = getActiveStyleFor(player);
        if (style == null) return;

        double radius = cfg.getDouble("mechanics.sound-radius", 15.0);
        SoundParticleUtil.playSoundInRadius(player.getLocation(), style.getSound(), radius, 1.4f, style.getPitch());

        double speed = cfg.getDouble("mechanics.initial-speed", 1.35);
        int straightTicks = cfg.getInt("mechanics.straight-flight-ticks", 7);
        double gravityDrop = cfg.getDouble("mechanics.gravity-drop-per-tick", 0.045);
        int maxFlightTicks = cfg.getInt("mechanics.max-flight-ticks", 60);

        Vector direction = player.getEyeLocation().getDirection().normalize().multiply(speed);
        LlamaSpit spitEntity = player.launchProjectile(LlamaSpit.class, direction);
        spitEntity.setMetadata(META_SPIT_SHOOTER, new FixedMetadataValue(extension.getCore(), player.getName()));
        spitEntity.setMetadata(META_SPIT_STYLE, new FixedMetadataValue(extension.getCore(), style.getId()));

        ChatUtil.sendMessage(player, cfg.getString("messages.spit-launch"));

        // Контроллер полета: сначала летит прямо, затем плавно снижается + спавнит все частицы стиля!
        new BukkitRunnable() {
            int ticks = 0;
            Location lastLoc = spitEntity.getLocation();

            @Override
            public void run() {
                ticks++;
                if (!spitEntity.isValid() || spitEntity.isDead() || ticks > maxFlightTicks) {
                    spawnGroundPersistenceParticles(lastLoc, style);
                    if (!spitEntity.isDead()) spitEntity.remove();
                    cancel();
                    return;
                }

                lastLoc = spitEntity.getLocation();
                if (ticks <= straightTicks) {
                    spitEntity.setVelocity(direction);
                } else {
                    Vector v = spitEntity.getVelocity();
                    v.setY(v.getY() - gravityDrop);
                    spitEntity.setVelocity(v);
                }

                for (String pName : style.getParticles()) {
                    SoundParticleUtil.spawnParticles(lastLoc.clone().subtract(0, 0.8, 0), pName, 2, 0.08, 0.08, 0.08, 0.01);
                }
            }
        }.runTaskTimer(extension.getCore(), 1L, 1L);
    }

    /**
     * Эффекты плевка остаются некоторое время лежать на земле в месте падения
     */
    public void spawnGroundPersistenceParticles(Location hitLocation, SpitStyle style) {
        if (hitLocation == null || style == null) return;
        int groundTicks = extension.getCustomConfig("spits.yml").get().getInt("mechanics.ground-effect-ticks", 60);
        if (groundTicks <= 0) return;

        new BukkitRunnable() {
            int elapsed = 0;

            @Override
            public void run() {
                if (elapsed >= groundTicks) {
                    cancel();
                    return;
                }
                elapsed += 5;
                for (String pName : style.getParticles()) {
                    SoundParticleUtil.spawnParticles(hitLocation.clone().subtract(0, 0.7, 0), pName, 4, 0.25, 0.05, 0.25, 0.02);
                }
            }
        }.runTaskTimer(extension.getCore(), 1L, 5L);
    }

    public void openSpitListGui(Player player, int page) {
        FileConfiguration cfg = extension.getCustomConfig("spits.yml").get();
        int safePage = Math.max(1, Math.min(page, maxPage));
        String title = ChatUtil.color(cfg.getString("gui.title", "&8Выбор эффекта плевка"));

        Inventory gui = Bukkit.createInventory(new SpitMenuHolder(safePage), cfg.getInt("gui.size", 27), title);
        SpitStyle active = getActiveStyleFor(player);

        for (SpitStyle style : styles.values()) {
            if (style.getPage() != safePage) continue;

            boolean isSelected = (active != null && active.getId().equalsIgnoreCase(style.getId()));
            boolean isUnlocked = hasStyleUnlocked(player, style);

            List<String> lore = new ArrayList<>(style.getDescription());
            lore.add("");
            if (isSelected) {
                lore.add(cfg.getString("gui.status-lore.selected", "&a✔ Выбрано"));
            } else if (isUnlocked) {
                lore.add(cfg.getString("gui.status-lore.unlocked", "&eНажмите, чтобы выбрать!"));
            } else {
                lore.add(cfg.getString("gui.status-lore.locked", "&c✖ Заблокировано (Выбивается из кейсов)"));
            }

            // Выбранный эффект зачарован (светится)!
            gui.setItem(style.getSlot(), new ItemBuilder(style.getIcon())
                    .setName(style.getDisplayName())
                    .setLore(lore)
                    .setGlowing(isSelected)
                    .build());
        }

        // Если страница всего ОДНА — не рисуем никакого лишнего интерфейса переключения страниц!
        if (maxPage > 1) {
            if (safePage > 1) {
                gui.setItem(cfg.getInt("gui.prev-page-slot", 18), new ItemBuilder(Material.ARROW)
                        .setName(cfg.getString("gui.prev-page-name", "&e← Предыдущая страница"))
                        .build());
            }
            if (safePage < maxPage) {
                gui.setItem(cfg.getInt("gui.next-page-slot", 26), new ItemBuilder(Material.ARROW)
                        .setName(cfg.getString("gui.next-page-name", "&eСледующая страница →"))
                        .build());
            }
        }

        player.openInventory(gui);
    }

    public SpitStyle getStyleByPageAndSlot(int page, int slot) {
        for (SpitStyle style : styles.values()) {
            if (style.getPage() == page && style.getSlot() == slot) {
                return style;
            }
        }
        return null;
    }

    public SpitStyle getStyleById(String id) {
        return id != null ? styles.get(id.toUpperCase()) : null;
    }
}