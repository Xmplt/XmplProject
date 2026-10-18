package me.xmpl.airdrop.storage;

import me.xmpl.airdrop.AirDropExtension;
import me.xmpl.airdrop.model.AirDropStage;
import me.xmpl.airdrop.model.PersistedAirDropState;
import me.xmpl.lib.database.DatabaseProvider;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class AirDropStateDatabaseStorage {
    private final AirDropExtension extension;

    public AirDropStateDatabaseStorage(AirDropExtension extension) {
        this.extension = extension;
        initTable();
    }

    private DatabaseProvider db() {
        return extension.getDatabase("default");
    }

    private void initTable() {
        db().executeUpdate("CREATE TABLE IF NOT EXISTS airdrop_runtime_state (" +
                "id INT PRIMARY KEY, " +
                "stage VARCHAR(32), " +
                "timer_sec INT, " +
                "world_key VARCHAR(64), " +
                "x INT, " +
                "y INT, " +
                "z INT, " +
                "has_loc INT, " +
                "wg_region VARCHAR(64), " +
                "r_elytra INT, " +
                "r_pearl INT, " +
                "r_chorus INT, " +
                "cur_phase INT, " +
                "total_phases INT, " +
                "rem_items INT" +
                ");");
    }

    public PersistedAirDropState loadState(int defaultSpawnInterval) {
        PersistedAirDropState s = new PersistedAirDropState();
        s.timerSeconds = defaultSpawnInterval;

        try (Connection conn = db().getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM airdrop_runtime_state WHERE id = 1");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                s.stage = AirDropStage.valueOf(rs.getString("stage"));
                s.timerSeconds = rs.getInt("timer_sec");
                s.worldKey = rs.getString("world_key");
                if (rs.getInt("has_loc") == 1 && s.worldKey != null) {
                    World w = Bukkit.getWorld(s.worldKey);
                    if (w != null) {
                        s.chestLocation = new Location(w, rs.getInt("x"), rs.getInt("y"), rs.getInt("z"));
                    }
                }
                s.wgRegionId = rs.getString("wg_region");
                s.restrictElytra = rs.getInt("r_elytra") == 1;
                s.restrictPearl = rs.getInt("r_pearl") == 1;
                s.restrictChorus = rs.getInt("r_chorus") == 1;
                s.currentPhase = rs.getInt("cur_phase");
                s.totalPhasesForRound = rs.getInt("total_phases");
                s.remainingItemsInCurrentPhase = rs.getInt("rem_items");
            }
        } catch (Exception e) {
            s.stage = AirDropStage.IDLE;
            s.timerSeconds = defaultSpawnInterval;
        }
        return s;
    }

    public void saveStateSync(PersistedAirDropState s) {
        int hasLoc = (s.chestLocation != null && s.chestLocation.getWorld() != null) ? 1 : 0;
        String wKey = hasLoc == 1 ? s.chestLocation.getWorld().getName() : (s.worldKey != null ? s.worldKey : "");
        int x = hasLoc == 1 ? s.chestLocation.getBlockX() : 0;
        int y = hasLoc == 1 ? s.chestLocation.getBlockY() : 0;
        int z = hasLoc == 1 ? s.chestLocation.getBlockZ() : 0;

        db().executeUpdate("DELETE FROM airdrop_runtime_state WHERE id = 1");
        db().executeUpdate("INSERT INTO airdrop_runtime_state VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                s.stage.name(),
                Math.max(0, s.timerSeconds),
                wKey,
                x, y, z,
                hasLoc,
                s.wgRegionId != null ? s.wgRegionId : "",
                s.restrictElytra ? 1 : 0,
                s.restrictPearl ? 1 : 0,
                s.restrictChorus ? 1 : 0,
                s.currentPhase,
                s.totalPhasesForRound,
                s.remainingItemsInCurrentPhase);
    }

    public void saveStateAsync(PersistedAirDropState s) {
        extension.runTaskAsync(() -> saveStateSync(s));
    }
}