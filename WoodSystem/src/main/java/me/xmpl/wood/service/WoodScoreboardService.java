package me.xmpl.wood.service;

import me.xmpl.lib.util.ChatUtil;
import me.xmpl.lib.util.ScoreboardUtil;
import me.xmpl.wood.WoodSystemExtension;
import me.xmpl.wood.model.PlayerWoodBag;
import me.xmpl.wood.model.WoodTreeType;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class WoodScoreboardService {
    private final WoodSystemExtension extension;

    public WoodScoreboardService(WoodSystemExtension extension) {
        this.extension = extension;
    }

    public void startRegionCheckTask() {
        extension.runTaskTimer(() -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                updatePlayerScoreboard(player);
            }
        }, 15L, 15L);
    }

    public void updatePlayerScoreboard(Player player) {
        if (player == null || !player.isOnline()) return;

        FileConfiguration sbCfg = extension.getCustomConfig("scoreboard.yml").get();
        if (!sbCfg.getBoolean("enabled", true)) {
            if (ScoreboardUtil.hasCustomBoardActive(player)) {
                ScoreboardUtil.restorePreviousBoard(player);
            }
            return;
        }

        boolean inWoodRegion = extension.getRegionStorage().isInAnyWoodRegion(player.getLocation());
        if (!inWoodRegion) {
            if (ScoreboardUtil.hasCustomBoardActive(player)) {
                ScoreboardUtil.restorePreviousBoard(player);
            }
            return;
        }

        PlayerWoodBag bag = extension.getBagStorage().getBag(player.getName());
        String title = sbCfg.getString("title", "&2&l🌲 ЛЕСОПИЛКА 🌲");
        List<String> rawLines = sbCfg.getStringList("lines");
        List<String> formattedLines = new ArrayList<>();

        for (String line : rawLines) {
            formattedLines.add(ChatUtil.applyReplacements(line,
                    "{oak}", bag.getAmount(WoodTreeType.OAK),
                    "{birch}", bag.getAmount(WoodTreeType.BIRCH),
                    "{spruce}", bag.getAmount(WoodTreeType.SPRUCE),
                    "{jungle}", bag.getAmount(WoodTreeType.JUNGLE),
                    "{acacia}", bag.getAmount(WoodTreeType.ACACIA),
                    "{dark_oak}", bag.getAmount(WoodTreeType.DARK_OAK),
                    "{total}", bag.getTotalTrees()));
        }

        ScoreboardUtil.showCustomSidebar(player, title, formattedLines);
    }

    public void restoreAllBoards() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (ScoreboardUtil.hasCustomBoardActive(player)) {
                ScoreboardUtil.restorePreviousBoard(player);
            }
        }
    }
}