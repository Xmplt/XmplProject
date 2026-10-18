package me.xmpl.antirelog.service;

import me.xmpl.antirelog.AntiRelogExtension;
import me.xmpl.antirelog.model.CombatSession;
import me.xmpl.lib.util.BossBarUtil;
import me.xmpl.lib.util.ChatUtil;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public class CombatBossBarService {
    private final AntiRelogExtension extension;

    public CombatBossBarService(AntiRelogExtension extension) {
        this.extension = extension;
    }

    public BossBar createForPlayer(Player player, String opponentName, int seconds) {
        FileConfiguration cfg = extension.getCustomConfig("bossbar.yml").get();
        if (!cfg.getBoolean("enabled", true)) return null;

        String rawTitle = cfg.getString("title", "&c&lРЕЖИМ ПВП &8| &fДо окончания: &e%time% сек.");
        String formattedTitle = ChatUtil.applyReplacements(rawTitle,
                "%time%", seconds,
                "%opponent%", opponentName);

        String color = cfg.getString("color", "RED");
        String style = cfg.getString("style", "SOLID");

        BossBar bar = BossBarUtil.createBar(formattedTitle, color, style);
        bar.addPlayer(player);
        return bar;
    }

    public void updateBar(CombatSession session) {
        BossBar bar = session.getBossBar();
        if (bar == null) return;

        FileConfiguration cfg = extension.getCustomConfig("bossbar.yml").get();
        String rawTitle = cfg.getString("title", "&c&lРЕЖИМ ПВП &8| &fДо окончания: &e%time% сек.");
        String formattedTitle = ChatUtil.applyReplacements(rawTitle,
                "%time%", session.getRemainingSeconds(),
                "%opponent%", session.getOpponentName());

        BossBarUtil.updateBar(bar, formattedTitle, session.getProgressRatio());
    }

    public void removeBar(CombatSession session) {
        if (session != null && session.getBossBar() != null) {
            BossBarUtil.clearBar(session.getBossBar());
            session.setBossBar(null);
        }
    }
}