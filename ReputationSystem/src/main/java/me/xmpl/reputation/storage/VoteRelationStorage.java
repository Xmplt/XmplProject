package me.xmpl.reputation.storage;

import me.xmpl.lib.database.DatabaseProvider;
import me.xmpl.reputation.ReputationSystemExtension;
import me.xmpl.reputation.model.VoteType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class VoteRelationStorage {
    private final ReputationSystemExtension extension;
    // Ключ: "voter_lower:target_lower" -> VoteType (LIKE / DISLIKE)
    private final Map<String, VoteType> votesMap = new ConcurrentHashMap<>();

    public VoteRelationStorage(ReputationSystemExtension extension) {
        this.extension = extension;
        initTable();
        loadAll();
    }

    private DatabaseProvider db() {
        return extension.getDatabase("votes");
    }

    private void initTable() {
        db().executeUpdate("CREATE TABLE IF NOT EXISTS rep_vote_relations (" +
                "pair_key VARCHAR(68) PRIMARY KEY, " +
                "voter_key VARCHAR(32), " +
                "target_key VARCHAR(32), " +
                "vote_type VARCHAR(16), " +
                "updated_at BIGINT" +
                ");");
    }

    public void loadAll() {
        votesMap.clear();
        try (Connection conn = db().getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM rep_vote_relations");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                try {
                    VoteType type = VoteType.valueOf(rs.getString("vote_type"));
                    votesMap.put(rs.getString("pair_key").toLowerCase(), type);
                } catch (IllegalArgumentException ignored) {}
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String makePairKey(String voterName, String targetName) {
        return voterName.toLowerCase() + ":" + targetName.toLowerCase();
    }

    public VoteType getPreviousVote(String voterName, String targetName) {
        return votesMap.get(makePairKey(voterName, targetName));
    }

    public void setVote(String voterName, String targetName, VoteType type) {
        String pairKey = makePairKey(voterName, targetName);
        String vKey = voterName.toLowerCase();
        String tKey = targetName.toLowerCase();
        long now = System.currentTimeMillis();

        votesMap.put(pairKey, type);

        extension.runTaskAsync(() -> {
            db().executeUpdate("DELETE FROM rep_vote_relations WHERE pair_key = ?", pairKey);
            db().executeUpdate("INSERT INTO rep_vote_relations (pair_key, voter_key, target_key, vote_type, updated_at) VALUES (?, ?, ?, ?, ?)",
                    pairKey, vKey, tKey, type.name(), now);
        });
    }
}