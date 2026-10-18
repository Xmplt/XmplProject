package me.xmpl.lib.util;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public final class BlockAndGlowUtil {
    private static final String WHITE_GLOW_TEAM = "XMPL_WHITE_GLOW";

    private BlockAndGlowUtil() {}

    /**
     * Проигрывает пакетную анимацию открытия (open = true) или закрытия (open = false) крышки сундука
     */
    public static void playChestLidAnimation(Block chestBlock, boolean open) {
        if (chestBlock == null || chestBlock.getWorld() == null) return;
        try {
            Location loc = chestBlock.getLocation();
            String version = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
            Class<?> blockPositionClass = Class.forName("net.minecraft.server." + version + ".BlockPosition");
            Class<?> blockClass = Class.forName("net.minecraft.server." + version + ".Block");
            Class<?> worldClass = Class.forName("net.minecraft.server." + version + ".World");
            Class<?> craftWorldClass = Class.forName("org.bukkit.craftbukkit." + version + ".CraftWorld");
            Class<?> magicNumbers = Class.forName("org.bukkit.craftbukkit." + version + ".util.CraftMagicNumbers");

            Constructor<?> posConstructor = blockPositionClass.getConstructor(int.class, int.class, int.class);
            Object pos = posConstructor.newInstance(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());

            Object craftWorld = craftWorldClass.cast(loc.getWorld());
            Object nmsWorld = craftWorldClass.getMethod("getHandle").invoke(craftWorld);

            Method getBlockMethod = magicNumbers.getMethod("getBlock", org.bukkit.Material.class);
            Object nmsBlock = getBlockMethod.invoke(null, chestBlock.getType());

            Method playAction = worldClass.getMethod("playBlockAction", blockPositionClass, blockClass, int.class, int.class);
            playAction.invoke(nmsWorld, pos, nmsBlock, 1, open ? 1 : 0);
        } catch (Exception ignored) {}
    }

    /**
     * Включает на сущности (например вылетевшем предмете) ярко-белое свечение, видимое сквозь блоки
     */
    public static void applyWhiteGlow(Entity entity) {
        if (entity == null) return;
        entity.setGlowing(true);
        if (Bukkit.getScoreboardManager() == null) return;

        Scoreboard mainBoard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = mainBoard.getTeam(WHITE_GLOW_TEAM);
        if (team == null) {
            team = mainBoard.registerNewTeam(WHITE_GLOW_TEAM);
            team.setPrefix(ChatColor.WHITE.toString());
        }
        String entry = entity.getUniqueId().toString();
        if (!team.hasEntry(entry)) {
            team.addEntry(entry);
        }
    }
}