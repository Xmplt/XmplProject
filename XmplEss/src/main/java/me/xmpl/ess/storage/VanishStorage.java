package me.xmpl.ess.storage;

import me.xmpl.ess.XmplEssExtension;
import me.xmpl.lib.database.DatabaseProvider;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VanishStorage {
    private final XmplEssExtension extension;
    private final Set<UUID> vanishedPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> godPlayers = ConcurrentHashMap.newKeySet();

    public VanishStorage(XmplEssExtension extension) {
        this.extension = extension;
        initTable();
        loadAll();
    }

    private DatabaseProvider db() {
        return extension.getDatabase("vanish");
    }

    private void initTable() {
        db().executeUpdate("CREATE TABLE IF NOT EXISTS xmpl_vanish_state (" +
                "uuid VARCHAR(36) PRIMARY KEY, " +
                "is_vanished INT, " +
                "is_god INT" +
                ");");
    }

    public void loadAll() {
        vanishedPlayers.clear();
        godPlayers.clear();
        try (Connection conn = db().getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM xmpl_vanish_state");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                if (rs.getInt("is_vanished") == 1) vanishedPlayers.add(uuid);
                if (rs.getInt("is_god") == 1) godPlayers.add(uuid);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setVanished(UUID uuid, boolean vanished) {
        if (vanished) vanishedPlayers.add(uuid);
        else vanishedPlayers.remove(uuid);
        saveStateAsync(uuid);
    }

    public void setGod(UUID uuid, boolean god) {
        if (god) godPlayers.add(uuid);
        else godPlayers.remove(uuid);
        saveStateAsync(uuid);
    }

    private void saveStateAsync(UUID uuid) {
        int v = vanishedPlayers.contains(uuid) ? 1 : 0;
        int g = godPlayers.contains(uuid) ? 1 : 0;
        extension.runTaskAsync(() -> {
            db().executeUpdate("DELETE FROM xmpl_vanish_state WHERE uuid = ?", uuid.toString());
            db().executeUpdate("INSERT INTO xmpl_vanish_state (uuid, is_vanished, is_god) VALUES (?, ?, ?)",
                    uuid.toString(), v, g);
        });
    }

    public boolean isVanished(UUID uuid) { return vanishedPlayers.contains(uuid); }
    public boolean isGod(UUID uuid) { return godPlayers.contains(uuid); }
}