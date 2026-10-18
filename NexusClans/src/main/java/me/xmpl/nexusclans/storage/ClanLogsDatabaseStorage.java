package me.xmpl.nexusclans.storage;

import me.xmpl.lib.database.DatabaseProvider;
import me.xmpl.nexusclans.NexusClansExtension;
import me.xmpl.nexusclans.model.ClanLogEntry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class ClanLogsDatabaseStorage {
    private final NexusClansExtension extension;

    public ClanLogsDatabaseStorage(NexusClansExtension extension) {
        this.extension = extension;
        initTable();
    }

    private DatabaseProvider db() {
        return extension.getDatabase("clans_logs");
    }

    private void initTable() {
        db().executeUpdate("CREATE TABLE IF NOT EXISTS nexus_clan_logs (" +
                "clan_key VARCHAR(32) NOT NULL, " +
                "player_key VARCHAR(32), " +
                "player_name VARCHAR(32), " +
                "points_delta INT NOT NULL DEFAULT 0, " +
                "message TEXT NOT NULL, " +
                "created_at BIGINT NOT NULL" +
                ");");
    }

    public void addLogAsync(String clanKey, String playerName, int pointsDelta, String message) {
        String cKey = clanKey.toLowerCase();
        String pKey = playerName != null ? playerName.toLowerCase() : "";
        String pName = playerName != null ? playerName : "";
        long now = System.currentTimeMillis();

        extension.runTaskAsync(() -> {
            db().executeUpdate("INSERT INTO nexus_clan_logs (clan_key, player_key, player_name, points_delta, message, created_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?)", cKey, pKey, pName, pointsDelta, message, now);
        });
    }

    public int countGlobalLogs(String clanKey) {
        try (Connection conn = db().getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM nexus_clan_logs WHERE clan_key = ?")) {
            ps.setString(1, clanKey.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    public int countPlayerLogs(String clanKey, String playerName) {
        try (Connection conn = db().getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM nexus_clan_logs WHERE clan_key = ? AND player_key = ?")) {
            ps.setString(1, clanKey.toLowerCase());
            ps.setString(2, playerName.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    public List<ClanLogEntry> getGlobalLogsPage(String clanKey, int page, int pageSize) {
        List<ClanLogEntry> list = new ArrayList<>();
        int offset = Math.max(0, (page - 1) * pageSize);
        String sql = "SELECT * FROM nexus_clan_logs WHERE clan_key = ? ORDER BY created_at DESC LIMIT ? OFFSET ?";
        try (Connection conn = db().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, clanKey.toLowerCase());
            ps.setInt(2, pageSize);
            ps.setInt(3, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new ClanLogEntry(
                            rs.getString("clan_key"),
                            rs.getString("player_name"),
                            rs.getInt("points_delta"),
                            rs.getString("message"),
                            rs.getLong("created_at")
                    ));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public List<ClanLogEntry> getPlayerLogsPage(String clanKey, String playerName, int page, int pageSize) {
        List<ClanLogEntry> list = new ArrayList<>();
        int offset = Math.max(0, (page - 1) * pageSize);
        String sql = "SELECT * FROM nexus_clan_logs WHERE clan_key = ? AND player_key = ? ORDER BY created_at DESC LIMIT ? OFFSET ?";
        try (Connection conn = db().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, clanKey.toLowerCase());
            ps.setString(2, playerName.toLowerCase());
            ps.setInt(3, pageSize);
            ps.setInt(4, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new ClanLogEntry(
                            rs.getString("clan_key"),
                            rs.getString("player_name"),
                            rs.getInt("points_delta"),
                            rs.getString("message"),
                            rs.getLong("created_at")
                    ));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public void deleteAllLogsForClan(String clanKey) {
        extension.runTaskAsync(() -> db().executeUpdate("DELETE FROM nexus_clan_logs WHERE clan_key = ?", clanKey.toLowerCase()));
    }
}