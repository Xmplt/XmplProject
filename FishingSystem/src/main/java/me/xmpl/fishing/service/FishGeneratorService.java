package me.xmpl.fishing.service;

import me.xmpl.fishing.FishingSystemExtension;
import me.xmpl.fishing.model.FishSpeciesConfig;
import me.xmpl.fishing.model.WeightTier;
import me.xmpl.lib.util.ChatUtil;
import me.xmpl.lib.util.ItemBuilder;
import me.xmpl.lib.util.ItemNbtUtil;
import me.xmpl.lib.util.RandomUtil;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class FishGeneratorService {
    public static final String NBT_WEIGHT = "xmpl_fish_weight";
    public static final String NBT_SPECIES = "xmpl_fish_type";
    public static final String NBT_MULTIPLIER = "xmpl_fish_mult";

    private final FishingSystemExtension extension;
    private final Map<Short, FishSpeciesConfig> speciesByData = new HashMap<>();
    private final Map<String, FishSpeciesConfig> speciesByKey = new HashMap<>();
    private final List<WeightTier> weightTiers = new ArrayList<>();

    public FishGeneratorService(FishingSystemExtension extension) {
        this.extension = extension;
        reload();
    }

    public void reload() {
        speciesByData.clear();
        speciesByKey.clear();
        weightTiers.clear();

        // 1. Загружаем виды рыбы из fish-species.yml
        FileConfiguration spCfg = extension.getCustomConfig("fish-species.yml").get();
        ConfigurationSection spSec = spCfg.getConfigurationSection("species");
        if (spSec != null) {
            for (String key : spSec.getKeys(false)) {
                short data = (short) spSec.getInt(key + ".data", 0);
                String name = spSec.getString(key + ".display-name", "&fРыба");
                double minW = spSec.getDouble(key + ".min-weight", 0.1);
                double maxW = spSec.getDouble(key + ".max-weight", 15.0);
                double price = spSec.getDouble(key + ".price-per-kg", 100.0);

                FishSpeciesConfig species = new FishSpeciesConfig(key.toUpperCase(), data, name, minW, maxW, price);
                speciesByData.put(data, species);
                speciesByKey.put(key.toUpperCase(), species);
            }
        }

        // 2. Загружаем тиры редкости и цвета веса из rarities.yml
        FileConfiguration rarCfg = extension.getCustomConfig("rarities.yml").get();
        ConfigurationSection tierSec = rarCfg.getConfigurationSection("weight-tiers");
        if (tierSec != null) {
            for (String key : tierSec.getKeys(false)) {
                weightTiers.add(new WeightTier(
                        key,
                        tierSec.getDouble(key + ".chance", 10.0),
                        tierSec.getDouble(key + ".min-percent", 0.0),
                        tierSec.getDouble(key + ".max-percent", 1.0),
                        tierSec.getString(key + ".color", "&e&l")
                ));
            }
        }
    }

    /**
     * Превращает пойманную ванильную рыбу в нашу кастомную рыбу с весом, цветом и NBT-тегами
     */
    @SuppressWarnings("deprecation")
    public ItemStack transformCaughtFish(Player angler, ItemStack caughtItem, boolean inBonusRegion) {
        if (caughtItem == null || caughtItem.getType() != Material.RAW_FISH) {
            return null;
        }

        short data = caughtItem.getDurability();
        FishSpeciesConfig species = speciesByData.getOrDefault(data, speciesByData.get((short) 0));
        if (species == null) return null;

        WeightTier selectedTier = rollWeightTier();
        double range = species.getMaxWeight() - species.getMinWeight();
        double minW = species.getMinWeight() + range * selectedTier.getMinPercent();
        double maxW = species.getMinWeight() + range * selectedTier.getMaxPercent();

        // Генерируем вес с точностью до 2 знаков после запятой (например 12.45 кг)
        double rawWeight = RandomUtil.nextDouble(minW, maxW);
        double weight = Math.max(species.getMinWeight(), Math.round(rawWeight * 100.0) / 100.0);

        String weightStr = String.format(Locale.US, "%.2f", weight);
        String weightColor = selectedTier.getColor();

        List<String> lore = new ArrayList<>();
        for (String line : extension.getConfig().getStringList("item-display.lore")) {
            lore.add(ChatUtil.applyReplacements(line,
                    "{weight_color}", weightColor,
                    "{weight}", weightStr));
        }

        double regionMult = inBonusRegion
                ? extension.getConfig().getDouble("settings.bonus-region-multiplier", 2.0)
                : 1.0;

        if (inBonusRegion) {
            String bonusLine = extension.getConfig().getString("item-display.bonus-region-lore-line", "");
            if (bonusLine != null && !bonusLine.isEmpty()) {
                lore.add(bonusLine);
            }
        }

        ItemStack result = new ItemBuilder(Material.RAW_FISH, 1, data)
                .setName(species.getDisplayName())
                .setLore(lore)
                .build();

        // Зашиваем защищенные NBT-теги внутрь рыбы
        result = ItemNbtUtil.setDouble(result, NBT_WEIGHT, weight);
        result = ItemNbtUtil.setString(result, NBT_SPECIES, species.getKey());
        result = ItemNbtUtil.setDouble(result, NBT_MULTIPLIER, regionMult);

        // Обновляем личный рекорд игрока
        extension.getStatsStorage().checkAndUpdateRecordWeight(angler.getName(), weight);

        // Если поймал ультра-редкий трофей (TROPHEY_GOD) — поздравляем в чате!
        if (selectedTier.getId().equalsIgnoreCase("TROPHEY_GOD")) {
            String msg = extension.getCustomConfig("commands/fishing.yml").get().getString("messages.trophy-caught-chat");
            ChatUtil.sendMessage(angler, msg, "{fish}", species.getDisplayName(), "{weight}", weightStr);
        }

        return result;
    }

    private WeightTier rollWeightTier() {
        if (weightTiers.isEmpty()) {
            return new WeightTier("DEFAULT", 100.0, 0.0, 0.5, "&e&l");
        }
        double totalWeight = 0.0;
        for (WeightTier t : weightTiers) {
            totalWeight += Math.max(0.0, t.getChance());
        }

        double roll = ThreadLocalRandom.current().nextDouble(0.0, totalWeight);
        double cursor = 0.0;
        for (WeightTier t : weightTiers) {
            cursor += Math.max(0.0, t.getChance());
            if (roll <= cursor) {
                return t;
            }
        }
        return weightTiers.get(0);
    }

    public FishSpeciesConfig getSpeciesByKey(String key) {
        if (key == null) return null;
        return speciesByKey.get(key.toUpperCase());
    }
}