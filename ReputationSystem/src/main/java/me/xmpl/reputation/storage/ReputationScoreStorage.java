package me.xmpl.reputation.storage;

import me.xmpl.lib.database.DatabaseProvider;
import me.xmpl.reputation.ReputationSystemExtension;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ReputationScoreStorage {
    public static class PlayerRepEntry {
        public String realName;
        public int reputation;
        public String pendingOfflineNotice;

        public PlayerRepEntry(String realName, int reputation, String pendingOfflineNotice) {
            this.realName = realName;
            this.reputation = reputation;
            this.pendingOfflineNotice = pendingOfflineNotice;
        }
    }

    private final ReputationSystemExtension extension;
    private final Map<String, PlayerRepEntry> scores = new ConcurrentHashMap<>();

    public ReputationScoreStorage(ReputationSystemExtension extension) {
        this.extension = extension;
        initTable();
        loadAll();
    }

    private DatabaseProvider db() {
        return extension.getDatabase("reputation");
    }

    private void initTable() {
        db().executeUpdate("CREATE TABLE IF NOT EXISTS rep_scores (" +
                "player_key VARCHAR(32) PRIMARY KEY, " +
                "player_name VARCHAR(32), " +
                "reputation INT NOT NULL DEFAULT 0, " +
                "offline_notice TEXT" +
                ");");
    }

    public void loadAll() {
        scores.clear();
        try (Connection conn = db().getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM rep_scores");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                scores.put(rs.getString("player_key").toLowerCase(), new PlayerRepEntry(
                        rs.getString("player_name"),
                        rs.getInt("reputation"),
                        rs.getString("offline_notice")
                ));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void ensurePlayerRegistered(String playerName) {
        String key = playerName.toLowerCase();
        if (!scores.containsKey(key)) {
            PlayerRepEntry entry = new PlayerRepEntry(playerName, 0, null);
            scores.put(key, entry);
            saveAsync(entry);
        }
    }

    public boolean isKnownPlayer(String playerName) {
        return scores.containsKey(playerName.toLowerCase());
    }

    public PlayerRepEntry getOrCreate(String playerName) {
        return scores.computeIfAbsent(playerName.toLowerCase(), k -> new PlayerRepEntry(playerName, 0, null));
    }

    public int getReputation(String playerName) {
        PlayerRepEntry entry = scores.get(playerName.toLowerCase());
        return entry != null ? entry.reputation : 0;
    }

    public void addReputationDelta(String playerName, int delta, String offlineNoticeIfNeeded) {
        PlayerRepEntry entry = getOrCreate(playerName);
        entry.reputation += delta;
        if (offlineNoticeIfNeeded != null) {
            entry.pendingOfflineNotice = offlineNoticeIfNeeded;
        }
        saveAsync(entry);
    }

    public void clearOfflineNotice(String playerName) {
        PlayerRepEntry entry = scores.get(playerName.toLowerCase());
        if (entry != null && entry.pendingOfflineNotice != null) {
            entry.pendingOfflineNotice = null;
            saveAsync(entry);
        }
    }

    private void saveAsync(PlayerRepEntry entry) {
        String key = entry.realName.toLowerCase();
        String name = entry.realName;
        int rep = entry.reputation;
        String notice = entry.pendingOfflineNotice;

        extension.runTaskAsync(() -> {
            db().executeUpdate("DELETE FROM rep_scores WHERE player_key = ?", key);
            db().executeUpdate("INSERT INTO rep_scores (player_key, player_name, reputation, offline_notice) VALUES (?, ?, ?, ?)",
                    key, name, rep, notice);
        });
    }
}