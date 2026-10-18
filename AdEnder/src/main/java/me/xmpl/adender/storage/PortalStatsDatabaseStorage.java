package me.xmpl.adender.storage;

import me.xmpl.adender.AdEnderExtension;
import me.xmpl.adender.model.PortalDestinationType;
import me.xmpl.lib.database.DatabaseProvider;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PortalStatsDatabaseStorage {
    private final AdEnderExtension extension;
    private boolean enabled;
    // [0] = телепортов в Ад, [1] = телепортов в Энд
    private final Map<String, int[]> cache = new ConcurrentHashMap<>();

    public PortalStatsDatabaseStorage(AdEnderExtension extension) {
        this.extension = extension;
        reload();
    }

    public void reload() {
        this.enabled = extension.getCustomConfig("databases.yml").get().getBoolean("enable-stats-database", true);
        if (!enabled) return;

        DatabaseProvider db = extension.getDatabase("default");
        db.executeUpdate("CREATE TABLE IF NOT EXISTS adender_stats (" +
                "player_key VARCHAR(32) PRIMARY KEY, " +
                "player_name VARCHAR(32), " +
                "ad_teleports INT DEFAULT 0, " +
                "ender_teleports INT DEFAULT 0" +
                ");");

        cache.clear();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM adender_stats");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                cache.put(rs.getString("player_key").toLowerCase(), new int[]{
                        rs.getInt("ad_teleports"),
                        rs.getInt("ender_teleports")
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void recordTeleport(String playerName, PortalDestinationType type) {
        if (!enabled) return;
        int[] st = cache.computeIfAbsent(playerName.toLowerCase(), k -> new int[]{0, 0});
        if (type == PortalDestinationType.AD) st[0]++;
        else st[1]++;

        String key = playerName.toLowerCase();
        int adCount = st[0];
        int enderCount = st[1];
        extension.runTaskAsync(() -> {
            DatabaseProvider db = extension.getDatabase("default");
            db.executeUpdate("DELETE FROM adender_stats WHERE player_key = ?", key);
            db.executeUpdate("INSERT INTO adender_stats (player_key, player_name, ad_teleports, ender_teleports) VALUES (?, ?, ?, ?)",
                    key, playerName, adCount, enderCount);
        });
    }

    public int[] getStats(String playerName) {
        return cache.getOrDefault(playerName.toLowerCase(), new int[]{0, 0});
    }
}