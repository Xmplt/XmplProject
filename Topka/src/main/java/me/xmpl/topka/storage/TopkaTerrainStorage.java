package me.xmpl.topka.storage;

import me.xmpl.lib.config.CustomConfig;
import me.xmpl.lib.util.CuboidBox;
import me.xmpl.topka.TopkaExtension;
import me.xmpl.topka.model.TerrainBlockEntry;
import me.xmpl.topka.model.TerrainOption;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TopkaTerrainStorage {
    private final TopkaExtension extension;
    private final Map<String, TerrainOption> terrains = new LinkedHashMap<>();

    public TopkaTerrainStorage(TopkaExtension extension) {
        this.extension = extension;
        reload();
    }

    public void reload() {
        terrains.clear();
        FileConfiguration cfg = extension.getCustomConfig("terrains.yml").get();
        ConfigurationSection sec = cfg.getConfigurationSection("terrains");
        if (sec == null) return;

        FileConfiguration voteCfg = extension.getCustomConfig("voting.yml").get();
        List<String> defaultLore = voteCfg.getStringList("default-terrain-template.lore");

        for (String id : sec.getKeys(false)) {
            boolean enabled = sec.getBoolean(id + ".enabled", true);
            String displayName = sec.getString(id + ".display-name", "&f" + id);
            Material iconMat = Material.matchMaterial(sec.getString(id + ".icon-material", "GRASS"));
            if (iconMat == null) iconMat = Material.GRASS;
            short iconData = (short) sec.getInt(id + ".icon-data", 0);
            List<String> lore = sec.contains(id + ".lore") ? sec.getStringList(id + ".lore") : defaultLore;

            String worldName = sec.getString(id + ".origin.world", "world");
            int ox = sec.getInt(id + ".origin.x");
            int oy = sec.getInt(id + ".origin.y");
            int oz = sec.getInt(id + ".origin.z");

            terrains.put(id.toLowerCase(), new TerrainOption(
                    id.toLowerCase(), enabled, displayName, iconMat, iconData, lore, worldName, ox, oy, oz
            ));
        }
    }

    /**
     * Сохраняет выделенную WorldEdit территорию в конфиг terrains.yml и файл блоков terrains/<id>.yml
     */
    @SuppressWarnings("deprecation")
    public TerrainOption saveNewTerrainFromSelection(String rawDisplayName, CuboidBox selection) {
        String stripped = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', rawDisplayName))
                .replaceAll("[^a-zA-Z0-9а-яА-Я_-]", "_")
                .toLowerCase();
        if (stripped.isEmpty()) {
            stripped = "terra_" + (terrains.size() + 1);
        }
        String id = stripped;
        int suffix = 2;
        while (terrains.containsKey(id)) {
            id = stripped + "_" + suffix++;
        }

        World world = Bukkit.getWorld(selection.getWorldName());
        if (world == null) return null;

        List<String> serializedBlocks = new ArrayList<>();
        int minX = selection.getMinX();
        int minY = selection.getMinY();
        int minZ = selection.getMinZ();

        for (int x = minX; x <= selection.getMaxX(); x++) {
            for (int y = minY; y <= selection.getMaxY(); y++) {
                for (int z = minZ; z <= selection.getMaxZ(); z++) {
                    Block b = world.getBlockAt(x, y, z);
                    int dx = x - minX;
                    int dy = y - minY;
                    int dz = z - minZ;
                    serializedBlocks.add(dx + ";" + dy + ";" + dz + ";" + b.getType().name() + ";" + b.getData());
                }
            }
        }

        // Сохраняем слепок блоков в отдельный файл terrains/<id>.yml
        CustomConfig blockDataCfg = extension.getCustomConfig("terrains/" + id + ".yml");
        blockDataCfg.get().set("blocks", serializedBlocks);
        blockDataCfg.save();

        // Регистрируем территорию в terrains.yml с настройками иконки, лора и enabled: true
        FileConfiguration voteCfg = extension.getCustomConfig("voting.yml").get();
        String defIcon = voteCfg.getString("default-terrain-template.icon-material", "GRASS");
        int defData = voteCfg.getInt("default-terrain-template.icon-data", 0);
        List<String> defLore = voteCfg.getStringList("default-terrain-template.lore");

        CustomConfig terrainsCfg = extension.getCustomConfig("terrains.yml");
        FileConfiguration tCfg = terrainsCfg.get();
        String path = "terrains." + id;
        tCfg.set(path + ".enabled", true);
        tCfg.set(path + ".display-name", rawDisplayName);
        tCfg.set(path + ".icon-material", defIcon);
        tCfg.set(path + ".icon-data", defData);
        tCfg.set(path + ".lore", defLore);
        tCfg.set(path + ".origin.world", world.getName());
        tCfg.set(path + ".origin.x", minX);
        tCfg.set(path + ".origin.y", minY);
        tCfg.set(path + ".origin.z", minZ);
        terrainsCfg.save();

        reload();
        return terrains.get(id);
    }

    /**
     * Заменяет текущую территорию на выбранную (с сохранением рабочих сундуков TOPKA и их лута).
     */
    @SuppressWarnings("deprecation")
    public boolean pasteTerrain(String id) {
        TerrainOption option = terrains.get(id.toLowerCase());
        if (option == null) return false;

        World world = Bukkit.getWorld(option.getWorldName());
        if (world == null) return false;

        CustomConfig blockDataCfg = extension.getCustomConfig("terrains/" + option.getId() + ".yml");
        List<String> rawBlocks = blockDataCfg.get().getStringList("blocks");
        if (rawBlocks == null || rawBlocks.isEmpty()) return false;

        // Сохраняем содержимое рабочих сундуков TOPKA перед заменой блоков
        Location c1Loc = extension.getArenaStorage().getChest1Location();
        Location c2Loc = extension.getArenaStorage().getChest2Location();
        ItemStack[] savedChest1 = backupChestContents(c1Loc);
        ItemStack[] savedChest2 = backupChestContents(c2Loc);

        int ox = option.getOriginX();
        int oy = option.getOriginY();
        int oz = option.getOriginZ();

        List<TerrainBlockEntry> entries = new ArrayList<>(rawBlocks.size());
        for (String line : rawBlocks) {
            String[] parts = line.split(";");
            if (parts.length < 5) continue;
            try {
                int dx = Integer.parseInt(parts[0]);
                int dy = Integer.parseInt(parts[1]);
                int dz = Integer.parseInt(parts[2]);
                Material mat = Material.matchMaterial(parts[3]);
                byte data = Byte.parseByte(parts[4]);
                if (mat != null) {
                    entries.add(new TerrainBlockEntry(dx, dy, dz, mat, data));
                }
            } catch (Exception ignored) {}
        }

        for (TerrainBlockEntry entry : entries) {
            Block b = world.getBlockAt(ox + entry.dx, oy + entry.dy, oz + entry.dz);
            // Не затираем изумрудный блок голосования
            if (extension.getArenaStorage().isVoteEmeraldBlock(b.getLocation())) continue;
            b.setType(entry.material, false);
            b.setData(entry.data, false);
        }

        // Гарантируем, что сундуки TOPKA и их лут остаются на месте
        restoreChestContents(c1Loc, savedChest1);
        restoreChestContents(c2Loc, savedChest2);

        extension.getLifecycleService().getState().activeTerrainId = option.getId();
        extension.getStateStorage().saveStateAsync(extension.getLifecycleService().getState());
        return true;
    }

    private ItemStack[] backupChestContents(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        Block b = loc.getBlock();
        if (b.getState() instanceof Chest) {
            ItemStack[] src = ((Chest) b.getState()).getBlockInventory().getContents();
            ItemStack[] copy = new ItemStack[src.length];
            for (int i = 0; i < src.length; i++) {
                if (src[i] != null) copy[i] = src[i].clone();
            }
            return copy;
        }
        return null;
    }

    private void restoreChestContents(Location loc, ItemStack[] contents) {
        if (loc == null || loc.getWorld() == null) return;
        Block b = loc.getBlock();
        if (b.getType() != Material.CHEST && b.getType() != Material.TRAPPED_CHEST) {
            b.setType(Material.CHEST);
        }
        if (contents != null && b.getState() instanceof Chest) {
            ((Chest) b.getState()).getBlockInventory().setContents(contents);
        }
    }

    public List<TerrainOption> getEnabledTerrains() {
        List<TerrainOption> list = new ArrayList<>();
        for (TerrainOption opt : terrains.values()) {
            if (opt.isEnabled()) {
                list.add(opt);
            }
        }
        return list;
    }

    public TerrainOption getTerrain(String id) {
        return id != null ? terrains.get(id.toLowerCase()) : null;
    }

    public Collection<TerrainOption> getAllTerrains() {
        return Collections.unmodifiableCollection(terrains.values());
    }
}