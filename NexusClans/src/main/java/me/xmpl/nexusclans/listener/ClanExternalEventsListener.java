package me.xmpl.nexusclans.listener;

import me.xmpl.airdrop.api.AirDropFirstActivateEvent;
import me.xmpl.lib.extension.XmplExtension;
import me.xmpl.lib.util.TreeStructureScanner;
import me.xmpl.nexuschest.NexusChestExtension;
import me.xmpl.nexusclans.NexusClansExtension;
import me.xmpl.topka.api.TopkaFirstLootTakeEvent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.lang.reflect.Method;

public class ClanExternalEventsListener implements Listener {
    private final NexusClansExtension extension;
    // Пин-код текущего раунда NexusChest, за который уже был обработан первый взятый предмет
    private String rewardedNexusRoundPinCode = "";

    public ClanExternalEventsListener(NexusClansExtension extension) {
        this.extension = extension;
    }

    // 1. Первая активация AirDropSystem -> 50..75 очков клана
    @EventHandler(priority = EventPriority.MONITOR)
    public void onAirDropFirstActivate(AirDropFirstActivateEvent event) {
        extension.getSmartPointsService().handleFirstEventAction(event.getActivator(), "airdrop", "AirDrop");
    }

    // 2. Первое взятие предмета из сундука TOPKA -> 50..75 очков клана
    @EventHandler(priority = EventPriority.MONITOR)
    public void onTopkaFirstTake(TopkaFirstLootTakeEvent event) {
        extension.getSmartPointsService().handleFirstEventAction(event.getPlayer(), "topka", "TOPKA");
    }

    // 3. Первое успешное взятие предмета из любого из двух сундуков NexusChest -> 50..75 очков клана
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onNexusChestFirstTake(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Inventory top = event.getView().getTopInventory();
        if (top == null || event.getRawSlot() < 0 || event.getRawSlot() >= top.getSize()) return;
        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;
        if (!(top.getHolder() instanceof Chest)) return;

        XmplExtension ncExt = extension.getCore().getExtensionManager().getExtension("NexusChest");
        if (!(ncExt instanceof NexusChestExtension) || !ncExt.isEnabled()) return;

        NexusChestExtension nexusChest = (NexusChestExtension) ncExt;
        if (!nexusChest.getEventManager().isEventRunning()) {
            rewardedNexusRoundPinCode = "";
            return;
        }

        Block chestBlock = ((Chest) top.getHolder()).getBlock();
        if (chestBlock == null) return;

        if (nexusChest.getArenaStorage().getChestNumberAt(chestBlock.getLocation()) != 0) {
            String currentPin = nexusChest.getEventManager().getCurrentPinCode();
            if (currentPin != null && !currentPin.isEmpty() && !currentPin.equals(rewardedNexusRoundPinCode)) {
                rewardedNexusRoundPinCode = currentPin;
                extension.getSmartPointsService().handleFirstEventAction((Player) event.getWhoClicked(), "nexuschest", "NexusChest");
            }
        }
    }

    // 4. Добыча руды в MineSystem (20 руд = +1 очко, до 100/день) и деревьев в WoodSystem (15 деревьев = +1 очко, до 100/день)
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onMineOrWoodBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Location loc = block.getLocation();

        if (isOreMaterial(block.getType()) && isBlockInMineRegion(loc)) {
            extension.runTask(() -> {
                if (loc.getBlock().getType() == Material.STAINED_GLASS) {
                    extension.getSmartPointsService().handleMineOreBroken(player);
                }
            });
            return;
        }

        if (TreeStructureScanner.isBottomTrunkBlock(block) && isBlockInWoodRegion(loc)) {
            extension.runTask(() -> {
                if (loc.getBlock().getType() == Material.SAPLING) {
                    extension.getSmartPointsService().handleWoodTreeChopped(player);
                }
            });
        }
    }

    private boolean isOreMaterial(Material m) {
        switch (m) {
            case COAL_ORE:
            case IRON_ORE:
            case GOLD_ORE:
            case REDSTONE_ORE:
            case GLOWING_REDSTONE_ORE:
            case LAPIS_ORE:
            case QUARTZ_ORE:
            case DIAMOND_ORE:
            case EMERALD_ORE:
                return true;
            default:
                return false;
        }
    }

    private boolean isBlockInMineRegion(Location loc) {
        try {
            XmplExtension mineExt = extension.getCore().getExtensionManager().getExtension("MineSystem");
            if (mineExt == null || !mineExt.isEnabled()) return false;
            Object regStorage = mineExt.getClass().getMethod("getRegionStorage").invoke(mineExt);
            Method check = regStorage.getClass().getMethod("isInAnyMineRegion", Location.class);
            return (boolean) check.invoke(regStorage, loc);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isBlockInWoodRegion(Location loc) {
        try {
            XmplExtension woodExt = extension.getCore().getExtensionManager().getExtension("WoodSystem");
            if (woodExt == null || !woodExt.isEnabled()) return false;
            Object regStorage = woodExt.getClass().getMethod("getRegionStorage").invoke(woodExt);
            Method check = regStorage.getClass().getMethod("isInAnyWoodRegion", Location.class);
            return (boolean) check.invoke(regStorage, loc);
        } catch (Exception e) {
            return false;
        }
    }
}