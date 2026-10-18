package me.xmpl.mine.service;

import me.xmpl.lib.util.ChatUtil;
import me.xmpl.lib.util.ScoreboardUtil;
import me.xmpl.mine.MineSystemExtension;
import me.xmpl.mine.model.MineOreType;
import me.xmpl.mine.model.PlayerOreBag;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class MineScoreboardService {
    private static final String BOARD_OWNER = "me.xmpl.mine";
    private final MineSystemExtension extension;

    public MineScoreboardService(MineSystemExtension extension) {
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
            if (ScoreboardUtil.hasCustomBoardActive(player, BOARD_OWNER)) {
                ScoreboardUtil.restorePreviousBoard(player, BOARD_OWNER);
            }
            return;
        }

        boolean inMine = extension.getRegionStorage().isPlayerInAnyMineRegion(player.getLocation());
        if (!inMine) {
            if (ScoreboardUtil.hasCustomBoardActive(player, BOARD_OWNER)) {
                ScoreboardUtil.restorePreviousBoard(player, BOARD_OWNER);
            }
            return;
        }

        PlayerOreBag bag = extension.getBagStorage().getBag(player.getName());
        String title = sbCfg.getString("title", "&6&lШАХТА");
        List<String> rawLines = sbCfg.getStringList("lines");
        List<String> formattedLines = new ArrayList<>();

        for (String line : rawLines) {
            formattedLines.add(ChatUtil.applyReplacements(line,
                    "{coal}", bag.getAmount(MineOreType.COAL),
                    "{iron}", bag.getAmount(MineOreType.IRON),
                    "{gold}", bag.getAmount(MineOreType.GOLD),
                    "{redstone}", bag.getAmount(MineOreType.REDSTONE),
                    "{lapis}", bag.getAmount(MineOreType.LAPIS),
                    "{quartz}", bag.getAmount(MineOreType.QUARTZ),
                    "{diamond}", bag.getAmount(MineOreType.DIAMOND),
                    "{emerald}", bag.getAmount(MineOreType.EMERALD)));
        }

        ScoreboardUtil.showCustomSidebar(player, BOARD_OWNER, title, formattedLines);
    }

    public void restoreAllBoards() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (ScoreboardUtil.hasCustomBoardActive(player, BOARD_OWNER)) {
                ScoreboardUtil.restorePreviousBoard(player, BOARD_OWNER);
            }
        }
    }
}