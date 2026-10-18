package me.xmpl.ess.storage;

import me.xmpl.ess.XmplEssExtension;
import me.xmpl.ess.model.PunishmentRecord;
import me.xmpl.lib.database.DatabaseProvider;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BanStorage {
    private final XmplEssExtension extension;
    private final Map<String, PunishmentRecord> activeBans = new ConcurrentHashMap<>();

    public BanStorage(XmplEssExtension extension) {
        this.extension = extension;
        initTable();
        loadAll();
    }

    private DatabaseProvider db() {
        return extension.getDatabase("bans");
    }

    private void initTable() {
        db().executeUpdate("CREATE TABLE IF NOT EXISTS xmpl_bans (" +
                "player_key VARCHAR(32) PRIMARY KEY, " +
                "player_name VARCHAR(32), " +
                "admin_name VARCHAR(32), " +
                "reason TEXT, " +
                "custom_reason INT, " +
                "expire_at BIGINT" +
                ");");
    }

    public void loadAll() {
        activeBans.clear();
        try (Connection conn = db().getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM xmpl_bans");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                PunishmentRecord rec = new PunishmentRecord(
                        rs.getString("player_name"),
                        rs.getString("admin_name"),
                        rs.getString("reason"),
                        rs.getInt("custom_reason") == 1,
                        rs.getLong("expire_at")
                );
                activeBans.put(rs.getString("player_key").toLowerCase(), rec);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void saveBan(PunishmentRecord record) {
        String key = record.getPlayerName().toLowerCase();
        activeBans.put(key, record);
        extension.runTaskAsync(() -> {
            db().executeUpdate("DELETE FROM xmpl_bans WHERE player_key = ?", key);
            db().executeUpdate("INSERT INTO xmpl_bans (player_key, player_name, admin_name, reason, custom_reason, expire_at) VALUES (?, ?, ?, ?, ?, ?)",
                    key, record.getPlayerName(), record.getAdminName(), record.getReason(), record.isCustomReason() ? 1 : 0, record.getExpireAt());
        });
    }

    public void removeBan(String playerName) {
        String key = playerName.toLowerCase();
        activeBans.remove(key);
        extension.runTaskAsync(() -> db().executeUpdate("DELETE FROM xmpl_bans WHERE player_key = ?", key));
    }

    public PunishmentRecord getBan(String playerName) {
        return activeBans.get(playerName.toLowerCase());
    }
}