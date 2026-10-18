package me.xmpl.lib.util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;

import java.util.Set;

public final class SignBlockUtil {
    private SignBlockUtil() {}

    public static boolean isSign(Block block) {
        if (block == null) return false;
        Material type = block.getType();
        return type == Material.SIGN_POST || type == Material.WALL_SIGN || type == Material.SIGN;
    }

    public static Block getTargetSign(Player player, int maxDistance) {
        if (player == null) return null;
        Block target = player.getTargetBlock((Set<Material>) null, maxDistance);
        return isSign(target) ? target : null;
    }

    public static boolean updateSignLines(Location loc, String line0, String line1, String line2, String line3) {
        if (loc == null || loc.getWorld() == null) return false;
        if (!loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
            return false;
        }

        Block block = loc.getBlock();
        if (!isSign(block) || !(block.getState() instanceof Sign)) {
            return false;
        }

        Sign sign = (Sign) block.getState();
        sign.setLine(0, ChatUtil.color(line0 != null ? line0 : ""));
        sign.setLine(1, ChatUtil.color(line1 != null ? line1 : ""));
        sign.setLine(2, ChatUtil.color(line2 != null ? line2 : ""));
        sign.setLine(3, ChatUtil.color(line3 != null ? line3 : ""));
        sign.update(true, false);
        return true;
    }
}