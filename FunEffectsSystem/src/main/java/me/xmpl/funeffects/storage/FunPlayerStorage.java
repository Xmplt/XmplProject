package me.xmpl.funeffects.storage;

import me.xmpl.funeffects.FunEffectsExtension;
import me.xmpl.lib.database.DatabaseProvider;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FunPlayerStorage {
    private final FunEffectsExtension extension;
    private final Map<String, String> selectedSpitMap = new ConcurrentHashMap<>();

    public FunPlayerStorage(FunEffectsExtension extension) {
        this.extension = extension;
        initTable();
        loadAll();
    }

    private DatabaseProvider db() {
        return extension.getDatabase("default");
    }

    private void initTable() {
        db().executeUpdate("CREATE TABLE IF NOT EXISTS funeffects_spit_choice (" +
                "player_key VARCHAR(32) PRIMARY KEY, " +
                "player_name VARCHAR(32), " +
                "spit_style VARCHAR(32) NOT NULL DEFAULT 'DEFAULT'" +
                ");");
    }

    public void loadAll() {
        selectedSpitMap.clear();
        try (Connection conn = db().getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM funeffects_spit_choice");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                selectedSpitMap.put(rs.getString("player_key").toLowerCase(), rs.getString("spit_style").toUpperCase());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String getSelectedSpitId(String playerName) {
        return selectedSpitMap.getOrDefault(playerName.toLowerCase(), "DEFAULT");
    }

    public void setSelectedSpitId(String playerName, String styleId) {
        String key = playerName.toLowerCase();
        String upperStyle = styleId.toUpperCase();
        selectedSpitMap.put(key, upperStyle);

        extension.runTaskAsync(() -> {
            db().executeUpdate("DELETE FROM funeffects_spit_choice WHERE player_key = ?", key);
            db().executeUpdate("INSERT INTO funeffects_spit_choice (player_key, player_name, spit_style) VALUES (?, ?, ?)",
                    key, playerName, upperStyle);
        });
    }
}