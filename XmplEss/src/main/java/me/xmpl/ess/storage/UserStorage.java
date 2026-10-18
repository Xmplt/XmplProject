package me.xmpl.ess.storage;

import me.xmpl.ess.XmplEssExtension;
import me.xmpl.ess.model.UserRecord;
import me.xmpl.lib.database.DatabaseProvider;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class UserStorage {
    private final XmplEssExtension extension;
    private final Map<String, UserRecord> usersByName = new ConcurrentHashMap<>();
    private final Map<UUID, Long> joinTimestamps = new ConcurrentHashMap<>();

    public UserStorage(XmplEssExtension extension) {
        this.extension = extension;
        initTable();
        loadAll();
    }

    private DatabaseProvider db() {
        return extension.getDatabase("userdata");
    }

    private void initTable() {
        db().executeUpdate("CREATE TABLE IF NOT EXISTS xmpl_users (" +
                "player_key VARCHAR(32) PRIMARY KEY, " +
                "player_name VARCHAR(32), " +
                "uuid VARCHAR(36), " +
                "last_seen BIGINT, " +
                "unban_admin VARCHAR(32), " +
                "unban_reason TEXT, " +
                "expired_ban_notice INT, " +
                "offline_mute_notice TEXT, " +
                "takecmd_notice TEXT" +
                ");");
    }

    public void loadAll() {
        usersByName.clear();
        try (Connection conn = db().getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM xmpl_users");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                UserRecord rec = new UserRecord(
                        rs.getString("player_name"),
                        rs.getString("uuid"),
                        rs.getLong("last_seen"),
                        rs.getString("unban_admin"),
                        rs.getString("unban_reason"),
                        rs.getInt("expired_ban_notice") == 1,
                        rs.getString("offline_mute_notice"),
                        rs.getString("takecmd_notice")
                );
                usersByName.put(rs.getString("player_key").toLowerCase(), rec);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void saveUserRecord(UserRecord rec) {
        String key = rec.name.toLowerCase();
        usersByName.put(key, rec);
        extension.runTaskAsync(() -> {
            db().executeUpdate("DELETE FROM xmpl_users WHERE player_key = ?", key);
            db().executeUpdate("INSERT INTO xmpl_users VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    key, rec.name, rec.uuid, rec.lastSeen, rec.pendingUnbanAdmin, rec.pendingUnbanReason,
                    rec.pendingExpiredBanNotice ? 1 : 0, rec.pendingOfflineMuteNotice, rec.pendingTakeCmdNotice);
        });
    }

    public UserRecord getOrCreate(String name, String uuid) {
        UserRecord existing = usersByName.get(name.toLowerCase());
        if (existing != null) return existing;
        UserRecord created = new UserRecord(name, uuid, System.currentTimeMillis(), null, null, false, null, null);
        saveUserRecord(created);
        return created;
    }

    public UserRecord getUser(String name) {
        return usersByName.get(name.toLowerCase());
    }

    public void markOnline(UUID uuid) {
        joinTimestamps.put(uuid, System.currentTimeMillis());
    }

    public long getSessionDuration(UUID uuid) {
        Long start = joinTimestamps.get(uuid);
        return start != null ? System.currentTimeMillis() - start : 0L;
    }
}