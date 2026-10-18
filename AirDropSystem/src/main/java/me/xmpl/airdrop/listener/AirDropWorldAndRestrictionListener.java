package me.xmpl.airdrop.listener;

import me.xmpl.airdrop.AirDropExtension;
import me.xmpl.airdrop.model.AirDropStage;
import me.xmpl.airdrop.model.PersistedAirDropState;
import me.xmpl.lib.util.ChatUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public class AirDropWorldAndRestrictionListener implements Listener {
    private final AirDropExtension extension;

    public AirDropWorldAndRestrictionListener(AirDropExtension extension) {
        this.extension = extension;
    }

    private boolean isSameBlock(Location a, Location b) {
        if (a == null || b == null || a.getWorld() == null || b.getWorld() == null) return false;
        return a.getWorld().getName().equalsIgnoreCase(b.getWorld().getName())
                && a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }

    private boolean isPlayerInActiveChestWorld(Player player) {
        PersistedAirDropState st = extension.getLifecycleManager().getState();
        if (st.stage == AirDropStage.IDLE || st.chestLocation == null || st.chestLocation.getWorld() == null) {
            return false;
        }
        // Запреты действуют пока сундук стоит в мире (до конца выдачи лута)
        if (st.stage == AirDropStage.FINISHED) return false;
        return player.getWorld().getName().equalsIgnoreCase(st.chestLocation.getWorld().getName());
    }

    // 1. Клик ПКМ по сундуку -> Нельзя открыть инвентарь, активирует сундук!
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChestRightClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        PersistedAirDropState st = extension.getLifecycleManager().getState();
        if (st.stage == AirDropStage.IDLE || st.chestLocation == null) return;

        if (isSameBlock(event.getClickedBlock().getLocation(), st.chestLocation)) {
            event.setCancelled(true); // Никогда не даем открыть стандартное окно сундука!
            if (st.stage == AirDropStage.WAITING_ACTIVATION) {
                extension.getLifecycleManager().activateChestByPlayer(event.getPlayer());
            }
        }
    }

    // 2. Запрет ломать сундук и блок под ним
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        PersistedAirDropState st = extension.getLifecycleManager().getState();
        if (st.stage == AirDropStage.IDLE || st.chestLocation == null) return;

        Location broken = event.getBlock().getLocation();
        if (isSameBlock(broken, st.chestLocation) || isSameBlock(broken, st.chestLocation.clone().subtract(0, 1, 0))) {
            event.setCancelled(true);
            String msg = extension.getCustomConfig("messages.yml").get().getString("cannot-break-chest");
            ChatUtil.sendMessage(event.getPlayer(), msg);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        PersistedAirDropState st = extension.getLifecycleManager().getState();
        if (st.stage == AirDropStage.IDLE || st.chestLocation == null) return;
        event.blockList().removeIf(b -> isSameBlock(b.getLocation(), st.chestLocation));
    }

    // 3. Ограничение №1: ЭЛИТРЫ (только в мире сундука!)
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onElytraToggle(EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        if (!isPlayerInActiveChestWorld(player)) return;

        if (extension.getLifecycleManager().getState().restrictElytra && event.isGliding()) {
            event.setCancelled(true);
            player.setGliding(false);
            FileConfiguration rCfg = extension.getCustomConfig("restrictions.yml").get();
            ChatUtil.sendMessage(player, rCfg.getString("blocked-messages.elytra"));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMoveWhileGliding(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!player.isGliding() || !isPlayerInActiveChestWorld(player)) return;
        if (extension.getLifecycleManager().getState().restrictElytra) {
            player.setGliding(false);
            FileConfiguration rCfg = extension.getCustomConfig("restrictions.yml").get();
            ChatUtil.sendMessage(player, rCfg.getString("blocked-messages.elytra"));
        }
    }

    // 4. Ограничение №2: ЭНДЕР-ЖЕМЧУГ (только в мире сундука!)
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPearlUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getItem() == null || event.getItem().getType() != Material.ENDER_PEARL) return;

        Player player = event.getPlayer();
        if (!isPlayerInActiveChestWorld(player)) return;

        if (extension.getLifecycleManager().getState().restrictPearl) {
            event.setCancelled(true);
            player.updateInventory();
            FileConfiguration rCfg = extension.getCustomConfig("restrictions.yml").get();
            ChatUtil.sendMessage(player, rCfg.getString("blocked-messages.pearl"));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPearlOrChorusTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (!isPlayerInActiveChestWorld(player)) return;

        PersistedAirDropState st = extension.getLifecycleManager().getState();
        FileConfiguration rCfg = extension.getCustomConfig("restrictions.yml").get();

        if (event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL && st.restrictPearl) {
            event.setCancelled(true);
            ChatUtil.sendMessage(player, rCfg.getString("blocked-messages.pearl"));
        } else if (event.getCause() == PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT && st.restrictChorus) {
            // 5. Ограничение №3: ХОРУС (только в мире сундука!)
            event.setCancelled(true);
            ChatUtil.sendMessage(player, rCfg.getString("blocked-messages.chorus"));
        }
    }
}