package me.xmpl.mine.storage;

import me.xmpl.lib.database.DatabaseProvider;
import me.xmpl.mine.MineSystemExtension;
import me.xmpl.mine.model.MineOreType;
import me.xmpl.mine.model.PlayerOreBag;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MineBagDatabaseStorage {
    private final MineSystemExtension extension;
    private final Map<String, PlayerOreBag> bags = new ConcurrentHashMap<>();

    public MineBagDatabaseStorage(MineSystemExtension extension) {
        this.extension = extension;
        initTable();
        loadAll();
    }

    private DatabaseProvider db() {
        return extension.getDatabase("default");
    }

    private void initTable() {
        db().executeUpdate("CREATE TABLE IF NOT EXISTS mine_player_bags (" +
                "player_key VARCHAR(32) PRIMARY KEY, " +
                "player_name VARCHAR(32), " +
                "coal INT DEFAULT 0, " +
                "iron INT DEFAULT 0, " +
                "gold INT DEFAULT 0, " +
                "redstone INT DEFAULT 0, " +
                "lapis INT DEFAULT 0, " +
                "quartz INT DEFAULT 0, " +
                "diamond INT DEFAULT 0, " +
                "emerald INT DEFAULT 0" +
                ");");
    }

    public void loadAll() {
        bags.clear();
        try (Connection conn = db().getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM mine_player_bags");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                PlayerOreBag bag = new PlayerOreBag();
                bag.setAmount(MineOreType.COAL, rs.getInt("coal"));
                bag.setAmount(MineOreType.IRON, rs.getInt("iron"));
                bag.setAmount(MineOreType.GOLD, rs.getInt("gold"));
                bag.setAmount(MineOreType.REDSTONE, rs.getInt("redstone"));
                bag.setAmount(MineOreType.LAPIS, rs.getInt("lapis"));
                bag.setAmount(MineOreType.QUARTZ, rs.getInt("quartz"));
                bag.setAmount(MineOreType.DIAMOND, rs.getInt("diamond"));
                bag.setAmount(MineOreType.EMERALD, rs.getInt("emerald"));
                bags.put(rs.getString("player_key").toLowerCase(), bag);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public PlayerOreBag getBag(String playerName) {
        return bags.computeIfAbsent(playerName.toLowerCase(), k -> new PlayerOreBag());
    }

    public void saveBagAsync(String playerName) {
        PlayerOreBag bag = getBag(playerName);
        String key = playerName.toLowerCase();

        int coal = bag.getAmount(MineOreType.COAL);
        int iron = bag.getAmount(MineOreType.IRON);
        int gold = bag.getAmount(MineOreType.GOLD);
        int redstone = bag.getAmount(MineOreType.REDSTONE);
        int lapis = bag.getAmount(MineOreType.LAPIS);
        int quartz = bag.getAmount(MineOreType.QUARTZ);
        int diamond = bag.getAmount(MineOreType.DIAMOND);
        int emerald = bag.getAmount(MineOreType.EMERALD);

        extension.runTaskAsync(() -> {
            db().executeUpdate("DELETE FROM mine_player_bags WHERE player_key = ?", key);
            db().executeUpdate("INSERT INTO mine_player_bags " +
                            "(player_key, player_name, coal, iron, gold, redstone, lapis, quartz, diamond, emerald) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    key, playerName, coal, iron, gold, redstone, lapis, quartz, diamond, emerald);
        });
    }
}