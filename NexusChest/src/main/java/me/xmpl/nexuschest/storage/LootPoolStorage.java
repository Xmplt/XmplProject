package me.xmpl.nexuschest.storage;

import me.xmpl.lib.config.CustomConfig;
import me.xmpl.lib.util.ChatUtil;
import me.xmpl.nexuschest.NexusChestExtension;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LootPoolStorage {
    public static class LootEditorHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private final NexusChestExtension extension;
    private final List<ItemStack> poolItems = new ArrayList<>();

    public LootPoolStorage(NexusChestExtension extension) {
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
                poolItems.add(item.clone());
            }
        }
    }

    public void openLootEditorGui(Player admin) {
        String title = ChatUtil.color(extension.getCustomConfig("commands/nchest.yml").get()
                .getString("messages.loot-editor-title", "&8Пул предметов NexusChest"));
        Inventory gui = Bukkit.createInventory(new LootEditorHolder(), 54, title);

        for (int i = 0; i < Math.min(54, poolItems.size()); i++) {
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
        for (int i = 0; i < gui.getSize(); i++) {
            ItemStack item = gui.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                poolItems.add(item.clone());
                cfg.set("items.slot_" + idx, item.clone());
                idx++;
            }
        }
        cc.save();
        return poolItems.size();
    }

    public List<ItemStack> getPoolItems() {
        return Collections.unmodifiableList(poolItems);
    }
}