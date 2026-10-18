package me.xmpl.lib.util;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

public final class BossBarUtil {
    private BossBarUtil() {}

    public static BossBar createBar(String title, String colorName, String styleName) {
        BarColor color;
        try {
            color = BarColor.valueOf(colorName.toUpperCase());
        } catch (Exception e) {
            color = BarColor.RED;
        }

        BarStyle style;
        try {
            style = BarStyle.valueOf(styleName.toUpperCase());
        } catch (Exception e) {
            style = BarStyle.SOLID; // Сплошная полоса без разделений
        }

        BossBar bar = Bukkit.createBossBar(ChatUtil.color(title), color, style);
        bar.setProgress(1.0);
        return bar;
    }

    public static void updateBar(BossBar bar, String title, double progress) {
        if (bar == null) return;
        bar.setTitle(ChatUtil.color(title));
        double clamped = Math.max(0.0, Math.min(1.0, progress));
        bar.setProgress(clamped);
    }

    public static void removePlayer(BossBar bar, Player player) {
        if (bar != null && player != null) {
            bar.removePlayer(player);
        }
    }

    public static void clearBar(BossBar bar) {
        if (bar != null) {
            bar.removeAll();
        }
    }
}