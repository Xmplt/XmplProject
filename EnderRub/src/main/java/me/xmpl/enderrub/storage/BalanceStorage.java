package me.xmpl.enderrub.storage;

import me.xmpl.enderrub.EnderRubExtension;
import me.xmpl.lib.database.DatabaseProvider;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BalanceStorage {
    private final EnderRubExtension extension;
    private final Map<String, Long> balances = new ConcurrentHashMap<>();

    public BalanceStorage(EnderRubExtension extension) {
        this.extension = extension;
        initTable();
        loadAll();
    }

    private DatabaseProvider db() {
        return extension.getDatabase("balances");
    }

    private void initTable() {
        db().executeUpdate("CREATE TABLE IF NOT EXISTS enderrub_balances (" +
                "player_key VARCHAR(32) PRIMARY KEY, " +
                "player_name VARCHAR(32), " +
                "balance BIGINT NOT NULL DEFAULT 0" +
                ");");
    }

    public void loadAll() {
        balances.clear();
        try (Connection conn = db().getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM enderrub_balances");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                balances.put(rs.getString("player_key").toLowerCase(), rs.getLong("balance"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public long getBalance(String playerName) {
        if (playerName == null) return 0L;
        long defaultStart = Math.max(0L, extension.getConfig().getLong("currency.starting-balance", 0L));
        return balances.getOrDefault(playerName.toLowerCase(), defaultStart);
    }

    public void setBalance(String playerName, long newBalance) {
        long clamped = Math.max(0L, newBalance);
        String key = playerName.toLowerCase();
        balances.put(key, clamped);

        extension.runTaskAsync(() -> {
            db().executeUpdate("DELETE FROM enderrub_balances WHERE player_key = ?", key);
            db().executeUpdate("INSERT INTO enderrub_balances (player_key, player_name, balance) VALUES (?, ?, ?)",
                    key, playerName, clamped);
        });
    }

    public void ensureAccountExists(String playerName) {
        String key = playerName.toLowerCase();
        if (!balances.containsKey(key)) {
            long start = Math.max(0L, extension.getConfig().getLong("currency.starting-balance", 0L));
            setBalance(playerName, start);
        }
    }
}