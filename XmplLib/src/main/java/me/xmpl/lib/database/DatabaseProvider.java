package me.xmpl.lib.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class DatabaseProvider {
    private final HikariDataSource dataSource;
    private final boolean sharedGlobalPool; // Если true, дополнение не должно закрывать глобальный пул при своей выгрузке
    private final String type; // MYSQL или SQLITE

    // Конструктор для MySQL
    public DatabaseProvider(String host, int port, String database, String user, String password, int poolSize, boolean sharedGlobalPool) {
        this.sharedGlobalPool = sharedGlobalPool;
        this.type = "MYSQL";

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&autoReconnect=true&characterEncoding=utf-8");
        config.setUsername(user);
        config.setPassword(password);
        config.setMaximumPoolSize(poolSize);
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        this.dataSource = new HikariDataSource(config);
    }

    // Конструктор для SQLite файла
    public DatabaseProvider(File sqliteFile, boolean sharedGlobalPool) {
        this.sharedGlobalPool = sharedGlobalPool;
        this.type = "SQLITE";

        try {
            if (!sqliteFile.exists()) {
                sqliteFile.getParentFile().mkdirs();
                sqliteFile.createNewFile();
            }
            Class.forName("org.sqlite.JDBC");
        } catch (Exception e) {
            throw new RuntimeException("Ошибка инициализации файла SQLite: " + sqliteFile.getAbsolutePath(), e);
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + sqliteFile.getAbsolutePath());
        config.setDriverClassName("org.sqlite.JDBC");
        config.setMaximumPoolSize(1); // Для SQLite оптимален 1 поток записи во избежание SQLITE_BUSY
        config.setConnectionTestQuery("SELECT 1");

        this.dataSource = new HikariDataSource(config);
    }

    private DatabaseProvider(HikariDataSource existingDataSource, String type) {
        this.dataSource = existingDataSource;
        this.sharedGlobalPool = true;
        this.type = type;
    }

    public DatabaseProvider createSharedReference() {
        return new DatabaseProvider(this.dataSource, this.type);
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    /**
     * Быстрое выполнение CREATE TABLE / INSERT / UPDATE запросов
     */
    public void executeUpdate(String sql, Object... params) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public String getType() {
        return type;
    }

    public void close() {
        if (!sharedGlobalPool && dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    public void forceCloseGlobal() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}