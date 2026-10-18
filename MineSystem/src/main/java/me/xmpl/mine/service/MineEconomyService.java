package me.xmpl.mine.service;

import me.xmpl.lib.hook.VaultHook;
import me.xmpl.lib.util.ChatUtil;
import me.xmpl.lib.util.NumberDateUtil;
import me.xmpl.lib.util.TitleUtil;
import me.xmpl.mine.MineSystemExtension;
import me.xmpl.mine.model.MineOreType;
import me.xmpl.mine.model.PlayerOreBag;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public class MineEconomyService {
    private final MineSystemExtension extension;

    public MineEconomyService(MineSystemExtension extension) {
        this.extension = extension;
    }

    /**
     * Пытается добавить 1 выкопанную руду игроку.
     * Возвращает true, если место было и руда добавлена. Если лимит заполнен — пишет сообщение в чат и возвращает false!
     */
    public boolean tryCollectOre(Player player, MineOreType oreType) {
        FileConfiguration oresCfg = extension.getCustomConfig("ores.yml").get();
        String path = "ores." + oreType.name();

        int maxLimit = oresCfg.getInt(path + ".max-limit", 70);
        String displayName = oresCfg.getString(path + ".display-name", oreType.name());

        PlayerOreBag bag = extension.getBagStorage().getBag(player.getName());
        int current = bag.getAmount(oreType);

        if (current >= maxLimit) {
            String msg = extension.getCustomConfig("commands/mine.yml").get().getString("messages.ore-limit-reached");
            ChatUtil.sendMessage(player, msg, "{ore}", displayName, "{limit}", maxLimit);
            return false;
        }

        bag.addOne(oreType);
        extension.getBagStorage().saveBagAsync(player.getName());
        extension.getScoreboardService().updatePlayerScoreboard(player);
        return true;
    }

    /**
     * Продает всю накопленную руду игрока (/mine sell)
     */
    public void sellAllOres(Player player) {
        FileConfiguration cmdCfg = extension.getCustomConfig("commands/mine.yml").get();
        PlayerOreBag bag = extension.getBagStorage().getBag(player.getName());

        if (bag.isEmpty()) {
            ChatUtil.sendMessage(player, cmdCfg.getString("messages.nothing-to-sell"));
            return;
        }

        FileConfiguration oresCfg = extension.getCustomConfig("ores.yml").get();
        double totalMoney = 0.0;
        int totalItems = bag.getTotalItems();

        for (MineOreType type : MineOreType.values()) {
            int count = bag.getAmount(type);
            if (count > 0) {
                double pricePerUnit = oresCfg.getDouble("ores." + type.name() + ".price", 15.0);
                totalMoney += count * pricePerUnit;
            }
        }

        long roundedMoney = Math.round(totalMoney);
        bag.clearAll();
        extension.getBagStorage().saveBagAsync(player.getName());
        extension.getScoreboardService().updatePlayerScoreboard(player);

        VaultHook.depositMoney(player, roundedMoney);

        String colorCode = resolveSellColor(roundedMoney);
        String amountStr = NumberDateUtil.formatInteger(roundedMoney);

        // 1. Сообщение в чат
        ChatUtil.sendMessage(player, cmdCfg.getString("messages.sell-success-chat"),
                "{total_items}", totalItems,
                "{color}", colorCode,
                "{amount}", amountStr);

        // 2. Большой Title на экране с цветом из sell-colors
        FileConfiguration sellCfg = extension.getCustomConfig("sell-settings.yml").get();
        if (sellCfg.getBoolean("title.enabled", true)) {
            TitleUtil.sendTitle(
                    player,
                    sellCfg.getString("title.main-title", "&a&lРУДА ПРОДАНА!"),
                    sellCfg.getString("title.subtitle", "&fВы получили: {color}&l+{amount}$"),
                    sellCfg.getInt("title.fade-in", 5),
                    sellCfg.getInt("title.stay", 45),
                    sellCfg.getInt("title.fade-out", 10),
                    "{color}", colorCode,
                    "{amount}", amountStr
            );
        }
    }

    /**
     * Выбирает цвет из секции sell-colors по порогам ('0', '100', '1000', '5000', '10000')
     */
    private String resolveSellColor(long amount) {
        FileConfiguration sellCfg = extension.getCustomConfig("sell-settings.yml").get();
        ConfigurationSection sec = sellCfg.getConfigurationSection("sell-colors");
        if (sec == null) return "&f";

        long bestThreshold = -1L;
        String bestColor = "&f";

        for (String key : sec.getKeys(false)) {
            try {
                long threshold = Long.parseLong(key);
                if (amount >= threshold && threshold >= bestThreshold) {
                    bestThreshold = threshold;
                    bestColor = sec.getString(key, "&f");
                }
            } catch (NumberFormatException ignored) {}
        }
        return bestColor;
    }
}