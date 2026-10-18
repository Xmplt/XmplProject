package me.xmpl.headdrop.storage;

import me.xmpl.headdrop.HeadDropExtension;
import me.xmpl.lib.database.DatabaseProvider;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HeadStatsStorage {
    private final HeadDropExtension extension;
    private boolean enabled;
    // [0] = выбил чужих голов, [1] = потерял своих голов
    private final Map<String, int[]> cache = new ConcurrentHashMap<>();

    public HeadStatsStorage(HeadDropExtension extension) {
        this.extension = extension;
        reload();
    }

    public void reload() {
        this.enabled = extension.getCustomConfig("databases.yml").get().getBoolean("enable-stats-database", true);
        if (!enabled) return;

        DatabaseProvider db = extension.getDatabase("default");
        db.executeUpdate("CREATE TABLE IF NOT EXISTS headdrop_stats (" +
                "player_key VARCHAR(32) PRIMARY KEY, " +
                "player_name VARCHAR(32), " +
                "heads_collected INT DEFAULT 0, " +
                "heads_lost INT DEFAULT 0" +
                ");");

        cache.clear();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM headdrop_stats");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                cache.put(rs.getString("player_key").toLowerCase(), new int[]{
                        rs.getInt("heads_collected"),
                        rs.getInt("heads_lost")
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void recordHeadDrop(String killerName, String victimName) {
        if (!enabled) return;
        int[] kStats = cache.computeIfAbsent(killerName.toLowerCase(), k -> new int[]{0, 0});
        kStats[0]++;
        saveAsync(killerName, kStats);

        int[] vStats = cache.computeIfAbsent(victimName.toLowerCase(), k -> new int[]{0, 0});
        vStats[1]++;
        saveAsync(victimName, vStats);
    }

    private void saveAsync(String playerName, int[] stats) {
        String key = playerName.toLowerCase();
        extension.runTaskAsync(() -> {
            DatabaseProvider db = extension.getDatabase("default");
            db.executeUpdate("DELETE FROM headdrop_stats WHERE player_key = ?", key);
            db.executeUpdate("INSERT INTO headdrop_stats (player_key, player_name, heads_collected, heads_lost) VALUES (?, ?, ?, ?)",
                    key, playerName, stats[0], stats[1]);
        });
    }

    public int[] getStats(String playerName) {
        return cache.getOrDefault(playerName.toLowerCase(), new int[]{0, 0});
    }
}