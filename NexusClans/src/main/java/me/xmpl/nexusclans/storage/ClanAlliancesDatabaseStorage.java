package me.xmpl.nexusclans.storage;

import me.xmpl.lib.database.DatabaseProvider;
import me.xmpl.nexusclans.NexusClansExtension;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ClanAlliancesDatabaseStorage {
    public static class ClanTeamSettings {
        public boolean teamPvpEnabled = false;
        public boolean teamSitAllowed = false;
        public boolean teamTpAllowed = false;
    }

    private final NexusClansExtension extension;
    // clanKey -> набор clanKey союзников (максимум 2)
    private final Map<String, Set<String>> alliesMap = new ConcurrentHashMap<>();
    // Индивидуальные союзные флаги клана (если хотя бы у одного из двух союзников включен флаг — он работает между ними!)
    private final Map<String, ClanTeamSettings> teamSettingsMap = new ConcurrentHashMap<>();

    public ClanAlliancesDatabaseStorage(NexusClansExtension extension) {
        this.extension = extension;
        initTables();
        loadAll();
    }

    private DatabaseProvider db() {
        return extension.getDatabase("clans_alliances");
    }

    private void initTables() {
        db().executeUpdate("CREATE TABLE IF NOT EXISTS nexus_clan_alliances (" +
                "pair_key VARCHAR(68) PRIMARY KEY, " +
                "clan_a VARCHAR(32) NOT NULL, " +
                "clan_b VARCHAR(32) NOT NULL" +
                ");");

        db().executeUpdate("CREATE TABLE IF NOT EXISTS nexus_clan_team_settings (" +
                "clan_key VARCHAR(32) PRIMARY KEY, " +
                "team_pvp INT NOT NULL DEFAULT 0, " +
                "team_sit INT NOT NULL DEFAULT 0, " +
                "team_tp INT NOT NULL DEFAULT 0" +
                ");");
    }

    public void loadAll() {
        alliesMap.clear();
        teamSettingsMap.clear();

        try (Connection conn = db().getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM nexus_clan_alliances");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String a = rs.getString("clan_a").toLowerCase();
                    String b = rs.getString("clan_b").toLowerCase();
                    alliesMap.computeIfAbsent(a, k -> ConcurrentHashMap.newKeySet()).add(b);
                    alliesMap.computeIfAbsent(b, k -> ConcurrentHashMap.newKeySet()).add(a);
                }
            }

            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM nexus_clan_team_settings");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ClanTeamSettings s = new ClanTeamSettings();
                    s.teamPvpEnabled = rs.getInt("team_pvp") == 1;
                    s.teamSitAllowed = rs.getInt("team_sit") == 1;
                    s.teamTpAllowed = rs.getInt("team_tp") == 1;
                    teamSettingsMap.put(rs.getString("clan_key").toLowerCase(), s);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String makeSortedPairKey(String clanA, String clanB) {
        String a = clanA.toLowerCase();
        String b = clanB.toLowerCase();
        return a.compareTo(b) < 0 ? (a + ":" + b) : (b + ":" + a);
    }

    public Set<String> getAllies(String clanKey) {
        if (clanKey == null) return Collections.emptySet();
        return alliesMap.getOrDefault(clanKey.toLowerCase(), Collections.emptySet());
    }

    public boolean areAllies(String clanA, String clanB) {
        if (clanA == null || clanB == null) return false;
        return getAllies(clanA).contains(clanB.toLowerCase());
    }

    public void createAlliance(String clanA, String clanB) {
        String a = clanA.toLowerCase();
        String b = clanB.toLowerCase();
        String pairKey = makeSortedPairKey(a, b);

        alliesMap.computeIfAbsent(a, k -> ConcurrentHashMap.newKeySet()).add(b);
        alliesMap.computeIfAbsent(b, k -> ConcurrentHashMap.newKeySet()).add(a);

        extension.runTaskAsync(() -> {
            db().executeUpdate("DELETE FROM nexus_clan_alliances WHERE pair_key = ?", pairKey);
            db().executeUpdate("INSERT INTO nexus_clan_alliances VALUES (?, ?, ?)", pairKey, a, b);
        });
    }

    public void breakAlliance(String clanA, String clanB) {
        String a = clanA.toLowerCase();
        String b = clanB.toLowerCase();
        String pairKey = makeSortedPairKey(a, b);

        if (alliesMap.containsKey(a)) alliesMap.get(a).remove(b);
        if (alliesMap.containsKey(b)) alliesMap.get(b).remove(a);

        extension.runTaskAsync(() -> db().executeUpdate("DELETE FROM nexus_clan_alliances WHERE pair_key = ?", pairKey));
    }

    public void removeAllAlliancesForClan(String clanKey) {
        String a = clanKey.toLowerCase();
        Set<String> allies = alliesMap.remove(a);
        if (allies != null) {
            for (String b : allies) {
                if (alliesMap.containsKey(b)) alliesMap.get(b).remove(a);
            }
        }
        teamSettingsMap.remove(a);
        extension.runTaskAsync(() -> {
            db().executeUpdate("DELETE FROM nexus_clan_alliances WHERE clan_a = ? OR clan_b = ?", a, a);
            db().executeUpdate("DELETE FROM nexus_clan_team_settings WHERE clan_key = ?", a);
        });
    }

    public ClanTeamSettings getSettings(String clanKey) {
        return teamSettingsMap.computeIfAbsent(clanKey.toLowerCase(), k -> new ClanTeamSettings());
    }

    public void saveSettingsAsync(String clanKey) {
        String key = clanKey.toLowerCase();
        ClanTeamSettings s = getSettings(key);
        int pvp = s.teamPvpEnabled ? 1 : 0;
        int sit = s.teamSitAllowed ? 1 : 0;
        int tp = s.teamTpAllowed ? 1 : 0;

        extension.runTaskAsync(() -> {
            db().executeUpdate("DELETE FROM nexus_clan_team_settings WHERE clan_key = ?", key);
            db().executeUpdate("INSERT INTO nexus_clan_team_settings VALUES (?, ?, ?, ?)", key, pvp, sit, tp);
        });
    }

    /**
     * По ТЗ: если хотя бы один из двух союзных кланов включил teamPvp / teamSit / teamTp,
     * эта функция работает между обоими союзными кланами!
     */
    public boolean isEffectiveAllyPvpAllowed(String clanA, String clanB) {
        return getSettings(clanA).teamPvpEnabled || getSettings(clanB).teamPvpEnabled;
    }

    public boolean isEffectiveAllySitAllowed(String clanA, String clanB) {
        return getSettings(clanA).teamSitAllowed || getSettings(clanB).teamSitAllowed;
    }

    public boolean isEffectiveAllyTpAllowed(String clanA, String clanB) {
        return getSettings(clanA).teamTpAllowed || getSettings(clanB).teamTpAllowed;
    }
}