package me.xmpl.shield.storage;

import me.xmpl.lib.database.DatabaseProvider;
import me.xmpl.shield.XmplShieldExtension;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ShieldStatsStorage {
    private final XmplShieldExtension extension;
    private boolean enabled;
    private final Map<String, int[]> statsCache = new ConcurrentHashMap<>(); // [0] = broken_others, [1] = lost_own

    public ShieldStatsStorage(XmplShieldExtension extension) {
        this.extension = extension;
        reload();
    }

    public void reload() {
        this.enabled = extension.getCustomConfig("databases.yml").get().getBoolean("enable-stats-database", true);
        if (!enabled) return;

        DatabaseProvider db = extension.getDatabase("default");
        db.executeUpdate("CREATE TABLE IF NOT EXISTS xmpl_shield_stats (" +
                "player_key VARCHAR(32) PRIMARY KEY, " +
                "player_name VARCHAR(32), " +
                "broken_others INT DEFAULT 0, " +
                "lost_own INT DEFAULT 0" +
                ");");

        statsCache.clear();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM xmpl_shield_stats");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                statsCache.put(rs.getString("player_key").toLowerCase(),
                        new int[]{rs.getInt("broken_others"), rs.getInt("lost_own")});
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void recordShieldBreak(String attackerName, String victimName) {
        if (!enabled) return;
        if (attackerName != null) {
            int[] att = statsCache.computeIfAbsent(attackerName.toLowerCase(), k -> new int[]{0, 0});
            att[0]++;
            savePlayerAsync(attackerName, att[0], att[1]);
        }
        if (victimName != null) {
            int[] vic = statsCache.computeIfAbsent(victimName.toLowerCase(), k -> new int[]{0, 0});
            vic[1]++;
            savePlayerAsync(victimName, vic[0], vic[1]);
        }
    }

    private void savePlayerAsync(String playerName, int brokenOthers, int lostOwn) {
        String key = playerName.toLowerCase();
        extension.runTaskAsync(() -> {
            DatabaseProvider db = extension.getDatabase("default");
            db.executeUpdate("DELETE FROM xmpl_shield_stats WHERE player_key = ?", key);
            db.executeUpdate("INSERT INTO xmpl_shield_stats (player_key, player_name, broken_others, lost_own) VALUES (?, ?, ?, ?)",
                    key, playerName, brokenOthers, lostOwn);
        });
    }

    public int getBrokenOthers(String playerName) {
        int[] data = statsCache.get(playerName.toLowerCase());
        return data != null ? data[0] : 0;
    }

    public int getLostOwn(String playerName) {
        int[] data = statsCache.get(playerName.toLowerCase());
        return data != null ? data[1] : 0;
    }
}