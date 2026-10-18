package me.xmpl.wood.service;

import me.xmpl.lib.util.ActionBarUtil;
import me.xmpl.lib.util.ChatUtil;
import me.xmpl.lib.util.RandomUtil;
import me.xmpl.lib.util.SoundParticleUtil;
import me.xmpl.lib.util.TreeStructureScanner;
import me.xmpl.lib.util.TreeStructureScanner.SavedBlockState;
import me.xmpl.lib.util.TreeStructureScanner.TreeSnapshot;
import me.xmpl.wood.WoodSystemExtension;
import me.xmpl.wood.model.WoodTreeType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TreeChopAndRegenService {
    private static class ChopProgress {
        int currentHits;
        final int requiredHits;

        ChopProgress(int requiredHits) {
            this.currentHits = 0;
            this.requiredHits = requiredHits;
        }
    }

    private final WoodSystemExtension extension;
    private final Map<Location, ChopProgress> hitCounters = new ConcurrentHashMap<>();
    private final Map<Location, TreeSnapshot> regeneratingSaplings = new ConcurrentHashMap<>();

    public TreeChopAndRegenService(WoodSystemExtension extension) {
        this.extension = extension;
    }

    public boolean isProtectedSapling(Location loc) {
        return regeneratingSaplings.containsKey(loc);
    }

    @SuppressWarnings("deprecation")
    public void handleBaseLogStrike(Player player, Block baseBlock) {
        FileConfiguration cfg = extension.getConfig();
        FileConfiguration cmdCfg = extension.getCustomConfig("commands/wood.yml").get();

        int maxLimit = cfg.getInt("chopping.max-trees-limit", 250);
        int currentTotal = extension.getBagStorage().getBag(player.getName()).getTotalTrees();
        if (currentTotal >= maxLimit) {
            ChatUtil.sendMessage(player, cmdCfg.getString("messages.limit-reached"));
            return;
        }

        TreeSnapshot snapshot = TreeStructureScanner.scanFullTree(baseBlock, 120, 4);
        if (snapshot == null) {
            ChatUtil.sendMessage(player, cmdCfg.getString("messages.not-a-full-tree"));
            return;
        }

        WoodTreeType treeType = WoodTreeType.fromSaplingData(snapshot.saplingData);
        String treeDisplayName = extension.getCustomConfig("trees.yml").get()
                .getString("trees." + treeType.name() + ".display-name", treeType.name());

        Location baseLoc = baseBlock.getLocation();
        int minHits = cfg.getInt("chopping.min-hits-required", 4);
        int maxHits = cfg.getInt("chopping.max-hits-required", 6);

        ChopProgress progress = hitCounters.computeIfAbsent(baseLoc,
                k -> new ChopProgress(RandomUtil.nextInt(minHits, maxHits)));

        progress.currentHits++;
        int remaining = Math.max(0, progress.requiredHits - progress.currentHits);

        // Если дерево еще не срублено — пишем в чат белым цветом, а цифру жирным оранжевым: "Осталось срубить: 5"
        if (remaining > 0) {
            if (cfg.getBoolean("chopping.send-hit-progress-chat", true)) {
                String chatFmt = cfg.getString("chopping.hit-progress-chat", "&fОсталось срубить: &6&l{remaining}");
                ChatUtil.sendMessage(player, chatFmt,
                        "{remaining}", remaining,
                        "{current}", progress.currentHits,
                        "{required}", progress.requiredHits);
            }
            if (cfg.getBoolean("chopping.show-hit-progress-actionbar", false)) {
                String ab = ChatUtil.applyReplacements(cfg.getString("chopping.hit-progress-actionbar", "&fОсталось срубить: &6&l{remaining}"),
                        "{remaining}", remaining);
                ActionBarUtil.sendActionBar(player, ab);
            }
            SoundParticleUtil.playSoundInRadius(baseLoc, cfg.getString("chopping.hit-sound", "BLOCK_WOOD_HIT"), 10.0, 1.0f, 1.0f);
            return;
        }

        // Последний удар нанесен — срубаем всё дерево целиком!
        hitCounters.remove(baseLoc);
        SoundParticleUtil.playSoundInRadius(baseLoc, cfg.getString("chopping.fell-sound", "BLOCK_WOOD_BREAK"), 14.0, 1.2f, 0.85f);

        for (SavedBlockState state : snapshot.allBlocks) {
            state.location.getBlock().setType(Material.AIR, false);
        }

        baseBlock.setType(Material.SAPLING, false);
        baseBlock.setData(snapshot.saplingData, false);
        regeneratingSaplings.put(baseLoc, snapshot);

        extension.getEconomyService().addChoppedTree(player, treeType, treeDisplayName);

        int respawnSeconds = cfg.getInt("chopping.sapling-respawn-seconds", 10);
        Bukkit.getScheduler().runTaskLater(extension.getCore(), () -> {
            TreeSnapshot saved = regeneratingSaplings.remove(baseLoc);
            if (saved != null) {
                for (SavedBlockState state : saved.allBlocks) {
                    state.restore();
                }
            }
        }, respawnSeconds * 20L);
    }

    public void forceRestoreAllTrees() {
        for (TreeSnapshot snapshot : regeneratingSaplings.values()) {
            for (SavedBlockState state : snapshot.allBlocks) {
                state.restore();
            }
        }
        regeneratingSaplings.clear();
        hitCounters.clear();
    }
}