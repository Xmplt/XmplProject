package me.xmpl.killsystem.service;

import me.xmpl.killsystem.KillSystemExtension;
import me.xmpl.killsystem.model.PlayerKillRecord;
import me.xmpl.killsystem.model.TopSignEntry;
import me.xmpl.lib.util.ChatUtil;
import me.xmpl.lib.util.SignBlockUtil;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

public class TopSignUpdaterService {
    private final KillSystemExtension extension;

    public TopSignUpdaterService(KillSystemExtension extension) {
        this.extension = extension;
    }

    public void startAutoUpdateTask() {
        int seconds = Math.max(10, extension.getConfig().getInt("settings.sign-update-interval-seconds", 300));
        long ticks = seconds * 20L;
        extension.runTaskTimer(this::updateAllSigns, 40L, ticks);
    }

    public int updateAllSigns() {
        List<PlayerKillRecord> sorted = extension.getDatabaseStorage().getSortedTopList();
        int count = 0;
        for (TopSignEntry signEntry : extension.getSignStorage().getAllSigns()) {
            if (updateSingleSign(signEntry, sorted)) {
                count++;
            }
        }
        return count;
    }

    public boolean updateSingleSign(TopSignEntry signEntry, List<PlayerKillRecord> sortedList) {
        Location loc = signEntry.toLocation();
        if (loc == null) return false;

        int rank = signEntry.getRank();
        int index = rank - 1;
        FileConfiguration fmtCfg = extension.getCustomConfig("signs-format.yml").get();

        if (index >= 0 && index < sortedList.size()) {
            PlayerKillRecord rec = sortedList.get(index);
            String l1 = formatLine(fmtCfg.getString("active-sign.line-1", "&0{player}"), rank, rec.getPlayerName(), rec.getKills(), rec.getDeaths());
            String l2 = formatLine(fmtCfg.getString("active-sign.line-2", "&0Убийств: &a{kills}"), rank, rec.getPlayerName(), rec.getKills(), rec.getDeaths());
            String l3 = formatLine(fmtCfg.getString("active-sign.line-3", "&0Смертей: &c{deaths}"), rank, rec.getPlayerName(), rec.getKills(), rec.getDeaths());
            String l4 = formatLine(fmtCfg.getString("active-sign.line-4", ""), rank, rec.getPlayerName(), rec.getKills(), rec.getDeaths());
            return SignBlockUtil.updateSignLines(loc, l1, l2, l3, l4);
        } else {
            String l1 = formatLine(fmtCfg.getString("empty-sign.line-1", "&0Пусто #{rank}"), rank, "-", 0, 0);
            String l2 = formatLine(fmtCfg.getString("empty-sign.line-2", "&0Убийств: &a0"), rank, "-", 0, 0);
            String l3 = formatLine(fmtCfg.getString("empty-sign.line-3", "&0Смертей: &c0"), rank, "-", 0, 0);
            String l4 = formatLine(fmtCfg.getString("empty-sign.line-4", ""), rank, "-", 0, 0);
            return SignBlockUtil.updateSignLines(loc, l1, l2, l3, l4);
        }
    }

    private String formatLine(String raw, int rank, String player, int kills, int deaths) {
        if (raw == null) return "";
        return ChatUtil.applyReplacements(raw,
                "{rank}", rank,
                "{player}", player,
                "{kills}", kills,
                "{deaths}", deaths);
    }
}