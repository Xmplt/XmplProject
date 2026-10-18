package me.xmpl.topka.storage;

import me.xmpl.lib.database.DatabaseProvider;
import me.xmpl.topka.TopkaExtension;
import me.xmpl.topka.model.PersistedTopkaState;
import me.xmpl.topka.model.TopkaStage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TopkaStateDatabaseStorage {
    private final TopkaExtension extension;

    public TopkaStateDatabaseStorage(TopkaExtension extension) {
        this.extension = extension;
        initTable();
    }

    private DatabaseProvider db() {
        return extension.getDatabase("default");
    }

    private void initTable() {
        db().executeUpdate("CREATE TABLE IF NOT EXISTS topka_runtime_state (" +
                "state_id INT PRIMARY KEY, " +
                "stage VARCHAR(32) NOT NULL, " +
                "remaining_seconds INT NOT NULL, " +
                "chosen_chest INT NOT NULL DEFAULT 1, " +
                "first_taken INT NOT NULL DEFAULT 0, " +
                "active_terrain VARCHAR(64)" +
                ");");

        db().executeUpdate("CREATE TABLE IF NOT EXISTS topka_voting_round (" +
                "id INT PRIMARY KEY, " +
                "options_csv TEXT NOT NULL" +
                ");");

        db().executeUpdate("CREATE TABLE IF NOT EXISTS topka_voting_votes (" +
                "voter_uuid VARCHAR(36) PRIMARY KEY, " +
                "terrain_id VARCHAR(64) NOT NULL" +
                ");");
    }

    public PersistedTopkaState loadState(int defaultCooldownSeconds) {
        PersistedTopkaState state = new PersistedTopkaState();
        state.stage = TopkaStage.TIMER;
        state.remainingSeconds = defaultCooldownSeconds;

        try (Connection conn = db().getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM topka_runtime_state WHERE state_id = 1");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                state.stage = TopkaStage.valueOf(rs.getString("stage"));
                state.remainingSeconds = Math.max(1, rs.getInt("remaining_seconds"));
                state.chosenChestNumber = rs.getInt("chosen_chest");
                state.firstItemTaken = rs.getInt("first_taken") == 1;
                state.activeTerrainId = rs.getString("active_terrain");
            }
        } catch (Exception e) {
            state.stage = TopkaStage.TIMER;
            state.remainingSeconds = defaultCooldownSeconds;
        }
        return state;
    }

    public void saveStateSync(PersistedTopkaState state) {
        db().executeUpdate("DELETE FROM topka_runtime_state WHERE state_id = 1");
        db().executeUpdate("INSERT INTO topka_runtime_state (state_id, stage, remaining_seconds, chosen_chest, first_taken, active_terrain) " +
                        "VALUES (1, ?, ?, ?, ?, ?)",
                state.stage.name(),
                Math.max(1, state.remainingSeconds),
                state.chosenChestNumber,
                state.firstItemTaken ? 1 : 0,
                state.activeTerrainId != null ? state.activeTerrainId : "");
    }

    public void saveStateAsync(PersistedTopkaState state) {
        String stName = state.stage.name();
        int rem = Math.max(1, state.remainingSeconds);
        int chosen = state.chosenChestNumber;
        int first = state.firstItemTaken ? 1 : 0;
        String terra = state.activeTerrainId != null ? state.activeTerrainId : "";

        extension.runTaskAsync(() -> {
            db().executeUpdate("DELETE FROM topka_runtime_state WHERE state_id = 1");
            db().executeUpdate("INSERT INTO topka_runtime_state (state_id, stage, remaining_seconds, chosen_chest, first_taken, active_terrain) " +
                    "VALUES (1, ?, ?, ?, ?, ?)", stName, rem, chosen, first, terra);
        });
    }

    public List<String> loadVotingRoundOptions() {
        List<String> list = new ArrayList<>();
        try (Connection conn = db().getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT options_csv FROM topka_voting_round WHERE id = 1");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                String csv = rs.getString("options_csv");
                if (csv != null && !csv.trim().isEmpty()) {
                    list.addAll(Arrays.asList(csv.split(",")));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public void saveVotingRoundOptionsAsync(List<String> options) {
        String csv = String.join(",", options);
        extension.runTaskAsync(() -> {
            db().executeUpdate("DELETE FROM topka_voting_round WHERE id = 1");
            db().executeUpdate("INSERT INTO topka_voting_round VALUES (1, ?)", csv);
        });
    }

    public Map<UUID, String> loadPlayerVotes() {
        Map<UUID, String> map = new HashMap<>();
        try (Connection conn = db().getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM topka_voting_votes");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                try {
                    map.put(UUID.fromString(rs.getString("voter_uuid")), rs.getString("terrain_id"));
                } catch (IllegalArgumentException ignored) {}
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return map;
    }

    public void savePlayerVoteAsync(UUID voterUuid, String terrainId) {
        String uStr = voterUuid.toString();
        extension.runTaskAsync(() -> {
            db().executeUpdate("DELETE FROM topka_voting_votes WHERE voter_uuid = ?", uStr);
            db().executeUpdate("INSERT INTO topka_voting_votes VALUES (?, ?)", uStr, terrainId);
        });
    }

    public void clearAllVotesAsync() {
        extension.runTaskAsync(() -> {
            db().executeUpdate("DELETE FROM topka_voting_votes");
            db().executeUpdate("DELETE FROM topka_voting_round WHERE id = 1");
        });
    }
}