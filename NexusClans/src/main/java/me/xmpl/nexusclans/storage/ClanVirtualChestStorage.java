package me.xmpl.nexusclans.storage;

import me.xmpl.lib.database.DatabaseProvider;
import me.xmpl.nexusclans.NexusClansExtension;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ClanVirtualChestStorage {
    private final NexusClansExtension extension;
    // clanKey -> сериализованный YAML-слепок 54 слотов имен вкладчиков
    private final Map<String, String> rawYamlByClan = new ConcurrentHashMap<>();

    public ClanVirtualChestStorage(NexusClansExtension extension) {
        this.extension = extension;
        initTable();
        loadAll();
    }

    private DatabaseProvider db() {
        return extension.getDatabase("clans_chests");
    }

    private void initTable() {
        db().executeUpdate("CREATE TABLE IF NOT EXISTS nexus_clan_chests (" +
                "clan_key VARCHAR(32) PRIMARY KEY, " +
                "contents_yaml TEXT NOT NULL" +
                ");");
    }

    public void loadAll() {
        rawYamlByClan.clear();
        try (Connection conn = db().getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM nexus_clan_chests");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rawYamlByClan.put(rs.getString("clan_key").toLowerCase(), rs.getString("contents_yaml"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public YamlConfiguration loadChestYaml(String clanKey) {
        YamlConfiguration yaml = new YamlConfiguration();
        String raw = rawYamlByClan.get(clanKey.toLowerCase());
        if (raw != null && !raw.isEmpty()) {
            try {
                yaml.loadFromString(raw);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return yaml;
    }

    public void saveChestYamlAsync(String clanKey, ItemStack[] cleanItems, String[] depositors) {
        YamlConfiguration yaml = new YamlConfiguration();
        for (int i = 0; i < cleanItems.length; i++) {
            if (cleanItems[i] != null) {
                yaml.set("slots." + i + ".item", cleanItems[i]);
                if (depositors[i] != null) {
                    yaml.set("slots." + i + ".by", depositors[i]);
                }
            }
        }
        String serialized = yaml.saveToString();
        String key = clanKey.toLowerCase();
        rawYamlByClan.put(key, serialized);

        extension.runTaskAsync(() -> {
            db().executeUpdate("DELETE FROM nexus_clan_chests WHERE clan_key = ?", key);
            db().executeUpdate("INSERT INTO nexus_clan_chests VALUES (?, ?)", key, serialized);
        });
    }

    public void deleteChestForClan(String clanKey) {
        String key = clanKey.toLowerCase();
        rawYamlByClan.remove(key);
        extension.runTaskAsync(() -> db().executeUpdate("DELETE FROM nexus_clan_chests WHERE clan_key = ?", key));
    }
}