package me.xmpl.topka.storage;

import me.xmpl.lib.config.CustomConfig;
import me.xmpl.lib.util.ChatUtil;
import me.xmpl.topka.TopkaExtension;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TopkaLootPoolStorage {
    public static class TopkaLootEditorHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private final TopkaExtension extension;
    private final List<ItemStack> poolItems = new ArrayList<>();

    public TopkaLootPoolStorage(TopkaExtension extension) {
        this.extension = extension;
        reload();
    }

    public void reload() {
        poolItems.clear();
        FileConfiguration cfg = extension.getCustomConfig("loot-pool.yml").get();
        ConfigurationSection sec = cfg.getConfigurationSection("items");
        if (sec == null) return;

        for (String key : sec.getKeys(false)) {
            ItemStack item = sec.getItemStack(key);
            if (item != null && item.getType() != Material.AIR) {
                poolItems.add(applyTopkaLore(item.clone()));
            }
        }
    }

    public void openLootEditorGui(Player admin) {
        String title = ChatUtil.color(extension.getCustomConfig("messages.yml").get()
                .getString("messages.loot-editor-title", "&8Пул предметов TOPKA (27 слотов)"));
        Inventory gui = Bukkit.createInventory(new TopkaLootEditorHolder(), 27, title);

        for (int i = 0; i < Math.min(27, poolItems.size()); i++) {
            gui.setItem(i, poolItems.get(i).clone());
        }
        admin.openInventory(gui);
    }

    public int saveFromEditorInventory(Inventory gui) {
        poolItems.clear();
        CustomConfig cc = extension.getCustomConfig("loot-pool.yml");
        FileConfiguration cfg = cc.get();
        cfg.set("items", null);

        int idx = 0;
        for (int i = 0; i < Math.min(27, gui.getSize()); i++) {
            ItemStack raw = gui.getItem(i);
            if (raw != null && raw.getType() != Material.AIR) {
                ItemStack stamped = applyTopkaLore(raw.clone());
                poolItems.add(stamped);
                cfg.set("items.slot_" + idx, stamped.clone());
                idx++;
            }
        }
        cc.save();
        return poolItems.size();
    }

    public ItemStack applyTopkaLore(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return item;
        if (!extension.getConfig().getBoolean("settings.replace-item-lore", true)) {
            return item;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> rawLore = extension.getConfig().getStringList("settings.custom-item-lore");
            if (rawLore == null || rawLore.isEmpty()) {
                rawLore = Collections.singletonList("&fВещь &e&lTOPKA");
            }
            meta.setLore(ChatUtil.color(rawLore));
            item.setItemMeta(meta);
        }
        return item;
    }

    public List<ItemStack> getPoolItems() {
        return Collections.unmodifiableList(poolItems);
    }
}