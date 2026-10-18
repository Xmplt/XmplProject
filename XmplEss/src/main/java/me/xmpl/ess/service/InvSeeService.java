package me.xmpl.ess.service;

import me.xmpl.antirelog.AntiRelogExtension;
import me.xmpl.ess.XmplEssExtension;
import me.xmpl.lib.extension.XmplExtension;
import me.xmpl.lib.util.ChatUtil;
import me.xmpl.lib.util.ItemBuilder;
import me.xmpl.lib.util.OfflineInventoryUtil;
import me.xmpl.lib.util.OfflineInventoryUtil.OfflinePlayerData;
import me.xmpl.lib.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.*;

public class InvSeeService {
    private final XmplEssExtension extension;

    public static class InvSeeHolder implements InventoryHolder {
        public final UUID targetUuid;
        public final String targetName;
        public final boolean offlineMode;
        private Inventory inventory;

        public InvSeeHolder(UUID targetUuid, String targetName, boolean offlineMode) {
            this.targetUuid = targetUuid;
            this.targetName = targetName;
            this.offlineMode = offlineMode;
        }

        public void setInventory(Inventory inventory) { this.inventory = inventory; }
        @Override public Inventory getInventory() { return inventory; }
    }

    public InvSeeService(XmplEssExtension extension) {
        this.extension = extension;
    }

    public void startLiveSyncTask() {
        // Живое обновление здоровья, эффектов и статуса ПвП для всех открытых /invsee каждые 10 тиков
        extension.runTaskTimer(() -> {
            for (Player admin : Bukkit.getOnlinePlayers()) {
                Inventory top = admin.getOpenInventory().getTopInventory();
                if (top != null && top.getHolder() instanceof InvSeeHolder) {
                    InvSeeHolder holder = (InvSeeHolder) top.getHolder();
                    if (!holder.offlineMode) {
                        Player target = Bukkit.getPlayer(holder.targetUuid);
                        if (target != null && target.isOnline()) {
                            updateIndicatorsOnly(top, target);
                        }
                    }
                }
            }
        }, 10L, 10L);
    }

    public boolean openInvSee(Player viewer, String targetNameArg) {
        FileConfiguration cfg = extension.getCustomConfig("commands/invsee.yml").get();
        Player onlineTarget = Bukkit.getPlayerExact(targetNameArg);

        if (onlineTarget != null && onlineTarget.isOnline()) {
            String title = ChatUtil.color(cfg.getString("settings.gui-title", "&8Инвентарь: &0{player}")
                    .replace("{player}", onlineTarget.getName()));
            InvSeeHolder holder = new InvSeeHolder(onlineTarget.getUniqueId(), onlineTarget.getName(), false);
            Inventory gui = Bukkit.createInventory(holder, 54, title);
            holder.setInventory(gui);

            populateFromOnline(gui, onlineTarget);
            viewer.openInventory(gui);
            return true;
        }

        // Игрок НЕ в сети — проверяем админское право на оффлайн-просмотр
        String offlinePerm = cfg.getString("permissions.offline", "xmpless.invsee.offline");
        if (!viewer.hasPermission(offlinePerm)) {
            ChatUtil.sendMessage(viewer, cfg.getString("messages.no-offline-permission"));
            return false;
        }

        @SuppressWarnings("deprecation")
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(targetNameArg);
        if (offlinePlayer == null || !offlinePlayer.hasPlayedBefore()) {
            ChatUtil.sendMessage(viewer, cfg.getString("messages.player-not-found"), "{player}", targetNameArg);
            return false;
        }

        OfflinePlayerData data = OfflineInventoryUtil.loadOfflineData(offlinePlayer.getUniqueId());
        if (data == null) {
            ChatUtil.sendMessage(viewer, cfg.getString("messages.player-not-found"), "{player}", targetNameArg);
            return false;
        }

        String displayName = offlinePlayer.getName() != null ? offlinePlayer.getName() : targetNameArg;
        String title = ChatUtil.color(cfg.getString("settings.gui-title", "&8Инвентарь: &0{player}")
                .replace("{player}", displayName + " (Оффлайн)"));
        InvSeeHolder holder = new InvSeeHolder(offlinePlayer.getUniqueId(), displayName, true);
        Inventory gui = Bukkit.createInventory(holder, 54, title);
        holder.setInventory(gui);

        populateFromOffline(gui, data);
        viewer.openInventory(gui);
        return true;
    }

