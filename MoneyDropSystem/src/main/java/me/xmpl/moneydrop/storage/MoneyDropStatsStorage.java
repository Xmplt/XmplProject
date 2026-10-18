package me.xmpl.moneydrop.storage;

import me.xmpl.lib.database.DatabaseProvider;
import me.xmpl.moneydrop.MoneyDropExtension;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MoneyDropStatsStorage {
    private final MoneyDropExtension extension;
    private boolean enabled;
    // [0] = mob_earned, [1] = pvp_stolen, [2] = pvp_lost
    private final Map<String, long[]> cache = new ConcurrentHashMap<>();

    public MoneyDropStatsStorage(MoneyDropExtension extension) {
        this.extension = extension;
        reload();
    }

    public void reload() {
        this.enabled = extension.getCustomConfig("databases.yml").get().getBoolean("enable-stats-database", true);
        if (!enabled) return;

        DatabaseProvider db = extension.getDatabase("default");
        db.executeUpdate("CREATE TABLE IF NOT EXISTS moneydrop_stats (" +
                "player_key VARCHAR(32) PRIMARY KEY, " +
                "player_name VARCHAR(32), " +
                "mob_earned BIGINT DEFAULT 0, " +
                "pvp_stolen BIGINT DEFAULT 0, " +
                "pvp_lost BIGINT DEFAULT 0" +
                ");");

        cache.clear();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM moneydrop_stats");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                cache.put(rs.getString("player_key").toLowerCase(), new long[]{
                        rs.getLong("mob_earned"),
                        rs.getLong("pvp_stolen"),
                        rs.getLong("pvp_lost")
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void addMobEarned(String playerName, long amount) {
        if (!enabled || amount <= 0) return;
        long[] stats = cache.computeIfAbsent(playerName.toLowerCase(), k -> new long[]{0L, 0L, 0L});
        stats[0] += amount;
        saveAsync(playerName, stats);
    }

    public void recordPvpSteal(String killerName, String victimName, long amount) {
        if (!enabled || amount <= 0) return;
        long[] kStats = cache.computeIfAbsent(killerName.toLowerCase(), k -> new long[]{0L, 0L, 0L});
        kStats[1] += amount;
        saveAsync(killerName, kStats);

        long[] vStats = cache.computeIfAbsent(victimName.toLowerCase(), k -> new long[]{0L, 0L, 0L});
        vStats[2] += amount;
        saveAsync(victimName, vStats);
    }

    private void saveAsync(String playerName, long[] stats) {
        String key = playerName.toLowerCase();
        extension.runTaskAsync(() -> {
            DatabaseProvider db = extension.getDatabase("default");
            db.executeUpdate("DELETE FROM moneydrop_stats WHERE player_key = ?", key);
            db.executeUpdate("INSERT INTO moneydrop_stats (player_key, player_name, mob_earned, pvp_stolen, pvp_lost) VALUES (?, ?, ?, ?, ?)",
                    key, playerName, stats[0], stats[1], stats[2]);
        });
    }

    public long[] getStats(String playerName) {
        return cache.getOrDefault(playerName.toLowerCase(), new long[]{0L, 0L, 0L});
    }
}