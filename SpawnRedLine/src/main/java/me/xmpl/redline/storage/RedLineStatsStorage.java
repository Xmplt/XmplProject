package me.xmpl.redline.storage;

import me.xmpl.lib.database.DatabaseProvider;
import me.xmpl.redline.SpawnRedLineExtension;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RedLineStatsStorage {
    private final SpawnRedLineExtension extension;
    private boolean enabled;
    // [0] = выходов за линию, [1] = убийств помеченных игроков внутри спавна
    private final Map<String, int[]> cache = new ConcurrentHashMap<>();

    public RedLineStatsStorage(SpawnRedLineExtension extension) {
        this.extension = extension;
        reload();
    }

    public void reload() {
        this.enabled = extension.getCustomConfig("databases.yml").get().getBoolean("enable-stats-database", true);
        if (!enabled) return;

        DatabaseProvider db = extension.getDatabase("default");
        db.executeUpdate("CREATE TABLE IF NOT EXISTS redline_stats (" +
                "player_key VARCHAR(32) PRIMARY KEY, " +
                "player_name VARCHAR(32), " +
                "line_crossings INT DEFAULT 0, " +
                "spawn_marked_kills INT DEFAULT 0" +
                ");");

        cache.clear();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM redline_stats");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                cache.put(rs.getString("player_key").toLowerCase(), new int[]{
                        rs.getInt("line_crossings"),
                        rs.getInt("spawn_marked_kills")
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void incrementCrossings(String playerName) {
        if (!enabled) return;
        int[] st = cache.computeIfAbsent(playerName.toLowerCase(), k -> new int[]{0, 0});
        st[0]++;
        saveAsync(playerName, st);
    }

    public void incrementSpawnMarkedKills(String playerName) {
        if (!enabled) return;
        int[] st = cache.computeIfAbsent(playerName.toLowerCase(), k -> new int[]{0, 0});
        st[1]++;
        saveAsync(playerName, st);
    }

    private void saveAsync(String playerName, int[] st) {
        String key = playerName.toLowerCase();
        int c = st[0];
        int k = st[1];
        extension.runTaskAsync(() -> {
            DatabaseProvider db = extension.getDatabase("default");
            db.executeUpdate("DELETE FROM redline_stats WHERE player_key = ?", key);
            db.executeUpdate("INSERT INTO redline_stats (player_key, player_name, line_crossings, spawn_marked_kills) VALUES (?, ?, ?, ?)",
                    key, playerName, c, k);
        });
    }

    public int[] getStats(String playerName) {
        return cache.getOrDefault(playerName.toLowerCase(), new int[]{0, 0});
    }
}