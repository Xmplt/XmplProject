package me.xmpl.topka.listener;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import me.xmpl.topka.TopkaExtension;
import me.xmpl.topka.service.TopkaVotingService.VotingGuiHolder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TopkaVotingListener implements Listener {
    private final TopkaExtension extension;
    private final Map<UUID, Long> clickDebounce = new ConcurrentHashMap<>();

    public TopkaVotingListener(TopkaExtension extension) {
        this.extension = extension;
        registerPacketClickInterceptor();
    }

    /**
     * Перехватывает пакет клика (ЛКМ / ПКМ) по пакетному изумрудному блоку в голограмме через ProtocolLib.
     */
    private void registerPacketClickInterceptor() {
        if (Bukkit.getPluginManager().getPlugin("ProtocolLib") == null) return;
        ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(
                extension.getCore(),
                ListenerPriority.NORMAL,
                PacketType.Play.Client.USE_ENTITY
        ) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                if (!extension.isEnabled()) return;
                Player player = event.getPlayer();
                if (player == null) return;

                int entityId = event.getPacket().getIntegers().read(0);
                if (extension.getVotingHologramService().isVotingHologramEntityId(entityId)) {
                    event.setCancelled(true);
                    triggerVoteGuiOpenDebounced(player);
                }
            }
        });
    }

    /**
     * Дополнительно проверяет луч взгляда при клике в воздух/блок в сторону парящего изумрудного блока голограммы.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAirOrBlockClickTowardsHoloIcon(PlayerInteractEvent event) {
        if (event.getHand() == EquipmentSlot.OFF_HAND) return;
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_AIR && action != Action.RIGHT_CLICK_AIR
                && action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        if (isLookingAtFloatingEmeraldIcon(player)) {
            event.setCancelled(true);
            triggerVoteGuiOpenDebounced(player);
        }
    }

    private void triggerVoteGuiOpenDebounced(Player player) {
        long now = System.currentTimeMillis();
        Long last = clickDebounce.get(player.getUniqueId());
        if (last != null && (now - last) < 250L) {
            return;
        }
        clickDebounce.put(player.getUniqueId(), now);
        extension.runTask(() -> {
            if (player.isOnline()) {
                extension.getVotingService().openVotingGui(player);
            }
        });
    }

    private boolean isLookingAtFloatingEmeraldIcon(Player player) {
        Location baseLoc = extension.getArenaStorage().getVoteEmeraldBlockLocation();
        if (baseLoc == null || baseLoc.getWorld() == null) return false;
        if (!player.getWorld().getName().equalsIgnoreCase(baseLoc.getWorld().getName())) return false;

        double offset = extension.getCustomConfig("voting.yml").get().getDouble("hologram.height-offset", 1.25);
        Location iconCenter = new Location(baseLoc.getWorld(),
                baseLoc.getBlockX() + 0.5,
                baseLoc.getBlockY() + offset - 0.1,
                baseLoc.getBlockZ() + 0.5);

        Location eye = player.getEyeLocation();
        double dist = eye.distance(iconCenter);
        if (dist > 5.0) return false;

        Vector toIcon = iconCenter.toVector().subtract(eye.toVector());
        Vector dir = eye.getDirection().normalize();
        double proj = toIcon.dot(dir);
        if (proj <= 0) return false;

        Vector closestPoint = eye.toVector().add(dir.multiply(proj));
        return closestPoint.distanceSquared(iconCenter.toVector()) <= (0.65 * 0.65);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onVotingGuiClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Inventory top = event.getView().getTopInventory();
        if (top == null || !(top.getHolder() instanceof VotingGuiHolder)) return;

        event.setCancelled(true);
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(top)) return;

        Player voter = (Player) event.getWhoClicked();
        extension.getVotingService().handleSlotVoteClick(voter, event.getSlot());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onVotingGuiDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top != null && top.getHolder() instanceof VotingGuiHolder) {
            event.setCancelled(true);
        }
    }
}