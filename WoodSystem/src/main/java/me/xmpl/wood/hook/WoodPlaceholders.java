package me.xmpl.wood.hook;

import me.xmpl.lib.hook.ExtensionPlaceholder;
import me.xmpl.wood.WoodSystemExtension;
import me.xmpl.wood.model.PlayerWoodBag;
import me.xmpl.wood.model.WoodTreeType;
import org.bukkit.entity.Player;

public class WoodPlaceholders implements ExtensionPlaceholder {
    private final WoodSystemExtension extension;

    public WoodPlaceholders(WoodSystemExtension extension) {
        this.extension = extension;
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (player == null) return "0";
        PlayerWoodBag bag = extension.getBagStorage().getBag(player.getName());

        if (params.equalsIgnoreCase("total")) {
            return String.valueOf(bag.getTotalTrees());
        }

        try {
            WoodTreeType type = WoodTreeType.valueOf(params.toUpperCase());
            return String.valueOf(bag.getAmount(type));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}