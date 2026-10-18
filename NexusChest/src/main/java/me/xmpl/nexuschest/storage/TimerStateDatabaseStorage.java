package me.xmpl.nexuschest.storage;

import me.xmpl.lib.database.DatabaseProvider;
import me.xmpl.nexuschest.NexusChestExtension;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class TimerStateDatabaseStorage {
    private final NexusChestExtension extension;

    public TimerStateDatabaseStorage(NexusChestExtension extension) {
        this.extension = extension;
        initTable();
    }

    private DatabaseProvider db() {
        return extension.getDatabase("default");
    }

    private void initTable() {
        db().executeUpdate("CREATE TABLE IF NOT EXISTS nexuschest_timer_state (" +
                "state_id INT PRIMARY KEY, " +
                "remaining_seconds INT NOT NULL" +
                ");");
    }

    public int loadRemainingSeconds(int defaultInterval) {
        try (Connection conn = db().getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT remaining_seconds FROM nexuschest_timer_state WHERE state_id = 1");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                int val = rs.getInt("remaining_seconds");
                if (val > 0) return val;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return defaultInterval;
    }

    public void saveRemainingSecondsSync(int seconds) {
        db().executeUpdate("DELETE FROM nexuschest_timer_state WHERE state_id = 1");
        db().executeUpdate("INSERT INTO nexuschest_timer_state (state_id, remaining_seconds) VALUES (1, ?)", Math.max(1, seconds));
    }

    public void saveRemainingSecondsAsync(int seconds) {
        int safe = Math.max(1, seconds);
        extension.runTaskAsync(() -> saveRemainingSecondsSync(safe));
    }
}