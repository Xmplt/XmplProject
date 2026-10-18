package me.xmpl.wood.listener;

import me.xmpl.lib.util.ChatUtil;
import me.xmpl.lib.util.ScoreboardUtil;
import me.xmpl.lib.util.TreeStructureScanner;
import me.xmpl.wood.WoodSystemExtension;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.StructureGrowEvent;

public class WoodBlockListener implements Listener {
    private final WoodSystemExtension extension;

    public WoodBlockListener(WoodSystemExtension extension) {
        this.extension = extension;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreakInWoodRegion(BlockBreakEvent event) {
        Block block = event.getBlock();

        if (!extension.getRegionStorage().isInAnyWoodRegion(block.getLocation())) {
            return;
        }

        Player player = event.getPlayer();

        // 1. Запрещаем ломать временные саженцы, которые ждут 10 секунд до восстановления дерева
        if (extension.getChopService().isProtectedSapling(block.getLocation())
                || block.getType() == Material.SAPLING) {
            event.setCancelled(true);
            return;
        }

        // 2. Если игрок пытается сломать не бревно (например листву или землю) — разрешаем только админу в креативе
        if (!TreeStructureScanner.isLog(block.getType())) {
            String adminPerm = extension.getConfig().getString("permissions.admin", "woodsystem.admin");
            if (player.getGameMode() == GameMode.CREATIVE && player.hasPermission(adminPerm)) {
                event.setCancelled(false);
            } else {
                event.setCancelled(true);
            }
            return;
        }

        // Всегда отменяем ванильное ломание блока, чтобы обрабатывать счетчик ударов (4-6)
        event.setCancelled(true);

        // 3. Проверяем, бьет ли игрок в САМЫЙ НИЖНИЙ блок дерева
        if (!TreeStructureScanner.isBottomTrunkBlock(block)) {
            String msg = extension.getCustomConfig("commands/wood.yml").get().getString("messages.hit-only-bottom-block");
            ChatUtil.sendMessage(player, msg);
            return;
        }

        // 4. Засчитываем удар по нижнему блоку дерева
        extension.getChopService().handleBaseLogStrike(player, block);
    }

    // Запрещаем листве осыпаться внутри регионов лесопилки
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLeavesDecay(LeavesDecayEvent event) {
        if (extension.getRegionStorage().isInAnyWoodRegion(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    // Запрещаем выращивать временные саженцы костной мукой раньше 10 секунд
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSaplingGrow(StructureGrowEvent event) {
        if (extension.getChopService().isProtectedSapling(event.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        ScoreboardUtil.restorePreviousBoard(event.getPlayer());
    }
}