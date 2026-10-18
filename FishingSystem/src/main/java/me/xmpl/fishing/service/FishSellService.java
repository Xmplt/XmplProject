package me.xmpl.fishing.service;

import me.xmpl.fishing.FishingSystemExtension;
import me.xmpl.fishing.model.FishSpeciesConfig;
import me.xmpl.lib.hook.VaultHook;
import me.xmpl.lib.util.ChatUtil;
import me.xmpl.lib.util.ItemNbtUtil;
import me.xmpl.lib.util.NumberDateUtil;
import me.xmpl.lib.util.TitleUtil;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Locale;

public class FishSellService {
    private final FishingSystemExtension extension;

    public FishSellService(FishingSystemExtension extension) {
        this.extension = extension;
    }

    public void sellAllPlayerFish(Player player) {
        FileConfiguration cmdCfg = extension.getCustomConfig("commands/fishing.yml").get();
        PlayerInventory inv = player.getInventory();

        int totalFishCount = 0;
        double totalWeight = 0.0;
        double totalMoney = 0.0;

        // Проверяем все 36 слотов основного инвентаря + левую руку
        ItemStack[] contents = inv.getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            double[] calc = evaluateFishStack(item);
            if (calc != null) {
                int amount = item.getAmount();
                totalFishCount += amount;
                totalWeight += calc[0] * amount;
                totalMoney += calc[1] * amount;
                inv.setItem(i, null);
            }
        }

        ItemStack offhand = inv.getItemInOffHand();
        double[] offCalc = evaluateFishStack(offhand);
        if (offCalc != null) {
            int amount = offhand.getAmount();
            totalFishCount += amount;
            totalWeight += offCalc[0] * amount;
            totalMoney += offCalc[1] * amount;
            inv.setItemInOffHand(null);
        }

        if (totalFishCount == 0) {
            ChatUtil.sendMessage(player, cmdCfg.getString("messages.no-fish-to-sell"));
            return;
        }

        player.updateInventory();

        long finalMoney = Math.max(1L, Math.round(totalMoney));
        VaultHook.depositMoney(player, finalMoney);
        extension.getStatsStorage().recordSale(player.getName(), totalFishCount, finalMoney);

        String colorCode = resolveSellColor(finalMoney);
        String moneyStr = NumberDateUtil.formatInteger(finalMoney);
        String weightStr = String.format(Locale.US, "%.2f", totalWeight);

        // 1. Зеленое сообщение в чат
        ChatUtil.sendMessage(player, cmdCfg.getString("messages.sold-chat"),
                "{count}", totalFishCount,
                "{total_weight}", weightStr,
                "{color}", colorCode,
                "{amount}", moneyStr);

        // 2. Тайтл на экране (+количество денег$ с цветом в зависимости от суммы)
        FileConfiguration sellCfg = extension.getCustomConfig("sell-settings.yml").get();
        if (sellCfg.getBoolean("title.enabled", true)) {
            TitleUtil.sendTitle(
                    player,
                    sellCfg.getString("title.main-title", "{color}+{amount}$"),
                    sellCfg.getString("title.subtitle", "&fПродано рыб: &e{count} шт. &7({total_weight} кг)"),
                    sellCfg.getInt("title.fade-in", 5),
                    sellCfg.getInt("title.stay", 45),
                    sellCfg.getInt("title.fade-out", 10),
                    "{color}", colorCode,
                    "{amount}", moneyStr,
                    "{count}", totalFishCount,
                    "{total_weight}", weightStr
            );
        }
    }

    /**
     * Проверяет, является ли предмет нашей пойманной рыбой (по NBT-тегам).
     * Возвращает массив [вес_1_рыбы, цена_1_рыбы] или null, если это обычная/жареная рыба.
     */
    private double[] evaluateFishStack(ItemStack item) {
        if (item == null || item.getType() != Material.RAW_FISH) return null;

        double weight = ItemNbtUtil.getDouble(item, FishGeneratorService.NBT_WEIGHT, -1.0);
        if (weight <= 0.0) return null; // Нет нашего тега веса — не продаем!

        String speciesKey = ItemNbtUtil.getString(item, FishGeneratorService.NBT_SPECIES);
        FishSpeciesConfig species = extension.getGeneratorService().getSpeciesByKey(speciesKey);
        double pricePerKg = (species != null) ? species.getPricePerKg() : 100.0;

        double mult = ItemNbtUtil.getDouble(item, FishGeneratorService.NBT_MULTIPLIER, 1.0);
        double singleFishPrice = weight * pricePerKg * mult;

        return new double[]{weight, singleFishPrice};
    }

    private String resolveSellColor(long amount) {
        FileConfiguration sellCfg = extension.getCustomConfig("sell-settings.yml").get();
        ConfigurationSection sec = sellCfg.getConfigurationSection("sell-colors");
        if (sec == null) return "&e&l";

        long bestThreshold = -1L;
        String bestColor = "&e&l";

        for (String key : sec.getKeys(false)) {
            try {
                long threshold = Long.parseLong(key);
                if (amount >= threshold && threshold >= bestThreshold) {
                    bestThreshold = threshold;
                    bestColor = sec.getString(key, "&e&l");
                }
            } catch (NumberFormatException ignored) {}
        }
        return bestColor;
    }
}