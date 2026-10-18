package me.xmpl.ess.storage;

import me.xmpl.ess.XmplEssExtension;
import me.xmpl.ess.model.PunishmentRecord;
import me.xmpl.lib.database.DatabaseProvider;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MuteStorage {
    private final XmplEssExtension extension;
    private final Map<String, PunishmentRecord> activeMutes = new ConcurrentHashMap<>();
    private final Map<String, PunishmentRecord> takenMuteRights = new ConcurrentHashMap<>();

    public MuteStorage(XmplEssExtension extension) {
        this.extension = extension;
        initTables();
        loadAll();
    }

    private DatabaseProvider db() {
        return extension.getDatabase("mutes");
    }

    private void initTables() {
        db().executeUpdate("CREATE TABLE IF NOT EXISTS xmpl_mutes (" +
                "player_key VARCHAR(32) PRIMARY KEY, " +
                "player_name VARCHAR(32), " +
                "admin_name VARCHAR(32), " +
                "reason TEXT, " +
                "custom_reason INT, " +
                "expire_at BIGINT" +
                ");");

        db().executeUpdate("CREATE TABLE IF NOT EXISTS xmpl_takecmd (" +
                "player_key VARCHAR(32) PRIMARY KEY, " +
                "player_name VARCHAR(32), " +
                "admin_name VARCHAR(32), " +
                "reason TEXT" +
                ");");
    }

    public void loadAll() {
        activeMutes.clear();
        takenMuteRights.clear();
        try (Connection conn = db().getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM xmpl_mutes");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    PunishmentRecord rec = new PunishmentRecord(
                            rs.getString("player_name"),
                            rs.getString("admin_name"),
                            rs.getString("reason"),
                            rs.getInt("custom_reason") == 1,
                            rs.getLong("expire_at")
                    );
                    activeMutes.put(rs.getString("player_key").toLowerCase(), rec);
                }
            }
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM xmpl_takecmd");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    PunishmentRecord rec = new PunishmentRecord(
                            rs.getString("player_name"),
                            rs.getString("admin_name"),
                            rs.getString("reason"),
                            true,
                            -1L
                    );
                    takenMuteRights.put(rs.getString("player_key").toLowerCase(), rec);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void saveMute(PunishmentRecord record) {
        String key = record.getPlayerName().toLowerCase();
        activeMutes.put(key, record);
        extension.runTaskAsync(() -> {
            db().executeUpdate("DELETE FROM xmpl_mutes WHERE player_key = ?", key);
            db().executeUpdate("INSERT INTO xmpl_mutes (player_key, player_name, admin_name, reason, custom_reason, expire_at) VALUES (?, ?, ?, ?, ?, ?)",
                    key, record.getPlayerName(), record.getAdminName(), record.getReason(), record.isCustomReason() ? 1 : 0, record.getExpireAt());
        });
    }

    public void removeMute(String playerName) {
        String key = playerName.toLowerCase();
        activeMutes.remove(key);
        extension.runTaskAsync(() -> db().executeUpdate("DELETE FROM xmpl_mutes WHERE player_key = ?", key));
    }

    public PunishmentRecord getActiveMute(String playerName) {
        PunishmentRecord rec = activeMutes.get(playerName.toLowerCase());
        if (rec != null && rec.isExpired()) {
            removeMute(playerName);
            return null;
        }
        return rec;
    }

    public boolean isMuteRightTaken(String playerName) {
        return takenMuteRights.containsKey(playerName.toLowerCase());
    }

    public PunishmentRecord getTakenRightRecord(String playerName) {
        return takenMuteRights.get(playerName.toLowerCase());
    }

    public void setMuteRightTaken(String playerName, String adminName, String reason) {
        String key = playerName.toLowerCase();
        PunishmentRecord rec = new PunishmentRecord(playerName, adminName, reason, true, -1L);
        takenMuteRights.put(key, rec);
        extension.runTaskAsync(() -> {
            db().executeUpdate("DELETE FROM xmpl_takecmd WHERE player_key = ?", key);
            db().executeUpdate("INSERT INTO xmpl_takecmd (player_key, player_name, admin_name, reason) VALUES (?, ?, ?, ?)",
                    key, playerName, adminName, reason);
        });
    }

    public void restoreMuteRight(String playerName) {
        String key = playerName.toLowerCase();
        takenMuteRights.remove(key);
        extension.runTaskAsync(() -> db().executeUpdate("DELETE FROM xmpl_takecmd WHERE player_key = ?", key));
    }
}