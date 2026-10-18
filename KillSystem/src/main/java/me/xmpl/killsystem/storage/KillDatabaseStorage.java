package me.xmpl.killsystem.storage;

import me.xmpl.killsystem.KillSystemExtension;
import me.xmpl.killsystem.model.PlayerKillRecord;
import me.xmpl.lib.database.DatabaseProvider;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class KillDatabaseStorage {
    private final KillSystemExtension extension;
    private final Map<String, PlayerKillRecord> cache = new ConcurrentHashMap<>();

    public KillDatabaseStorage(KillSystemExtension extension) {
        this.extension = extension;
        initTable();
        loadAll();
    }

    private DatabaseProvider db() {
        return extension.getDatabase("default");
    }

    private void initTable() {
        db().executeUpdate("CREATE TABLE IF NOT EXISTS killsystem_stats (" +
                "player_key VARCHAR(32) PRIMARY KEY, " +
                "player_name VARCHAR(32), " +
                "kills INT NOT NULL DEFAULT 0, " +
                "deaths INT NOT NULL DEFAULT 0" +
                ");");
    }

    public void loadAll() {
        cache.clear();
        try (Connection conn = db().getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM killsystem_stats");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                PlayerKillRecord rec = new PlayerKillRecord(
                        rs.getString("player_name"),
                        rs.getInt("kills"),
                        rs.getInt("deaths")
                );
                cache.put(rs.getString("player_key").toLowerCase(), rec);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public PlayerKillRecord getOrCreate(String playerName) {
        return cache.computeIfAbsent(playerName.toLowerCase(), k -> new PlayerKillRecord(playerName, 0, 0));
    }

    public void addKill(String killerName) {
        PlayerKillRecord rec = getOrCreate(killerName);
        rec.incrementKills();
        saveRecordAsync(rec);
    }

    public void addDeath(String victimName) {
        PlayerKillRecord rec = getOrCreate(victimName);
        rec.incrementDeaths();
        saveRecordAsync(rec);
    }

    private void saveRecordAsync(PlayerKillRecord rec) {
        String key = rec.getPlayerName().toLowerCase();
        int k = rec.getKills();
        int d = rec.getDeaths();
        extension.runTaskAsync(() -> {
            db().executeUpdate("DELETE FROM killsystem_stats WHERE player_key = ?", key);
            db().executeUpdate("INSERT INTO killsystem_stats (player_key, player_name, kills, deaths) VALUES (?, ?, ?, ?)",
                    key, rec.getPlayerName(), k, d);
        });
    }

    /**
     * Возвращает отсортированный список всех игроков: сначала по убыванию убийств, а при равенстве — у кого меньше смертей
     */
    public List<PlayerKillRecord> getSortedTopList() {
        List<PlayerKillRecord> list = new ArrayList<>(cache.values());
        list.sort((a, b) -> {
            int cmp = Integer.compare(b.getKills(), a.getKills());
            if (cmp != 0) return cmp;
            return Integer.compare(a.getDeaths(), b.getDeaths());
        });
        return list;
    }
}