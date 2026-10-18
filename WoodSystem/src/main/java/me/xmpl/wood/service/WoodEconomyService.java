package me.xmpl.wood.service;

import me.xmpl.lib.hook.VaultHook;
import me.xmpl.lib.util.ChatUtil;
import me.xmpl.lib.util.NumberDateUtil;
import me.xmpl.lib.util.TitleUtil;
import me.xmpl.wood.WoodSystemExtension;
import me.xmpl.wood.model.PlayerWoodBag;
import me.xmpl.wood.model.WoodTreeType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public class WoodEconomyService {
    private final WoodSystemExtension extension;

    public WoodEconomyService(WoodSystemExtension extension) {
        this.extension = extension;
    }

    public void addChoppedTree(Player player, WoodTreeType treeType, String displayName) {
        PlayerWoodBag bag = extension.getBagStorage().getBag(player.getName());
        bag.addOne(treeType);
        extension.getBagStorage().saveBagAsync(player.getName());
        extension.getScoreboardService().updatePlayerScoreboard(player);

        String msg = extension.getCustomConfig("commands/wood.yml").get().getString("messages.tree-chopped-chat");
        ChatUtil.sendMessage(player, msg, "{tree}", displayName, "{total}", bag.getTotalTrees());
    }

    public void sellAllWood(Player player) {
        FileConfiguration cmdCfg = extension.getCustomConfig("commands/wood.yml").get();
        PlayerWoodBag bag = extension.getBagStorage().getBag(player.getName());

        if (bag.isEmpty()) {
            ChatUtil.sendMessage(player, cmdCfg.getString("messages.nothing-to-sell"));
            return;
        }

        FileConfiguration treesCfg = extension.getCustomConfig("trees.yml").get();
        double totalMoney = 0.0;
        int totalTrees = bag.getTotalTrees();

        for (WoodTreeType type : WoodTreeType.values()) {
            int count = bag.getAmount(type);
            if (count > 0) {
                double price = treesCfg.getDouble("trees." + type.name() + ".price", 35.0);
                totalMoney += count * price;
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
                "{total}", totalTrees,
                "{color}", colorCode,
                "{amount}", amountStr);

        // 2. Титл на экране с суммой и цветом из sell-colors ('0', '100', '500', '1000')
        FileConfiguration sellCfg = extension.getCustomConfig("sell-settings.yml").get();
        if (sellCfg.getBoolean("title.enabled", true)) {
            TitleUtil.sendTitle(
                    player,
                    sellCfg.getString("title.main-title", "{color}&l+{amount}$"),
                    sellCfg.getString("title.subtitle", "&aВы продали срубленные деревья ({total} шт.)"),
                    sellCfg.getInt("title.fade-in", 5),
                    sellCfg.getInt("title.stay", 45),
                    sellCfg.getInt("title.fade-out", 10),
                    "{color}", colorCode,
                    "{amount}", amountStr,
                    "{total}", totalTrees
            );
        }
    }

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