package me.xmpl.wood.storage;

import me.xmpl.lib.database.DatabaseProvider;
import me.xmpl.wood.WoodSystemExtension;
import me.xmpl.wood.model.PlayerWoodBag;
import me.xmpl.wood.model.WoodTreeType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WoodBagDatabaseStorage {
    private final WoodSystemExtension extension;
    private final Map<String, PlayerWoodBag> bags = new ConcurrentHashMap<>();

    public WoodBagDatabaseStorage(WoodSystemExtension extension) {
        this.extension = extension;
        initTable();
        loadAll();
    }

    private DatabaseProvider db() {
        return extension.getDatabase("default");
    }

    private void initTable() {
        db().executeUpdate("CREATE TABLE IF NOT EXISTS wood_player_bags (" +
                "player_key VARCHAR(32) PRIMARY KEY, " +
                "player_name VARCHAR(32), " +
                "oak INT DEFAULT 0, " +
                "spruce INT DEFAULT 0, " +
                "birch INT DEFAULT 0, " +
                "jungle INT DEFAULT 0, " +
                "acacia INT DEFAULT 0, " +
                "dark_oak INT DEFAULT 0" +
                ");");
    }

    public void loadAll() {
        bags.clear();
        try (Connection conn = db().getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM wood_player_bags");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                PlayerWoodBag bag = new PlayerWoodBag();
                bag.setAmount(WoodTreeType.OAK, rs.getInt("oak"));
                bag.setAmount(WoodTreeType.SPRUCE, rs.getInt("spruce"));
                bag.setAmount(WoodTreeType.BIRCH, rs.getInt("birch"));
                bag.setAmount(WoodTreeType.JUNGLE, rs.getInt("jungle"));
                bag.setAmount(WoodTreeType.ACACIA, rs.getInt("acacia"));
                bag.setAmount(WoodTreeType.DARK_OAK, rs.getInt("dark_oak"));
                bags.put(rs.getString("player_key").toLowerCase(), bag);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public PlayerWoodBag getBag(String playerName) {
        return bags.computeIfAbsent(playerName.toLowerCase(), k -> new PlayerWoodBag());
    }

    public void saveBagAsync(String playerName) {
        PlayerWoodBag bag = getBag(playerName);
        String key = playerName.toLowerCase();

        int oak = bag.getAmount(WoodTreeType.OAK);
        int spruce = bag.getAmount(WoodTreeType.SPRUCE);
        int birch = bag.getAmount(WoodTreeType.BIRCH);
        int jungle = bag.getAmount(WoodTreeType.JUNGLE);
        int acacia = bag.getAmount(WoodTreeType.ACACIA);
        int darkOak = bag.getAmount(WoodTreeType.DARK_OAK);

        extension.runTaskAsync(() -> {
            db().executeUpdate("DELETE FROM wood_player_bags WHERE player_key = ?", key);
            db().executeUpdate("INSERT INTO wood_player_bags " +
                            "(player_key, player_name, oak, spruce, birch, jungle, acacia, dark_oak) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    key, playerName, oak, spruce, birch, jungle, acacia, darkOak);
        });
    }
}