package me.xmpl.lib.hook;

import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.bukkit.selections.Selection;
import me.xmpl.lib.util.CuboidBox;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class WorldEditHook {
    private WorldEditHook() {}

    public static CuboidBox getPlayerSelection(Player player, String regionName) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("WorldEdit");
        if (!(plugin instanceof WorldEditPlugin) || !plugin.isEnabled()) {
            return null;
        }

        WorldEditPlugin we = (WorldEditPlugin) plugin;
        Selection sel = we.getSelection(player);
        if (sel == null || sel.getMinimumPoint() == null || sel.getMaximumPoint() == null || sel.getWorld() == null) {
            return null;
        }

        Location min = sel.getMinimumPoint();
        Location max = sel.getMaximumPoint();
        return new CuboidBox(
                regionName,
                sel.getWorld().getName(),
                min.getBlockX(), min.getBlockY(), min.getBlockZ(),
                max.getBlockX(), max.getBlockY(), max.getBlockZ()
        );
    }
}