package me.xmpl.enderrub.hook;

import me.xmpl.enderrub.EnderRubExtension;
import me.xmpl.enderrub.service.EnderRubService;
import me.xmpl.lib.hook.ExtensionPlaceholder;
import me.xmpl.lib.util.ChatUtil;
import me.xmpl.lib.util.NumberDateUtil;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public class EnderRubPlaceholders implements ExtensionPlaceholder {
    private final EnderRubExtension extension;
    private final EnderRubService service;

    public EnderRubPlaceholders(EnderRubExtension extension, EnderRubService service) {
        this.extension = extension;
        this.service = service;
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (player == null) return "0";

        long balance = service.getBalance(player.getName());
        String formatted = NumberDateUtil.formatInteger(balance);
        String symbol = extension.getConfig().getString("currency.symbol", "₪");
        FileConfiguration phCfg = extension.getCustomConfig("placeholders.yml").get();

        switch (params.toLowerCase()) {
            case "balance":
                return String.valueOf(balance);
            case "balance_formatted":
                return formatted;
            case "symbol":
                return symbol;
            case "tab": {
                if (balance <= 0) {
                    String zeroFmt = phCfg.getString("zero-balance-tab-format", "&70 &8{symbol}");
                    return ChatUtil.color(zeroFmt.replace("{symbol}", symbol));
                }
                String tabFmt = phCfg.getString("tab-format", "&d&l{balance_formatted} &5&l{symbol}");
                return ChatUtil.color(ChatUtil.applyReplacements(tabFmt,
                        "{balance}", balance,
                        "{balance_formatted}", formatted,
                        "{symbol}", symbol));
            }
            default:
                return null;
        }
    }
}