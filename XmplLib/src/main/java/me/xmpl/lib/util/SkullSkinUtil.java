package me.xmpl.lib.util;

import me.xmpl.lib.XmplLib;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Skull;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SkullSkinUtil {

    // Base64 от {"textures":{}} — предотвращает синхронные HTTP-запросы Spigot 1.12.2 к Mojang в главном потоке
    private static final String EMPTY_TEXTURES_BASE64 = "eyJ0ZXh0dXJlcyI6e319";

    private static final Map<String, Object> PROFILE_CACHE = new ConcurrentHashMap<>();
    private static final Set<String> LOADING_PROFILES = ConcurrentHashMap.newKeySet();

    private static Constructor<?> gameProfileConstructor;
    private static Method getPropertiesMethod;
    private static Method propertyMapPutMethod;
    private static Constructor<?> propertyConstructor;
    private static Field craftMetaSkullProfileField;

    private SkullSkinUtil() {}

    public static ItemStack createPlayerHead(Player victim, String displayName, List<String> lore) {
        ItemStack head = new ItemStack(Material.SKULL_ITEM, 1, (short) 3);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta == null) return head;

        boolean profileInjected = false;
        if (victim != null) {
            Object gameProfile = extractOnlineGameProfile(victim);
            if (gameProfile != null) {
                ensureTexturesKeyPresent(gameProfile);
                PROFILE_CACHE.put(victim.getName().toLowerCase(), gameProfile);
                profileInjected = injectProfile(meta, gameProfile);
            }
            if (!profileInjected) {
                meta.setOwningPlayer(victim);
            }
        }

        applyDisplayAndLore(meta, displayName, lore);
        head.setItemMeta(meta);
        return head;
    }

    public static ItemStack createOfflineHead(String playerName, String displayName, List<String> lore) {
        ItemStack head = new ItemStack(Material.SKULL_ITEM, 1, (short) 3);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta == null) return head;

        if (playerName != null && !playerName.isEmpty()) {
            String key = playerName.toLowerCase();

            Player online = Bukkit.getPlayerExact(playerName);
            if (online != null) {
                Object onlineProfile = extractOnlineGameProfile(online);
                if (onlineProfile != null) {
                    ensureTexturesKeyPresent(onlineProfile);
                    PROFILE_CACHE.put(key, onlineProfile);
                }
            }

            Object cached = PROFILE_CACHE.get(key);
            if (cached != null) {
                injectProfile(meta, cached);
            } else {
                Object fastProfile = createNonBlockingOfflineProfile(playerName);
                if (fastProfile != null) {
                    injectProfile(meta, fastProfile);
                }
                fetchRealSkinAsync(playerName);
            }
        }

        applyDisplayAndLore(meta, displayName, lore);
        head.setItemMeta(meta);
        return head;
    }

    private static void initReflection() throws Exception {
        if (gameProfileConstructor == null) {
            Class<?> gpClass = Class.forName("com.mojang.authlib.GameProfile");
            Class<?> propClass = Class.forName("com.mojang.authlib.properties.Property");
            Class<?> propMapClass = Class.forName("com.mojang.authlib.properties.PropertyMap");

            gameProfileConstructor = gpClass.getConstructor(UUID.class, String.class);
            getPropertiesMethod = gpClass.getMethod("getProperties");
            propertyConstructor = propClass.getConstructor(String.class, String.class);
            propertyMapPutMethod = propMapClass.getMethod("put", Object.class, Object.class);
        }
    }

    private static Object createNonBlockingOfflineProfile(String playerName) {
        try {
            initReflection();
            UUID offlineUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + playerName).getBytes(StandardCharsets.UTF_8));
            Object profile = gameProfileConstructor.newInstance(offlineUuid, playerName);
            Object propMap = getPropertiesMethod.invoke(profile);
            Object emptyTexProp = propertyConstructor.newInstance("textures", EMPTY_TEXTURES_BASE64);
            propertyMapPutMethod.invoke(propMap, "textures", emptyTexProp);
            return profile;
        } catch (Exception e) {
            return null;
        }
    }

    private static void ensureTexturesKeyPresent(Object gameProfile) {
        if (gameProfile == null) return;
        try {
            initReflection();
            Object propMap = getPropertiesMethod.invoke(gameProfile);
            Method containsKey = propMap.getClass().getMethod("containsKey", Object.class);
            boolean hasTextures = (boolean) containsKey.invoke(propMap, "textures");
            if (!hasTextures) {
                Object emptyTexProp = propertyConstructor.newInstance("textures", EMPTY_TEXTURES_BASE64);
                propertyMapPutMethod.invoke(propMap, "textures", emptyTexProp);
            }
        } catch (Exception ignored) {}
    }

    private static Object extractOnlineGameProfile(Player player) {
        try {
            Method getHandle = player.getClass().getMethod("getHandle");
            Object entityPlayer = getHandle.invoke(player);
            Method getProfile = entityPlayer.getClass().getMethod("getProfile");
            return getProfile.invoke(entityPlayer);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean injectProfile(SkullMeta meta, Object gameProfile) {
        if (meta == null || gameProfile == null) return false;
        try {
            if (craftMetaSkullProfileField == null) {
                craftMetaSkullProfileField = meta.getClass().getDeclaredField("profile");
                craftMetaSkullProfileField.setAccessible(true);
            }
            craftMetaSkullProfileField.set(meta, gameProfile);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    private static void fetchRealSkinAsync(String playerName) {
        String key = playerName.toLowerCase();
        if (PROFILE_CACHE.containsKey(key) || !LOADING_PROFILES.add(key)) return;

        XmplLib lib = XmplLib.getInstance();
        if (lib == null || !lib.isEnabled()) {
            LOADING_PROFILES.remove(key);
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(lib, () -> {
            try {
                ItemStack temp = new ItemStack(Material.SKULL_ITEM, 1, (short) 3);
                SkullMeta tempMeta = (SkullMeta) temp.getItemMeta();
                if (tempMeta != null) {
                    tempMeta.setOwner(playerName);
                    temp.setItemMeta(tempMeta);
                    SkullMeta resolvedMeta = (SkullMeta) temp.getItemMeta();
                    if (resolvedMeta != null) {
                        if (craftMetaSkullProfileField == null) {
                            craftMetaSkullProfileField = resolvedMeta.getClass().getDeclaredField("profile");
                            craftMetaSkullProfileField.setAccessible(true);
                        }
                        Object resolvedProfile = craftMetaSkullProfileField.get(resolvedMeta);
                        if (resolvedProfile != null) {
                            ensureTexturesKeyPresent(resolvedProfile);
                            PROFILE_CACHE.put(key, resolvedProfile);
                        } else {
                            Object fallback = createNonBlockingOfflineProfile(playerName);
                            if (fallback != null) PROFILE_CACHE.put(key, fallback);
                        }
                    }
                }
            } catch (Exception ignored) {
                Object fallback = createNonBlockingOfflineProfile(playerName);
                if (fallback != null) PROFILE_CACHE.put(key, fallback);
            } finally {
                LOADING_PROFILES.remove(key);
            }
        });
    }

    private static void applyDisplayAndLore(SkullMeta meta, String displayName, List<String> lore) {
        if (displayName != null) {
            meta.setDisplayName(ChatUtil.color(displayName));
        }
        if (lore != null && !lore.isEmpty()) {
            meta.setLore(ChatUtil.color(lore));
        }
    }

    public static ItemStack extractHeadFromBlock(Block block, String displayName, List<String> lore) {
        ItemStack head = new ItemStack(Material.SKULL_ITEM, 1, (short) 3);
        if (block == null || !(block.getState() instanceof Skull)) return head;

        Skull skullState = (Skull) block.getState();
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta == null) return head;

        try {
            Field tileField = skullState.getClass().getDeclaredField("skull");
            tileField.setAccessible(true);
            Object tileEntitySkull = tileField.get(skullState);

            Method getGameProfile = tileEntitySkull.getClass().getMethod("getGameProfile");
            Object gameProfile = getGameProfile.invoke(tileEntitySkull);

            if (gameProfile != null) {
                ensureTexturesKeyPresent(gameProfile);
                injectProfile(meta, gameProfile);
            }
        } catch (Exception e) {
            if (skullState.hasOwner() && skullState.getOwningPlayer() != null) {
                meta.setOwningPlayer(skullState.getOwningPlayer());
            }
        }

        applyDisplayAndLore(meta, displayName, lore);
        head.setItemMeta(meta);
        return head;
    }

    @SuppressWarnings("deprecation")
    public static String getOwnerNameFromSkullBlock(Block block) {
        if (block == null || !(block.getState() instanceof Skull)) return null;
        Skull skullState = (Skull) block.getState();
        try {
            Field tileField = skullState.getClass().getDeclaredField("skull");
            tileField.setAccessible(true);
            Object tileEntitySkull = tileField.get(skullState);
            Method getGameProfile = tileEntitySkull.getClass().getMethod("getGameProfile");
            Object gameProfile = getGameProfile.invoke(tileEntitySkull);
            if (gameProfile != null) {
                Method getName = gameProfile.getClass().getMethod("getName");
                Object nameObj = getName.invoke(gameProfile);
                if (nameObj != null && !nameObj.toString().isEmpty()) {
                    return nameObj.toString();
                }
            }
        } catch (Exception ignored) {}
        return skullState.hasOwner() ? skullState.getOwner() : null;
    }
}