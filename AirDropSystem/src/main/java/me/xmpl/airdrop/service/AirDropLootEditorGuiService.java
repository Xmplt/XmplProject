package me.xmpl.airdrop.service;

import me.xmpl.airdrop.AirDropExtension;
import me.xmpl.airdrop.model.AirDropLootItem;
import me.xmpl.lib.util.ChatUtil;
import me.xmpl.lib.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class AirDropLootEditorGuiService {
    public static class StoragePageHolder implements InventoryHolder {
        public final int page;
        public StoragePageHolder(int page) { this.page = page; }
        @Override public Inventory getInventory() { return null; }
    }

    public static class ChanceEditorHolder implements InventoryHolder {
        public final int returnPage;
        public final int itemGlobalIndex;
        public ChanceEditorHolder(int returnPage, int itemGlobalIndex) {
            this.returnPage = returnPage;
            this.itemGlobalIndex = itemGlobalIndex;
        }
        @Override public Inventory getInventory() { return null; }
    }

    private final AirDropExtension extension;

    public AirDropLootEditorGuiService(AirDropExtension extension) {
        this.extension = extension;
    }

    public void openStoragePage(Player admin, int page) {
        FileConfiguration guiCfg = extension.getCustomConfig("gui-editor.yml").get();
        int safePage = Math.max(1, page);
        String title = ChatUtil.color(guiCfg.getString("storage-gui.title", "&8Лут АирДропа (Стр. {page})")
                .replace("{page}", String.valueOf(safePage)));

        Inventory gui = Bukkit.createInventory(new StoragePageHolder(safePage), 54, title);
        List<AirDropLootItem> allItems = extension.getLootStorage().getItems();

        int startIdx = (safePage - 1) * 45;
        for (int slot = 0; slot < 45; slot++) {
            int globalIdx = startIdx + slot;
            if (globalIdx >= allItems.size()) break;

            AirDropLootItem entry = allItems.get(globalIdx);
            ItemStack displayStack = entry.getItemStack().clone();
            ItemMeta meta = displayStack.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                for (String l : guiCfg.getStringList("storage-gui.item-lore-preview")) {
                    lore.add(ChatUtil.color(l.replace("{chance}", String.valueOf(entry.getChance()))));
                }
                meta.setLore(lore);
                displayStack.setItemMeta(meta);
            }
            gui.setItem(slot, displayStack);
        }

        // Нижняя панель навигации (слоты 45..53)
        ItemStack glass = new ItemBuilder(Material.STAINED_GLASS_PANE, 1, (short) 15).setName("&7").build();
        for (int s = 45; s < 54; s++) gui.setItem(s, glass);

        if (safePage > 1) {
            gui.setItem(guiCfg.getInt("storage-gui.prev-page-slot", 45), new ItemBuilder(Material.ARROW)
                    .setName(guiCfg.getString("storage-gui.prev-page-name", "&e← Предыдущая страница"))
                    .build());
        }

        gui.setItem(guiCfg.getInt("storage-gui.info-slot", 49), new ItemBuilder(Material.NETHER_STAR)
                .setName(guiCfg.getString("storage-gui.info-name", "&a&lУправление лутом"))
                .setLore(guiCfg.getStringList("storage-gui.info-lore"))
                .build());

        gui.setItem(guiCfg.getInt("storage-gui.next-page-slot", 53), new ItemBuilder(Material.ARROW)
                .setName(guiCfg.getString("storage-gui.next-page-name", "&eСледующая страница →"))
                .build());

        admin.openInventory(gui);
    }

    /**
     * Сохраняет изменения предметов на текущей странице (слоты 0..44), очищая временный превью-лор шанса
     */
    public void saveStoragePageContents(Inventory gui, int page) {
        List<AirDropLootItem> allItems = extension.getLootStorage().getItems();
        FileConfiguration guiCfg = extension.getCustomConfig("gui-editor.yml").get();
        int previewLinesCount = guiCfg.getStringList("storage-gui.item-lore-preview").size();

        int startIdx = (page - 1) * 45;
        List<AirDropLootItem> updatedPageItems = new ArrayList<>();

        for (int slot = 0; slot < 45; slot++) {
            ItemStack stack = gui.getItem(slot);
            if (stack == null || stack.getType() == Material.AIR) continue;

            int oldGlobalIdx = startIdx + slot;
            double existingChance = (oldGlobalIdx < allItems.size()) ? allItems.get(oldGlobalIdx).getChance() : 50.0;

            ItemStack cleanClone = stack.clone();
            ItemMeta meta = cleanClone.getItemMeta();
            if (meta != null && meta.hasLore()) {
                List<String> lore = new ArrayList<>(meta.getLore());
                // Убираем превью-строки редактора, если они были добавлены при открытии
                if (lore.size() >= previewLinesCount && lore.get(lore.size() - 1).contains("ПКМ")) {
                    for (int k = 0; k < previewLinesCount; k++) {
                        lore.remove(lore.size() - 1);
                    }
                    meta.setLore(lore.isEmpty() ? null : lore);
                    cleanClone.setItemMeta(meta);
                }
            }
            updatedPageItems.add(new AirDropLootItem("item_" + slot, cleanClone, existingChance));
        }

        // Обновляем срез списка для данной страницы
        while (allItems.size() > startIdx && allItems.size() > startIdx) {
            if (startIdx < allItems.size()) {
                int removeCount = Math.min(45, allItems.size() - startIdx);
                for (int r = 0; r < removeCount; r++) {
                    allItems.remove(startIdx);
                }
            }
            break;
        }

        allItems.addAll(Math.min(startIdx, allItems.size()), updatedPageItems);
        extension.getLootStorage().saveAllToDisk();
    }

    public void openChanceEditor(Player admin, int returnPage, int globalIndex) {
        List<AirDropLootItem> allItems = extension.getLootStorage().getItems();
        if (globalIndex < 0 || globalIndex >= allItems.size()) return;

        AirDropLootItem target = allItems.get(globalIndex);
        FileConfiguration guiCfg = extension.getCustomConfig("gui-editor.yml").get();
        String title = ChatUtil.color(guiCfg.getString("chance-gui.title", "&8Настройка шанса предмета"));

        Inventory gui = Bukkit.createInventory(new ChanceEditorHolder(returnPage, globalIndex), 9, title);

        // По центру предмет с текущим шансом
        ItemStack center = target.getItemStack().clone();
        ItemMeta meta = center.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add("");
            lore.add(ChatUtil.color("&6Текущий шанс: &e&l" + target.getChance() + "%"));
            meta.setLore(lore);
            center.setItemMeta(meta);
        }
        gui.setItem(guiCfg.getInt("chance-gui.center-item-slot", 4), center);

        // По бокам стекла с цифрами: -25, -10, -1 и +1, +10, +25
        placeGlassBtn(gui, guiCfg, "minus-25", 0);
        placeGlassBtn(gui, guiCfg, "minus-10", 1);
        placeGlassBtn(gui, guiCfg, "minus-1", 2);

        gui.setItem(guiCfg.getInt("chance-gui.buttons.back-save.slot", 3), new ItemBuilder(Material.ARROW)
                .setName(guiCfg.getString("chance-gui.buttons.back-save.name", "&e← Вернуться к списку лута"))
                .build());

        placeGlassBtn(gui, guiCfg, "plus-1", 5);
        placeGlassBtn(gui, guiCfg, "plus-10", 6);
        placeGlassBtn(gui, guiCfg, "plus-25", 7);

        admin.openInventory(gui);
    }

    private void placeGlassBtn(Inventory gui, FileConfiguration cfg, String key, int defSlot) {
        String path = "chance-gui.buttons." + key;
        int slot = cfg.getInt(path + ".slot", defSlot);
        short data = (short) cfg.getInt(path + ".glass-data", 5);
        String name = cfg.getString(path + ".name", key);
        gui.setItem(slot, new ItemBuilder(Material.STAINED_GLASS_PANE, 1, data).setName(name).build());
    }
}