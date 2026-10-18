package me.xmpl.fishing.storage;

import me.xmpl.fishing.FishingSystemExtension;
import me.xmpl.lib.database.DatabaseProvider;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FishingStatsStorage {
    public static class PlayerFishStats {
        public int fishSold;
        public long moneyEarned;
        public double maxWeightRecord;
    }

    private final FishingSystemExtension extension;
    private boolean enabled;
    private final Map<String, PlayerFishStats> cache = new ConcurrentHashMap<>();

    public FishingStatsStorage(FishingSystemExtension extension) {
        this.extension = extension;
        reload();
    }

    public void reload() {
        this.enabled = extension.getCustomConfig("databases.yml").get().getBoolean("enable-stats-database", true);
        if (!enabled) return;

        DatabaseProvider db = extension.getDatabase("default");
        db.executeUpdate("CREATE TABLE IF NOT EXISTS fishing_stats (" +
                "player_key VARCHAR(32) PRIMARY KEY, " +
                "player_name VARCHAR(32), " +
                "fish_sold INT DEFAULT 0, " +
                "money_earned BIGINT DEFAULT 0, " +
                "max_weight DOUBLE DEFAULT 0.0" +
                ");");

        cache.clear();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM fishing_stats");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                PlayerFishStats st = new PlayerFishStats();
                st.fishSold = rs.getInt("fish_sold");
                st.moneyEarned = rs.getLong("money_earned");
                st.maxWeightRecord = rs.getDouble("max_weight");
                cache.put(rs.getString("player_key").toLowerCase(), st);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void checkAndUpdateRecordWeight(String playerName, double weight) {
        if (!enabled) return;
        PlayerFishStats st = cache.computeIfAbsent(playerName.toLowerCase(), k -> new PlayerFishStats());
        if (weight > st.maxWeightRecord) {
            st.maxWeightRecord = weight;
            saveAsync(playerName, st);
        }
    }

    public void recordSale(String playerName, int count, long money) {
        if (!enabled) return;
        PlayerFishStats st = cache.computeIfAbsent(playerName.toLowerCase(), k -> new PlayerFishStats());
        st.fishSold += count;
        st.moneyEarned += money;
        saveAsync(playerName, st);
    }

    private void saveAsync(String playerName, PlayerFishStats st) {
        String key = playerName.toLowerCase();
        extension.runTaskAsync(() -> {
            DatabaseProvider db = extension.getDatabase("default");
            db.executeUpdate("DELETE FROM fishing_stats WHERE player_key = ?", key);
            db.executeUpdate("INSERT INTO fishing_stats (player_key, player_name, fish_sold, money_earned, max_weight) VALUES (?, ?, ?, ?, ?)",
                    key, playerName, st.fishSold, st.moneyEarned, st.maxWeightRecord);
        });
    }

    public PlayerFishStats getStats(String playerName) {
        return cache.getOrDefault(playerName.toLowerCase(), new PlayerFishStats());
    }
}