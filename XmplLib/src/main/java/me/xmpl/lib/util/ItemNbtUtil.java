package me.xmpl.lib.util;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Method;

public final class ItemNbtUtil {
    private ItemNbtUtil() {}

    private static String getVersion() {
        return Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
    }

    public static ItemStack setDouble(ItemStack item, String key, double value) {
        if (item == null || item.getType() == Material.AIR) return item;
        ItemMeta savedMeta = item.hasItemMeta() ? item.getItemMeta().clone() : null;
        try {
            String v = getVersion();
            Class<?> craftItemStack = Class.forName("org.bukkit.craftbukkit." + v + ".inventory.CraftItemStack");
            Class<?> nmsItemStack = Class.forName("net.minecraft.server." + v + ".ItemStack");
            Class<?> nbtTagCompound = Class.forName("net.minecraft.server." + v + ".NBTTagCompound");

            Method asNMSCopy = craftItemStack.getMethod("asNMSCopy", ItemStack.class);
            Object nmsItem = asNMSCopy.invoke(null, item);

            Method hasTag = nmsItemStack.getMethod("hasTag");
            Method getTag = nmsItemStack.getMethod("getTag");
            Method setTag = nmsItemStack.getMethod("setTag", nbtTagCompound);

            Object tag = (boolean) hasTag.invoke(nmsItem) ? getTag.invoke(nmsItem) : nbtTagCompound.getConstructor().newInstance();
            nbtTagCompound.getMethod("setDouble", String.class, double.class).invoke(tag, key, value);
            setTag.invoke(nmsItem, tag);

            ItemStack result = (ItemStack) craftItemStack.getMethod("asBukkitCopy", nmsItemStack).invoke(null, nmsItem);
            if (savedMeta != null) {
                ItemMeta newMeta = result.getItemMeta();
                if (savedMeta.hasDisplayName()) newMeta.setDisplayName(savedMeta.getDisplayName());
                if (savedMeta.hasLore()) newMeta.setLore(savedMeta.getLore());
                result.setItemMeta(newMeta);
            }
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return item;
        }
    }

    public static double getDouble(ItemStack item, String key, double defaultValue) {
        if (item == null || item.getType() == Material.AIR) return defaultValue;
        try {
            String v = getVersion();
            Class<?> craftItemStack = Class.forName("org.bukkit.craftbukkit." + v + ".inventory.CraftItemStack");
            Class<?> nmsItemStack = Class.forName("net.minecraft.server." + v + ".ItemStack");
            Class<?> nbtTagCompound = Class.forName("net.minecraft.server." + v + ".NBTTagCompound");

            Object nmsItem = craftItemStack.getMethod("asNMSCopy", ItemStack.class).invoke(null, item);
            if (nmsItem == null || !(boolean) nmsItemStack.getMethod("hasTag").invoke(nmsItem)) return defaultValue;

            Object tag = nmsItemStack.getMethod("getTag").invoke(nmsItem);
            if (!(boolean) nbtTagCompound.getMethod("hasKey", String.class).invoke(tag, key)) return defaultValue;

            return (double) nbtTagCompound.getMethod("getDouble", String.class).invoke(tag, key);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static ItemStack setString(ItemStack item, String key, String value) {
        if (item == null || item.getType() == Material.AIR) return item;
        ItemMeta savedMeta = item.hasItemMeta() ? item.getItemMeta().clone() : null;
        try {
            String v = getVersion();
            Class<?> craftItemStack = Class.forName("org.bukkit.craftbukkit." + v + ".inventory.CraftItemStack");
            Class<?> nmsItemStack = Class.forName("net.minecraft.server." + v + ".ItemStack");
            Class<?> nbtTagCompound = Class.forName("net.minecraft.server." + v + ".NBTTagCompound");

            Object nmsItem = craftItemStack.getMethod("asNMSCopy", ItemStack.class).invoke(null, item);
            boolean has = (boolean) nmsItemStack.getMethod("hasTag").invoke(nmsItem);
            Object tag = has ? nmsItemStack.getMethod("getTag").invoke(nmsItem) : nbtTagCompound.getConstructor().newInstance();

            nbtTagCompound.getMethod("setString", String.class, String.class).invoke(tag, key, value);
            nmsItemStack.getMethod("setTag", nbtTagCompound).invoke(nmsItem, tag);

            ItemStack result = (ItemStack) craftItemStack.getMethod("asBukkitCopy", nmsItemStack).invoke(null, nmsItem);
            if (savedMeta != null) {
                ItemMeta newMeta = result.getItemMeta();
                if (savedMeta.hasDisplayName()) newMeta.setDisplayName(savedMeta.getDisplayName());
                if (savedMeta.hasLore()) newMeta.setLore(savedMeta.getLore());
                result.setItemMeta(newMeta);
            }
            return result;
        } catch (Exception e) {
            return item;
        }
    }

    public static String getString(ItemStack item, String key) {
        if (item == null || item.getType() == Material.AIR) return null;
        try {
            String v = getVersion();
            Class<?> craftItemStack = Class.forName("org.bukkit.craftbukkit." + v + ".inventory.CraftItemStack");
            Class<?> nmsItemStack = Class.forName("net.minecraft.server." + v + ".ItemStack");
            Class<?> nbtTagCompound = Class.forName("net.minecraft.server." + v + ".NBTTagCompound");

            Object nmsItem = craftItemStack.getMethod("asNMSCopy", ItemStack.class).invoke(null, item);
            if (nmsItem == null || !(boolean) nmsItemStack.getMethod("hasTag").invoke(nmsItem)) return null;

            Object tag = nmsItemStack.getMethod("getTag").invoke(nmsItem);
            if (!(boolean) nbtTagCompound.getMethod("hasKey", String.class).invoke(tag, key)) return null;

            return (String) nbtTagCompound.getMethod("getString", String.class).invoke(tag, key);
        } catch (Exception e) {
            return null;
        }
    }
}