package me.xmpl.airdrop.storage;

import me.xmpl.airdrop.AirDropExtension;
import me.xmpl.airdrop.model.AirDropLootItem;
import me.xmpl.lib.config.CustomConfig;
import me.xmpl.lib.util.ChatUtil;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class AirDropLootStorage {
    private final AirDropExtension extension;
    private final List<AirDropLootItem> items = new ArrayList<>();

    public AirDropLootStorage(AirDropExtension extension) {
        this.extension = extension;
        reload();
    }

    public void reload() {
        items.clear();
        FileConfiguration cfg = extension.getCustomConfig("loot-items.yml").get();
        ConfigurationSection sec = cfg.getConfigurationSection("items");
        if (sec == null) return;

        for (String key : sec.getKeys(false)) {
            ItemStack stack = sec.getItemStack(key + ".item");
            double chance = sec.getDouble(key + ".chance", 50.0);
            if (stack != null && stack.getType() != Material.AIR) {
                items.add(new AirDropLootItem(key, applySignatureLore(stack.clone()), chance));
            }
        }
    }

    public void saveAllToDisk() {
        CustomConfig cc = extension.getCustomConfig("loot-items.yml");
        FileConfiguration cfg = cc.get();
        cfg.set("items", null);

        for (int i = 0; i < items.size(); i++) {
            AirDropLootItem entry = items.get(i);
            ItemStack cleanStamped = applySignatureLore(entry.getItemStack().clone());
            entry.setItemStack(cleanStamped);
            cfg.set("items.item_" + i + ".item", cleanStamped);
            cfg.set("items.item_" + i + ".chance", entry.getChance());
        }
        cc.save();
    }

    /**
     * Полностью очищает любой старый лор предмета и устанавливает:
     * "&6Получено из сундука по координатам"
     */
    public ItemStack applySignatureLore(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return item;
        if (!extension.getConfig().getBoolean("settings.replace-item-lore", true)) {
            return item;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> rawLore = extension.getConfig().getStringList("settings.custom-item-lore");
            if (rawLore == null || rawLore.isEmpty()) {
                rawLore = Collections.singletonList("&6Получено из сундука по координатам");
            }
            meta.setLore(ChatUtil.color(rawLore));
            item.setItemMeta(meta);
        }
        return item;
    }

    public List<AirDropLootItem> getItems() {
        return items;
    }

    public ItemStack pickRandomLootItem() {
        if (items.isEmpty()) return null;
        List<AirDropLootItem> shuffled = new ArrayList<>(items);
        Collections.shuffle(shuffled);

        for (AirDropLootItem candidate : shuffled) {
            if (ThreadLocalRandom.current().nextDouble(0.0, 100.0) <= candidate.getChance()) {
                return applySignatureLore(candidate.getItemStack().clone());
            }
        }
        return applySignatureLore(shuffled.get(0).getItemStack().clone());
    }
}