    public void populateFromOnline(Inventory gui, Player target) {
        ItemStack[] contents = target.getInventory().getStorageContents();
        for (int i = 0; i < 36; i++) {
            gui.setItem(i, contents[i]);
        }

        FileConfiguration cfg = extension.getCustomConfig("commands/invsee.yml").get();
        gui.setItem(cfg.getInt("settings.slots.boots", 45), target.getInventory().getBoots());
        gui.setItem(cfg.getInt("settings.slots.leggings", 46), target.getInventory().getLeggings());
        gui.setItem(cfg.getInt("settings.slots.chestplate", 47), target.getInventory().getChestplate());
        gui.setItem(cfg.getInt("settings.slots.helmet", 48), target.getInventory().getHelmet());
        gui.setItem(cfg.getInt("settings.slots.offhand", 49), target.getInventory().getItemInOffHand());

        fillDecorSlots(gui, cfg);
        updateIndicatorsOnly(gui, target);
    }

    private void populateFromOffline(Inventory gui, OfflinePlayerData data) {
        for (int i = 0; i < 36; i++) {
            gui.setItem(i, data.storage[i]);
        }

        FileConfiguration cfg = extension.getCustomConfig("commands/invsee.yml").get();
        gui.setItem(cfg.getInt("settings.slots.boots", 45), data.armor[0]);
        gui.setItem(cfg.getInt("settings.slots.leggings", 46), data.armor[1]);
        gui.setItem(cfg.getInt("settings.slots.chestplate", 47), data.armor[2]);
        gui.setItem(cfg.getInt("settings.slots.helmet", 48), data.armor[3]);
        gui.setItem(cfg.getInt("settings.slots.offhand", 49), data.offhand);

        fillDecorSlots(gui, cfg);

        // Здоровье оффлайн
        int healthSlot = cfg.getInt("settings.slots.health", 44);
        String hFormat = cfg.getString("items.health.format", "&7Текущее: &f{health} &7из &f{max} &c❤")
                .replace("{health}", String.format("%.1f", data.health))
                .replace("{max}", String.format("%.1f", data.maxHealth));
        gui.setItem(healthSlot, new ItemBuilder(Material.APPLE)
                .setName(cfg.getString("items.health.name", "&cЗдоровье"))
                .setLore(hFormat)
                .build());

        // Эффекты и ПвП для оффлайн-игрока
        gui.setItem(cfg.getInt("settings.slots.potion", 52), new ItemBuilder(Material.GLASS_BOTTLE)
                .setName(cfg.getString("items.no-effects.name", "&bЭффекты зелий"))
                .setLore(cfg.getStringList("items.no-effects.lore"))
                .build());

        short safeColor = (short) cfg.getInt("items.pvp-status.safe.color-id", 5);
        gui.setItem(cfg.getInt("settings.slots.pvp-status", 53), new ItemBuilder(Material.CONCRETE, 1, safeColor)
                .setName(cfg.getString("items.pvp-status.safe.name", "&a✔ Игрок не в PvP"))
                .build());
    }

    private void fillDecorSlots(Inventory gui, FileConfiguration cfg) {
        short glassColor = (short) cfg.getInt("items.filler-glass.color-id", 15);
        ItemStack filler = new ItemBuilder(Material.STAINED_GLASS_PANE, 1, glassColor)
                .setName(cfg.getString("items.filler-glass.name", "&7"))
                .build();
        for (int i = 36; i < 44; i++) gui.setItem(i, filler);
        gui.setItem(50, filler);
        gui.setItem(51, filler);
    }

