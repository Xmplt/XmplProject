package me.xmpl.killsystem.service;

import me.xmpl.killsystem.KillSystemExtension;
import me.xmpl.killsystem.model.PlayerKillRecord;
import me.xmpl.lib.util.ChatUtil;
import me.xmpl.lib.util.SkullSkinUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class TopHopperGuiService {
    public static class TopGuiHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private final KillSystemExtension extension;

    public TopHopperGuiService(KillSystemExtension extension) {
        this.extension = extension;
    }

    public void openTopGui(Player viewer) {
        FileConfiguration guiCfg = extension.getCustomConfig("gui-hopper.yml").get();
        if (!guiCfg.getBoolean("enabled", true)) return;

        String title = ChatUtil.color(guiCfg.getString("title", "&8Топ лучших убийц"));
        String typeStr = guiCfg.getString("inventory-type", "HOPPER").toUpperCase();

        Inventory gui;
        int maxPlaces;
        if (typeStr.equals("ROW_9")) {
            gui = Bukkit.createInventory(new TopGuiHolder(), 9, title);
            maxPlaces = 6; // Если выбран ROW_9, показываем ровно 6 мест
        } else {
            // Классическая воронка Minecraft (5 слотов: с 1 по 5 место)
            gui = Bukkit.createInventory(new TopGuiHolder(), InventoryType.HOPPER, title);
            maxPlaces = 5;
        }

        List<PlayerKillRecord> sorted = extension.getDatabaseStorage().getSortedTopList();

        for (int i = 0; i < maxPlaces; i++) {
            int rank = i + 1;
            if (i < sorted.size()) {
                PlayerKillRecord rec = sorted.get(i);
                gui.setItem(i, buildPlayerHeadItem(guiCfg, rank, rec));
            } else {
                gui.setItem(i, buildEmptyHeadItem(guiCfg, rank));
            }
        }

        viewer.openInventory(gui);
    }

    private ItemStack buildPlayerHeadItem(FileConfiguration guiCfg, int rank, PlayerKillRecord rec) {
        String name = ChatUtil.applyReplacements(guiCfg.getString("head-item.display-name", "&6&l#{rank} &0{player}"),
                "{rank}", rank, "{player}", rec.getPlayerName(), "{kills}", rec.getKills(), "{deaths}", rec.getDeaths());

        List<String> lore = new ArrayList<>();
        for (String line : guiCfg.getStringList("head-item.lore")) {
            lore.add(ChatUtil.applyReplacements(line,
                    "{rank}", rank, "{player}", rec.getPlayerName(), "{kills}", rec.getKills(), "{deaths}", rec.getDeaths()));
        }

        Player onlineTarget = Bukkit.getPlayerExact(rec.getPlayerName());
        if (onlineTarget != null && onlineTarget.isOnline()) {
            return SkullSkinUtil.createPlayerHead(onlineTarget, name, lore);
        }
        return SkullSkinUtil.createOfflineHead(rec.getPlayerName(), name, lore);
    }

    private ItemStack buildEmptyHeadItem(FileConfiguration guiCfg, int rank) {
        String name = ChatUtil.applyReplacements(guiCfg.getString("empty-slot-item.display-name", "&7#{rank} Пусто"),
                "{rank}", rank);
        List<String> lore = new ArrayList<>(guiCfg.getStringList("empty-slot-item.lore"));
        return SkullSkinUtil.createOfflineHead("MHF_Question", name, lore);
    }
}