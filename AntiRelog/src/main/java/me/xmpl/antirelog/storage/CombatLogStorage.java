package me.xmpl.antirelog.storage;

import me.xmpl.antirelog.AntiRelogExtension;
import me.xmpl.lib.database.DatabaseProvider;

import java.util.UUID;

public class CombatLogStorage {
    private final AntiRelogExtension extension;
    private boolean enabled;

    public CombatLogStorage(AntiRelogExtension extension) {
        this.extension = extension;
        reload();
    }

    public void reload() {
        this.enabled = extension.getCustomConfig("databases.yml").get().getBoolean("enable-database-logging", false);
        if (enabled) {
            DatabaseProvider db = extension.getDatabase("default");
            db.executeUpdate("CREATE TABLE IF NOT EXISTS antirelog_punishments (" +
                    "uuid VARCHAR(36) PRIMARY KEY, " +
                    "player_name VARCHAR(32), " +
                    "quits_in_pvp INT DEFAULT 0" +
                    ");");
        }
    }

    public void logCombatQuitAsync(UUID uuid, String playerName) {
        if (!enabled) return;
        extension.runTaskAsync(() -> {
            DatabaseProvider db = extension.getDatabase("default");
            if (db.getType().equalsIgnoreCase("MYSQL")) {
                db.executeUpdate("INSERT INTO antirelog_punishments (uuid, player_name, quits_in_pvp) VALUES (?, ?, 1) " +
                                "ON DUPLICATE KEY UPDATE quits_in_pvp = quits_in_pvp + 1, player_name = ?;",
                        uuid.toString(), playerName, playerName);
            } else {
                db.executeUpdate("INSERT OR IGNORE INTO antirelog_punishments (uuid, player_name, quits_in_pvp) VALUES (?, ?, 0);",
                        uuid.toString(), playerName);
                db.executeUpdate("UPDATE antirelog_punishments SET quits_in_pvp = quits_in_pvp + 1, player_name = ? WHERE uuid = ?;",
                        playerName, uuid.toString());
            }
        });
    }
}