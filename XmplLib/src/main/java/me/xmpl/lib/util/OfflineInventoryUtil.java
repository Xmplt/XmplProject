package me.xmpl.lib.util;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.UUID;

public final class OfflineInventoryUtil {
    private OfflineInventoryUtil() {}

    public static class OfflinePlayerData {
        public final ItemStack[] storage = new ItemStack[36];
        public final ItemStack[] armor = new ItemStack[4]; // 0=boots, 1=leggings, 2=chest, 3=helmet
        public ItemStack offhand = new ItemStack(Material.AIR);
        public double health = 20.0;
        public double maxHealth = 20.0;
    }

    private static File getPlayerDataFile(UUID uuid) {
        if (Bukkit.getWorlds().isEmpty()) return null;
        File worldFolder = Bukkit.getWorlds().get(0).getWorldFolder();
        File playerDataDir = new File(worldFolder, "playerdata");
        return new File(playerDataDir, uuid.toString() + ".dat");
    }

    public static OfflinePlayerData loadOfflineData(UUID uuid) {
        File file = getPlayerDataFile(uuid);
        if (file == null || !file.exists()) return null;

        try {
            String version = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
            Class<?> compressedStreamTools = Class.forName("net.minecraft.server." + version + ".NBTCompressedStreamTools");
            Class<?> nbtTagCompoundClass = Class.forName("net.minecraft.server." + version + ".NBTTagCompound");
            Class<?> nbtTagListClass = Class.forName("net.minecraft.server." + version + ".NBTTagList");
            Class<?> nmsItemStackClass = Class.forName("net.minecraft.server." + version + ".ItemStack");
            Class<?> craftItemStackClass = Class.forName("org.bukkit.craftbukkit." + version + ".inventory.CraftItemStack");

            Method readCompressed = compressedStreamTools.getMethod("a", InputStream.class);
            Object rootCompound;
            try (FileInputStream fis = new FileInputStream(file)) {
                rootCompound = readCompressed.invoke(null, fis);
            }

            OfflinePlayerData data = new OfflinePlayerData();

            Method hasKey = nbtTagCompoundClass.getMethod("hasKey", String.class);
            Method getFloat = nbtTagCompoundClass.getMethod("getFloat", String.class);
            if ((boolean) hasKey.invoke(rootCompound, "Health")) {
                data.health = (float) getFloat.invoke(rootCompound, "Health");
            }

            Method getList = nbtTagCompoundClass.getMethod("getList", String.class, int.class);
            Object invList = getList.invoke(rootCompound, "Inventory", 10);

            Method sizeMethod = nbtTagListClass.getMethod("size");
            Method getCompoundAt = nbtTagListClass.getMethod("get", int.class);
            Method getByte = nbtTagCompoundClass.getMethod("getByte", String.class);
            Constructor<?> nmsItemConstructor = nmsItemStackClass.getConstructor(nbtTagCompoundClass);
            Method asBukkitCopy = craftItemStackClass.getMethod("asBukkitCopy", nmsItemStackClass);

            int count = (int) sizeMethod.invoke(invList);
            for (int i = 0; i < count; i++) {
                Object itemTag = getCompoundAt.invoke(invList, i);
                int slot = (byte) getByte.invoke(itemTag, "Slot") & 255;
                Object nmsItem = nmsItemConstructor.newInstance(itemTag);
                ItemStack bukkitItem = (ItemStack) asBukkitCopy.invoke(null, nmsItem);

                if (slot >= 0 && slot < 36) {
                    data.storage[slot] = bukkitItem;
                } else if (slot >= 100 && slot < 104) {
                    data.armor[slot - 100] = bukkitItem;
                } else if (slot == 150) {
                    data.offhand = bukkitItem;
                }
            }
            return data;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static boolean saveOfflineData(UUID uuid, OfflinePlayerData data) {
        File file = getPlayerDataFile(uuid);
        if (file == null || !file.exists()) return false;

        try {
            String version = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
            Class<?> compressedStreamTools = Class.forName("net.minecraft.server." + version + ".NBTCompressedStreamTools");
            Class<?> nbtTagCompoundClass = Class.forName("net.minecraft.server." + version + ".NBTTagCompound");
            Class<?> nbtTagListClass = Class.forName("net.minecraft.server." + version + ".NBTTagList");
            Class<?> nbtBaseClass = Class.forName("net.minecraft.server." + version + ".NBTBase");
            Class<?> nmsItemStackClass = Class.forName("net.minecraft.server." + version + ".ItemStack");
            Class<?> craftItemStackClass = Class.forName("org.bukkit.craftbukkit." + version + ".inventory.CraftItemStack");

            Method readCompressed = compressedStreamTools.getMethod("a", InputStream.class);
            Object rootCompound;
            try (FileInputStream fis = new FileInputStream(file)) {
                rootCompound = readCompressed.invoke(null, fis);
            }

            Object newInvList = nbtTagListClass.getConstructor().newInstance();
            Method addListElement = nbtTagListClass.getMethod("add", nbtBaseClass);
            Method asNMSCopy = craftItemStackClass.getMethod("asNMSCopy", ItemStack.class);
            Method saveItem = nmsItemStackClass.getMethod("save", nbtTagCompoundClass);
            Method setByte = nbtTagCompoundClass.getMethod("setByte", String.class, byte.class);

            // 1. Обычные слоты 0..35
            for (int i = 0; i < 36; i++) {
                ItemStack item = data.storage[i];
                if (item != null && item.getType() != Material.AIR) {
                    Object tag = nbtTagCompoundClass.getConstructor().newInstance();
                    setByte.invoke(tag, "Slot", (byte) i);
                    Object nmsItem = asNMSCopy.invoke(null, item);
                    saveItem.invoke(nmsItem, tag);
                    addListElement.invoke(newInvList, tag);
                }
            }

            // 2. Броня 100..103
            for (int i = 0; i < 4; i++) {
                ItemStack item = data.armor[i];
                if (item != null && item.getType() != Material.AIR) {
                    Object tag = nbtTagCompoundClass.getConstructor().newInstance();
                    setByte.invoke(tag, "Slot", (byte) (100 + i));
                    Object nmsItem = asNMSCopy.invoke(null, item);
                    saveItem.invoke(nmsItem, tag);
                    addListElement.invoke(newInvList, tag);
                }
            }

            // 3. Вторая рука 150
            if (data.offhand != null && data.offhand.getType() != Material.AIR) {
                Object tag = nbtTagCompoundClass.getConstructor().newInstance();
                setByte.invoke(tag, "Slot", (byte) 150);
                Object nmsItem = asNMSCopy.invoke(null, data.offhand);
                saveItem.invoke(nmsItem, tag);
                addListElement.invoke(newInvList, tag);
            }

            Method setTag = nbtTagCompoundClass.getMethod("set", String.class, nbtBaseClass);
            setTag.invoke(rootCompound, "Inventory", newInvList);

            Method writeCompressed = compressedStreamTools.getMethod("a", nbtTagCompoundClass, OutputStream.class);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                writeCompressed.invoke(null, rootCompound, fos);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}