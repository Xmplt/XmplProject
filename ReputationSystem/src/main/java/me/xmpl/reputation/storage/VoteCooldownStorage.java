package me.xmpl.reputation.storage;

import me.xmpl.lib.database.DatabaseProvider;
import me.xmpl.reputation.ReputationSystemExtension;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class VoteCooldownStorage {
    private final ReputationSystemExtension extension;
    // Ключ: voter_key -> timestamp когда истекает кулдаун
    private final Map<String, Long> cooldownExpireMap = new ConcurrentHashMap<>();

    public VoteCooldownStorage(ReputationSystemExtension extension) {
        this.extension = extension;
        initTable();
        loadAll();
    }

    private DatabaseProvider db() {
        return extension.getDatabase("cooldowns");
    }

    private void initTable() {
        db().executeUpdate("CREATE TABLE IF NOT EXISTS rep_cooldowns (" +
                "voter_key VARCHAR(32) PRIMARY KEY, " +
                "expire_at BIGINT NOT NULL" +
                ");");
    }

    public void loadAll() {
        cooldownExpireMap.clear();
        long now = System.currentTimeMillis();
        try (Connection conn = db().getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM rep_cooldowns");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                long exp = rs.getLong("expire_at");
                if (exp > now) {
                    cooldownExpireMap.put(rs.getString("voter_key").toLowerCase(), exp);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean isOnCooldown(String voterName) {
        Long expireAt = cooldownExpireMap.get(voterName.toLowerCase());
        if (expireAt == null) return false;
        if (System.currentTimeMillis() >= expireAt) {
            cooldownExpireMap.remove(voterName.toLowerCase());
            return false;
        }
        return true;
    }

    /**
     * Возвращает оставшееся время кулдауна в минутах (округление вверх, минимум 1 мин, пока кулдаун идет)
     */
    public int getRemainingMinutes(String voterName) {
        Long expireAt = cooldownExpireMap.get(voterName.toLowerCase());
        if (expireAt == null) return 0;
        long diffMillis = expireAt - System.currentTimeMillis();
        if (diffMillis <= 0L) return 0;
        return (int) Math.max(1L, (long) Math.ceil(diffMillis / 60000.0));
    }

    public void applyCooldown(String voterName, int seconds) {
        if (seconds <= 0) return;
        String key = voterName.toLowerCase();
        long expireAt = System.currentTimeMillis() + (seconds * 1000L);
        cooldownExpireMap.put(key, expireAt);

        extension.runTaskAsync(() -> {
            db().executeUpdate("DELETE FROM rep_cooldowns WHERE voter_key = ?", key);
            db().executeUpdate("INSERT INTO rep_cooldowns (voter_key, expire_at) VALUES (?, ?)", key, expireAt);
        });
    }
}