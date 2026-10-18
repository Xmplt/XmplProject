package me.xmpl.mine.hook;

import me.xmpl.lib.hook.ExtensionPlaceholder;
import me.xmpl.mine.MineSystemExtension;
import me.xmpl.mine.model.MineOreType;
import me.xmpl.mine.model.PlayerOreBag;
import org.bukkit.entity.Player;

public class MinePlaceholders implements ExtensionPlaceholder {
    private final MineSystemExtension extension;

    public MinePlaceholders(MineSystemExtension extension) {
        this.extension = extension;
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (player == null) return "0";
        PlayerOreBag bag = extension.getBagStorage().getBag(player.getName());

        if (params.equalsIgnoreCase("total")) {
            return String.valueOf(bag.getTotalItems());
        }

        try {
            MineOreType type = MineOreType.valueOf(params.toUpperCase());
            return String.valueOf(bag.getAmount(type));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}