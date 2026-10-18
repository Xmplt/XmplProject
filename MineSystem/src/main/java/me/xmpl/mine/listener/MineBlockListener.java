package me.xmpl.mine.listener;

import me.xmpl.mine.MineSystemExtension;
import me.xmpl.mine.model.MineOreType;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class MineBlockListener implements Listener {
    private final MineSystemExtension extension;

    public MineBlockListener(MineSystemExtension extension) {
        this.extension = extension;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreakInMine(BlockBreakEvent event) {
        Block block = event.getBlock();

        // Проверяем, находится ли блок в одном из наших формальных регионов шахты
        if (!extension.getRegionStorage().isInAnyMineRegion(block.getLocation())) {
            return;
        }

        Player player = event.getPlayer();

        // Если блок сейчас в процессе светофорной регенерации (стекло) — отменяем ломание!
        if (extension.getRegenService().isRegenerating(block.getLocation())
                || block.getType() == Material.STAINED_GLASS) {
            event.setCancelled(true);
            return;
        }

        MineOreType oreType = MineOreType.fromBlockMaterial(block.getType());
        if (oreType == null) {
            // Если это не руда (например камень или декор шахты), разрешаем ломать только админу в креативе
            String adminPerm = extension.getConfig().getString("permissions.admin", "minesystem.admin");
            if (player.getGameMode() == GameMode.CREATIVE && player.hasPermission(adminPerm)) {
                event.setCancelled(false);
            } else {
                event.setCancelled(true);
            }
            return;
        }

        // Отменяем стандартное разрушение блока и выпадение ванильного дропа
        event.setCancelled(true);

        // Пытаемся положить +1 руду в виртуальный рюкзак игрока
        if (extension.getEconomyService().tryCollectOre(player, oreType)) {
            // Запускаем светофор: Красное стекло -> Желтое стекло -> Зеленое стекло -> Руда
            extension.getRegenService().startRegenCycle(block, oreType);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        me.xmpl.lib.util.ScoreboardUtil.restorePreviousBoard(event.getPlayer());
    }
}