    public void updateIndicatorsOnly(Inventory gui, Player target) {
        FileConfiguration cfg = extension.getCustomConfig("commands/invsee.yml").get();

        // 1. Слот Здоровья (44)
        double hp = target.getHealth();
        double maxHp = target.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        String hFormat = cfg.getString("items.health.format", "&7Текущее: &f{health} &7из &f{max} &c❤")
                .replace("{health}", String.format("%.1f", hp))
                .replace("{max}", String.format("%.1f", maxHp));
        gui.setItem(cfg.getInt("settings.slots.health", 44), new ItemBuilder(Material.APPLE)
                .setName(cfg.getString("items.health.name", "&cЗдоровье"))
                .setLore(hFormat)
                .build());

        // 2. Слот Эффектов зелий (52)
        Collection<PotionEffect> effects = target.getActivePotionEffects();
        int potionSlot = cfg.getInt("settings.slots.potion", 52);
        if (effects.isEmpty()) {
            gui.setItem(potionSlot, new ItemBuilder(Material.GLASS_BOTTLE)
                    .setName(cfg.getString("items.no-effects.name", "&bЭффекты зелий"))
                    .setLore(cfg.getStringList("items.no-effects.lore"))
                    .build());
        } else {
            List<String> lore = new ArrayList<>();
            String lineFmt = cfg.getString("items.has-effects.effect-line-format", "&f- &e{effect} &7({time})");
            for (PotionEffect eff : effects) {
                String effName = eff.getType().getName() + " " + (eff.getAmplifier() + 1);
                String timeStr = TimeUtil.formatDuration((eff.getDuration() / 20L) * 1000L);
                lore.add(lineFmt.replace("{effect}", effName).replace("{time}", timeStr));
            }
            gui.setItem(potionSlot, new ItemBuilder(Material.POTION)
                    .setName(cfg.getString("items.has-effects.name", "&bАктивные эффекты"))
                    .setLore(lore)
                    .build());
        }

        // 3. Слот ПвП статуса через наш кастомный AntiRelog (53)
        boolean inPvp = checkPlayerInAntiRelog(target);
        int pvpSlot = cfg.getInt("settings.slots.pvp-status", 53);
        if (inPvp) {
            short unsafeColor = (short) cfg.getInt("items.pvp-status.unsafe.color-id", 14);
            gui.setItem(pvpSlot, new ItemBuilder(Material.CONCRETE, 1, unsafeColor)
                    .setName(cfg.getString("items.pvp-status.unsafe.name", "&c⚔ Игрок в режиме PvP"))
                    .build());
        } else {
            short safeColor = (short) cfg.getInt("items.pvp-status.safe.color-id", 5);
            gui.setItem(pvpSlot, new ItemBuilder(Material.CONCRETE, 1, safeColor)
                    .setName(cfg.getString("items.pvp-status.safe.name", "&a✔ Игрок не в PvP"))
                    .build());
        }
    }

    private boolean checkPlayerInAntiRelog(Player target) {
        XmplExtension ext = extension.getCore().getExtensionManager().getExtension("AntiRelog");
        if (ext instanceof AntiRelogExtension && ext.isEnabled()) {
            return ((AntiRelogExtension) ext).getCombatManager().isInPvp(target);
        }
        return false;
    }

    public void applyChangesFromGuiToTarget(Inventory gui, InvSeeHolder holder) {
        FileConfiguration cfg = extension.getCustomConfig("commands/invsee.yml").get();
        int bootsSlot = cfg.getInt("settings.slots.boots", 45);
        int legsSlot = cfg.getInt("settings.slots.leggings", 46);
        int chestSlot = cfg.getInt("settings.slots.chestplate", 47);
        int helmSlot = cfg.getInt("settings.slots.helmet", 48);
        int offhandSlot = cfg.getInt("settings.slots.offhand", 49);

        Player onlineTarget = Bukkit.getPlayer(holder.targetUuid);
        if (onlineTarget != null && onlineTarget.isOnline()) {
            for (int i = 0; i < 36; i++) {
                onlineTarget.getInventory().setItem(i, gui.getItem(i));
            }
            onlineTarget.getInventory().setBoots(gui.getItem(bootsSlot));
            onlineTarget.getInventory().setLeggings(gui.getItem(legsSlot));
            onlineTarget.getInventory().setChestplate(gui.getItem(chestSlot));
            onlineTarget.getInventory().setHelmet(gui.getItem(helmSlot));
            onlineTarget.getInventory().setItemInOffHand(gui.getItem(offhandSlot));
            onlineTarget.updateInventory();
            return;
        }

        // Если цель оффлайн — сохраняем напрямую в .dat файл!
        OfflinePlayerData data = new OfflinePlayerData();
        for (int i = 0; i < 36; i++) {
            data.storage[i] = gui.getItem(i);
        }
        data.armor[0] = gui.getItem(bootsSlot);
        data.armor[1] = gui.getItem(legsSlot);
        data.armor[2] = gui.getItem(chestSlot);
        data.armor[3] = gui.getItem(helmSlot);
        data.offhand = gui.getItem(offhandSlot);

        OfflineInventoryUtil.saveOfflineData(holder.targetUuid, data);
    }

    public boolean isEditableItemSlot(int rawSlot) {
        FileConfiguration cfg = extension.getCustomConfig("commands/invsee.yml").get();
        if (rawSlot >= 0 && rawSlot < 36) return true;
        return rawSlot == cfg.getInt("settings.slots.boots", 45)
                || rawSlot == cfg.getInt("settings.slots.leggings", 46)
                || rawSlot == cfg.getInt("settings.slots.chestplate", 47)
                || rawSlot == cfg.getInt("settings.slots.helmet", 48)
                || rawSlot == cfg.getInt("settings.slots.offhand", 49);
    }
}