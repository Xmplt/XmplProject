package me.xmpl.lib.util;

import me.xmpl.lib.XmplLib;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PacketSlotUtil {

    private static final Map<UUID, Integer> ACTIVE_RESTORE_TASKS = new ConcurrentHashMap<>();

    private PacketSlotUtil() {}

    /**
     * Заменяет ТОЛЬКО конкретный нажатый слот (rawSlot) на fakeItem (красную шерсть) для игрока,
     * а через durationTicks возвращает настоящий предмет на место.
     */
    public static void sendTemporaryFakeSlot(Player player, int rawSlot, ItemStack fakeItem, long durationTicks) {
        if (player == null || !player.isOnline() || rawSlot < 0 || fakeItem == null) return;

        UUID uuid = player.getUniqueId();
        Integer oldTask = ACTIVE_RESTORE_TASKS.remove(uuid);
        if (oldTask != null) {
            Bukkit.getScheduler().cancelTask(oldTask);
        }

        // Отправляем пакет замены одного слота на следующий тик после завершения InventoryClickEvent
        Bukkit.getScheduler().runTask(XmplLib.getInstance(), () -> {
            if (!player.isOnline()) return;
            sendSingleSlotPacketNow(player, rawSlot, fakeItem);
        });

        int taskId = Bukkit.getScheduler().runTaskLater(XmplLib.getInstance(), () -> {
            ACTIVE_RESTORE_TASKS.remove(uuid);
            if (player.isOnline()) {
                player.updateInventory();
            }
        }, Math.max(2L, durationTicks)).getTaskId();

        ACTIVE_RESTORE_TASKS.put(uuid, taskId);
    }

    /**
     * Оставлено для обратной совместимости (меняет слот 0, если вызван старый метод).
     */
    public static void sendTemporaryFakeChestContents(Player player, Inventory topInventory, ItemStack fakeItem, long durationTicks) {
        sendTemporaryFakeSlot(player, 0, fakeItem, durationTicks);
    }

    private static void sendSingleSlotPacketNow(Player player, int rawSlot, ItemStack fakeItem) {
        try {
            String version = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
            Class<?> craftItemStack = Class.forName("org.bukkit.craftbukkit." + version + ".inventory.CraftItemStack");
            Class<?> nmsItemStack = Class.forName("net.minecraft.server." + version + ".ItemStack");
            Class<?> packetSetSlot = Class.forName("net.minecraft.server." + version + ".PacketPlayOutSetSlot");
            Class<?> packetClass = Class.forName("net.minecraft.server." + version + ".Packet");

            Object entityPlayer = player.getClass().getMethod("getHandle").invoke(player);
            Object activeContainer = entityPlayer.getClass().getField("activeContainer").get(entityPlayer);
            int windowId = (int) activeContainer.getClass().getField("windowId").get(activeContainer);

            if (windowId == 0) return;

            Method asNMSCopy = craftItemStack.getMethod("asNMSCopy", ItemStack.class);
            Object nmsFake = asNMSCopy.invoke(null, fakeItem);
            Object nmsAir = asNMSCopy.invoke(null, new ItemStack(Material.AIR));

            Constructor<?> setSlotConstructor = packetSetSlot.getConstructor(int.class, int.class, nmsItemStack);
            // Меняем ТОЛЬКО тот слот (rawSlot), по которому кликнул игрок!
            Object slotPacket = setSlotConstructor.newInstance(windowId, rawSlot, nmsFake);
            // Очищаем курсор мыши (-1, -1)
            Object clearCursorPacket = setSlotConstructor.newInstance(-1, -1, nmsAir);

            Object playerConnection = entityPlayer.getClass().getField("playerConnection").get(entityPlayer);
            Method sendPacket = playerConnection.getClass().getMethod("sendPacket", packetClass);
            sendPacket.invoke(playerConnection, slotPacket);
            sendPacket.invoke(playerConnection, clearCursorPacket);
        } catch (Exception e) {
            player.updateInventory();
        }
    }
}