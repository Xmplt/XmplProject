package me.xmpl.enderrub.storage;

import me.xmpl.enderrub.EnderRubExtension;
import me.xmpl.enderrub.model.TransactionRecord;
import me.xmpl.enderrub.model.TransactionType;
import me.xmpl.lib.database.DatabaseProvider;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class HistoryStorage {
    private final EnderRubExtension extension;

    public HistoryStorage(EnderRubExtension extension) {
        this.extension = extension;
        initTable();
    }

    private DatabaseProvider db() {
        return extension.getDatabase("history");
    }

    private void initTable() {
        db().executeUpdate("CREATE TABLE IF NOT EXISTS enderrub_history (" +
                "player_key VARCHAR(32), " +
                "player_name VARCHAR(32), " +
                "tx_type VARCHAR(16), " +
                "amount BIGINT, " +
                "balance_after BIGINT, " +
                "source VARCHAR(64), " +
                "description TEXT, " +
                "created_at BIGINT" +
                ");");
    }

    public void addRecordAsync(TransactionRecord record) {
        extension.runTaskAsync(() -> {
            db().executeUpdate("INSERT INTO enderrub_history " +
                            "(player_key, player_name, tx_type, amount, balance_after, source, description, created_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    record.getPlayerName().toLowerCase(),
                    record.getPlayerName(),
                    record.getType().name(),
                    record.getAmount(),
                    record.getBalanceAfter(),
                    record.getSource(),
                    record.getDescription(),
                    record.getTimestamp());
        });
    }

    public int countRecords(String playerName) {
        try (Connection conn = db().getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM enderrub_history WHERE player_key = ?")) {
            ps.setString(1, playerName.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    public List<TransactionRecord> getPage(String playerName, int page, int pageSize) {
        List<TransactionRecord> list = new ArrayList<>();
        int offset = Math.max(0, (page - 1) * pageSize);

        String sql = "SELECT * FROM enderrub_history WHERE player_key = ? ORDER BY created_at DESC LIMIT ? OFFSET ?";
        try (Connection conn = db().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerName.toLowerCase());
            ps.setInt(2, pageSize);
            ps.setInt(3, offset);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new TransactionRecord(
                            rs.getString("player_name"),
                            TransactionType.valueOf(rs.getString("tx_type")),
                            rs.getLong("amount"),
                            rs.getLong("balance_after"),
                            rs.getString("source"),
                            rs.getString("description"),
                            rs.getLong("created_at")
                    ));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }
}