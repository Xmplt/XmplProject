package me.xmpl.ess.service;

import me.xmpl.ess.XmplEssExtension;
import me.xmpl.lib.hook.VaultHook;
import me.xmpl.lib.hook.WorldGuardHook;
import me.xmpl.lib.util.ChatUtil;
import me.xmpl.lib.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class RtpService {
    private final XmplEssExtension extension;

    public static class RtpGuiHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    public RtpService(XmplEssExtension extension) {
        this.extension = extension;
    }

    public void openRtpMenu(Player player) {
        FileConfiguration cfg = extension.getCustomConfig("commands/rtp.yml").get();
        String title = ChatUtil.color(cfg.getString("gui.title", "&8Рандомная телепортация"));
        Inventory gui = Bukkit.createInventory(new RtpGuiHolder(), cfg.getInt("gui.size", 27), title);

        // Кнопка 1: Обычный телепорт (слева по центру, слот 11)
        int normalSlot = cfg.getInt("gui.normal-button.slot", 11);
        Material normalMat = Material.matchMaterial(cfg.getString("gui.normal-button.material", "COMPASS"));
        gui.setItem(normalSlot, new ItemBuilder(normalMat != null ? normalMat : Material.COMPASS)
                .setName(cfg.getString("gui.normal-button.name"))
                .setLore(cfg.getStringList("gui.normal-button.lore"))
                .build());

        // Кнопка 2: Телепорт ближе к игрокам (справа по центру, слот 15, Око Эндера)
        int playerSlot = cfg.getInt("gui.player-button.slot", 15);
        Material playerMat = Material.matchMaterial(cfg.getString("gui.player-button.material", "EYE_OF_ENDER"));
        String perm = cfg.getString("permissions.near-players", "xmpless.rtp.players");
        boolean hasPerm = player.hasPermission(perm);

        List<String> lore = hasPerm
                ? cfg.getStringList("gui.player-button.lore-has-perm")
                : cfg.getStringList("gui.player-button.lore-no-perm");

        gui.setItem(playerSlot, new ItemBuilder(playerMat != null ? playerMat : Material.EYE_OF_ENDER)
                .setName(cfg.getString("gui.player-button.name"))
                .setLore(lore)
                .setGlowing(hasPerm)
                .build());

        player.openInventory(gui);
    }

    public void performNormalRtp(Player player) {
        FileConfiguration cfg = extension.getCustomConfig("commands/rtp.yml").get();
        ChatUtil.sendMessage(player, cfg.getString("messages.searching-location"));

        World world = player.getWorld();
        String spawnRegion = cfg.getString("settings.spawn-region-name", "spawn");
        Location spawnCenter = WorldGuardHook.getRegionCenter(world, spawnRegion);

        int minDist = cfg.getInt("settings.normal-rtp.min-distance-from-spawn", 150);
        int maxDist = cfg.getInt("settings.normal-rtp.max-distance-from-spawn", 900);

        for (int attempt = 0; attempt < 35; attempt++) {
            double angle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
            double dist = ThreadLocalRandom.current().nextDouble(minDist, maxDist);

            int x = (int) (spawnCenter.getX() + Math.cos(angle) * dist);
            int z = (int) (spawnCenter.getZ() + Math.sin(angle) * dist);

            Location safeLoc = findSurfaceSafeLocation(world, x, z);
            if (safeLoc != null) {
                player.teleport(safeLoc);
                ChatUtil.sendMessage(player, cfg.getString("messages.teleport-normal-success"),
                        "{x}", safeLoc.getBlockX(), "{y}", safeLoc.getBlockY(), "{z}", safeLoc.getBlockZ());
                return;
            }
        }

        ChatUtil.sendMessage(player, cfg.getString("messages.search-failed"));
    }

    public void performPlayerRtp(Player player) {
        FileConfiguration cfg = extension.getCustomConfig("commands/rtp.yml").get();
        String perm = cfg.getString("permissions.near-players", "xmpless.rtp.players");
        if (!player.hasPermission(perm)) {
            ChatUtil.sendMessage(player, cfg.getString("messages.no-player-rtp-perm"));
            return;
        }

        double cost = cfg.getDouble("settings.player-rtp.cost", 3000.0);
        if (!VaultHook.hasMoney(player, cost)) {
            ChatUtil.sendMessage(player, cfg.getString("messages.not-enough-money"), "{cost}", (int) cost);
            return;
        }

        World world = player.getWorld();
        String spawnRegion = cfg.getString("settings.spawn-region-name", "spawn");
        Location spawnCenter = WorldGuardHook.getRegionCenter(world, spawnRegion);
        double maxSpawnDist = cfg.getDouble("settings.player-rtp.max-distance-from-spawn", 2500.0);

        // Ищем подходящих игроков (не в ванише, в этом же мире, не дальше 2500 блоков от спавна)
        List<Player> candidates = new ArrayList<>();
        for (Player target : world.getPlayers()) {
            if (target.equals(player)) continue;
            if (extension.getVanishService().isVanished(target)) continue;
            if (target.getLocation().distance(spawnCenter) <= maxSpawnDist) {
                candidates.add(target);
            }
        }

        if (candidates.isEmpty()) {
            ChatUtil.sendMessage(player, cfg.getString("messages.no-targets-online"));
            return;
        }

        Collections.shuffle(candidates);
        int minOffset = cfg.getInt("settings.player-rtp.min-distance-from-target", 280);
        int maxOffset = cfg.getInt("settings.player-rtp.max-distance-from-target", 285);

        for (Player target : candidates) {
            Location tLoc = target.getLocation();
            for (int attempt = 0; attempt < 25; attempt++) {
                double angle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
                double dist = ThreadLocalRandom.current().nextDouble(minOffset, maxOffset);

                int x = (int) (tLoc.getX() + Math.cos(angle) * dist);
                int z = (int) (tLoc.getZ() + Math.sin(angle) * dist);

                Location safeLoc = findSurfaceSafeLocation(world, x, z);
                if (safeLoc != null && safeLoc.distance(spawnCenter) <= maxSpawnDist) {
                    if (!VaultHook.withdrawMoney(player, cost)) {
                        ChatUtil.sendMessage(player, cfg.getString("messages.not-enough-money"), "{cost}", (int) cost);
                        return;
                    }
                    player.teleport(safeLoc);
                    ChatUtil.sendMessage(player, cfg.getString("messages.teleport-player-success"), "{cost}", (int) cost);
                    return;
                }
            }
        }

        ChatUtil.sendMessage(player, cfg.getString("messages.search-failed"));
    }

    /**
     * Гарантирует телепортацию СТРОГО на поверхность под открытым небом (без шахт/пещер, без воды/лавы и без чужих регионов!)
     */
    private Location findSurfaceSafeLocation(World world, int x, int z) {
        int highestY = world.getHighestBlockYAt(x, z);
        if (highestY <= 55 || highestY >= 250) return null; // Защита от провалов в глубокие каньоны/шахты

        Block ground = world.getBlockAt(x, highestY - 1, z);
        Block feet = world.getBlockAt(x, highestY, z);
        Block head = world.getBlockAt(x, highestY + 1, z);

        Material gType = ground.getType();
        if (!gType.isSolid() || gType == Material.WATER || gType == Material.STATIONARY_WATER
                || gType == Material.LAVA || gType == Material.STATIONARY_LAVA
                || gType == Material.CACTUS || gType == Material.MAGMA) {
            return null;
        }

        if (feet.getType() != Material.AIR || head.getType() != Material.AIR) {
            return null;
        }

        // Проверяем, что над головой реально чистое небо (не пещера и не шахта)
        if (head.getLightFromSky() < 15) {
            return null;
        }

        Location candidate = new Location(world, x + 0.5, highestY, z + 0.5);
        if (!WorldGuardHook.isLocationFreeOfRegions(candidate)) {
            return null;
        }

        return candidate;
    }
}