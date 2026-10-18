package me.xmpl.lib.database;

import me.xmpl.lib.XmplLib;
import me.xmpl.lib.extension.XmplExtension;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;

public class DatabaseManager {
    private final XmplLib core;
    private String mode;
    private DatabaseProvider globalProvider;

    public DatabaseManager(XmplLib core) {
        this.core = core;
        initGlobal();
    }

    private void initGlobal() {
        FileConfiguration cfg = core.getConfig();
        this.mode = cfg.getString("database-mode", "HYBRID").toUpperCase();

        if (mode.equals("GLOBAL_MYSQL")) {
            globalProvider = createGlobalMySQL(cfg);
            core.getLogger().info("[Database] Активирован режим GLOBAL_MYSQL.");
        } else if (mode.equals("GLOBAL_SQLITE")) {
            File dbFile = new File(core.getDataFolder(), "storage.db");
            globalProvider = new DatabaseProvider(dbFile, true);
            core.getLogger().info("[Database] Активирован режим GLOBAL_SQLITE (storage.db).");
        } else {
            core.getLogger().info("[Database] Активирован режим HYBRID (индивидуальные настройки дополнений).");
        }
    }

    private DatabaseProvider createGlobalMySQL(FileConfiguration cfg) {
        String host = cfg.getString("global-mysql.host", "127.0.0.1");
        int port = cfg.getInt("global-mysql.port", 3306);
        String db = cfg.getString("global-mysql.database", "xmpl_global");
        String user = cfg.getString("global-mysql.username", "root");
        String pass = cfg.getString("global-mysql.password", "");
        int pool = cfg.getInt("global-mysql.pool-size", 10);
        return new DatabaseProvider(host, port, db, user, pass, pool, true);
    }

    public DatabaseProvider createProviderFor(XmplExtension extension) {
        return createProviderFor(extension, "default");
    }

    /**
     * Позволяет дополнению создавать либо одну БД ("default"), либо множество разных БД по ключу
     * с настройкой в отдельном файле databases.yml внутри папки Configs/<ИмяДополнения>/
     */
    public DatabaseProvider createProviderFor(XmplExtension extension, String dbKey) {
        if (mode.equals("GLOBAL_MYSQL") || mode.equals("GLOBAL_SQLITE")) {
            return globalProvider.createSharedReference();
        }

        FileConfiguration dbCfg = extension.getCustomConfig("databases.yml").get();
        String path = "databases." + dbKey;
        ConfigurationSection dbSec = dbCfg.getConfigurationSection(path);

        if (dbSec == null) {
            dbCfg.set(path + ".type", "SQLITE"); // Варианты: SQLITE, MYSQL, GLOBAL
            dbCfg.set(path + ".sqlite-file", dbKey.equals("default") ? "database.db" : dbKey + ".db");
            dbCfg.set(path + ".mysql.host", "127.0.0.1");
            dbCfg.set(path + ".mysql.port", 3306);
            dbCfg.set(path + ".mysql.database", "xmpl_" + extension.getName().toLowerCase() + "_" + dbKey);
            dbCfg.set(path + ".mysql.username", "root");
            dbCfg.set(path + ".mysql.password", "password");
            extension.getCustomConfig("databases.yml").save();
            dbSec = dbCfg.getConfigurationSection(path);
        }

        String extType = dbSec.getString("type", "SQLITE").toUpperCase();

        if (extType.equals("GLOBAL")) {
            if (globalProvider == null) {
                File dbFile = new File(core.getDataFolder(), "storage.db");
                globalProvider = new DatabaseProvider(dbFile, true);
            }
            return globalProvider.createSharedReference();
        } else if (extType.equals("MYSQL")) {
            String host = dbSec.getString("mysql.host", "127.0.0.1");
            int port = dbSec.getInt("mysql.port", 3306);
            String db = dbSec.getString("mysql.database", "minecraft");
            String user = dbSec.getString("mysql.username", "root");
            String pass = dbSec.getString("mysql.password", "");
            return new DatabaseProvider(host, port, db, user, pass, 5, false);
        } else {
            String fileName = dbSec.getString("sqlite-file", dbKey + ".db");
            File sqliteFile = new File(extension.getDataFolder(), fileName);
            return new DatabaseProvider(sqliteFile, false);
        }
    }

    public void shutdown() {
        if (globalProvider != null) {
            globalProvider.forceCloseGlobal();
        }
    }
}