package me.xmpl.mine.service;

import me.xmpl.mine.MineSystemExtension;
import me.xmpl.mine.model.MineOreType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class OreRegenService {
    private final MineSystemExtension extension;
    private final Map<Location, Material> regeneratingBlocks = new ConcurrentHashMap<>();

    public OreRegenService(MineSystemExtension extension) {
        this.extension = extension;
    }

    public boolean isRegenerating(Location loc) {
        return regeneratingBlocks.containsKey(loc);
    }

    @SuppressWarnings("deprecation")
    public void startRegenCycle(Block block, MineOreType oreType) {
        Location loc = block.getLocation();
        Material targetOreMaterial = oreType.getDefaultBlockMaterial();
        regeneratingBlocks.put(loc, targetOreMaterial);

        FileConfiguration cfg = extension.getConfig();
        long redTicks = cfg.getLong("regeneration.red-glass-ticks", 40L);
        long yellowTicks = cfg.getLong("regeneration.yellow-glass-ticks", 40L);
        long greenTicks = cfg.getLong("regeneration.green-glass-ticks", 40L);

        byte redData = (byte) cfg.getInt("regeneration.red-glass-data", 14);
        byte yellowData = (byte) cfg.getInt("regeneration.yellow-glass-data", 4);
        byte greenData = (byte) cfg.getInt("regeneration.green-glass-data", 5);

        // Фаза 1: Сразу ставим КРАСНОЕ стекло
        block.setType(Material.STAINED_GLASS);
        block.setData(redData);

        // Фаза 2: Переход в ЖЕЛТОЕ стекло
        Bukkit.getScheduler().runTaskLater(extension.getCore(), () -> {
            if (!regeneratingBlocks.containsKey(loc)) return;
            Block b = loc.getBlock();
            b.setType(Material.STAINED_GLASS);
            b.setData(yellowData);
        }, redTicks);

        // Фаза 3: Переход в ЗЕЛЕНОЕ стекло
        Bukkit.getScheduler().runTaskLater(extension.getCore(), () -> {
            if (!regeneratingBlocks.containsKey(loc)) return;
            Block b = loc.getBlock();
            b.setType(Material.STAINED_GLASS);
            b.setData(greenData);
        }, redTicks + yellowTicks);

        // Фаза 4: Возвращаем ИСХОДНУЮ РУДУ
        Bukkit.getScheduler().runTaskLater(extension.getCore(), () -> {
            Material restoreMat = regeneratingBlocks.remove(loc);
            if (restoreMat != null) {
                Block b = loc.getBlock();
                b.setType(restoreMat);
            }
        }, redTicks + yellowTicks + greenTicks);
    }

    /**
     * Мгновенно возвращает всю руду на место при рестарте или отключении плагина
     */
    public void forceRestoreAllBlocks() {
        for (Map.Entry<Location, Material> entry : regeneratingBlocks.entrySet()) {
            entry.getKey().getBlock().setType(entry.getValue());
        }
        regeneratingBlocks.clear();
    }
}