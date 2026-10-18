package me.xmpl.headdrop.listener;

import me.xmpl.headdrop.HeadDropExtension;
import me.xmpl.headdrop.service.HeadFactoryService;
import me.xmpl.lib.util.SkullSkinUtil;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.SkullType;
import org.bukkit.block.Block;
import org.bukkit.block.Skull;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

public class HeadBlockPersistListener implements Listener {
    private final HeadDropExtension extension;
    private final HeadFactoryService headFactory;

    public HeadBlockPersistListener(HeadDropExtension extension, HeadFactoryService headFactory) {
        this.extension = extension;
        this.headFactory = headFactory;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSkullBlockBreak(BlockBreakEvent event) {
        if (!extension.getConfig().getBoolean("settings.preserve-name-on-block-break", true)) {
            return;
        }

        Block block = event.getBlock();
        if (block.getType() != Material.SKULL) return;
        if (!(block.getState() instanceof Skull)) return;

        Skull skull = (Skull) block.getState();
        if (skull.getSkullType() != SkullType.PLAYER) return;

        String ownerName = SkullSkinUtil.getOwnerNameFromSkullBlock(block);
        if (ownerName == null || ownerName.isEmpty()) return;

        // Если ломает не в креативе — заменяем стандартный дроп на нашу подписанную голову с сохранением GameProfile
        if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            ItemStack restoredHead = headFactory.buildHeadFromBrokenBlock(block, ownerName);
            event.setDropItems(false);
            block.getWorld().dropItemNaturally(block.getLocation(), restoredHead);
        }
    }
}