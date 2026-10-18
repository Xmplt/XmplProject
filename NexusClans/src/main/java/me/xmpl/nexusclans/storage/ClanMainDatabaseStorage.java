package me.xmpl.nexusclans.storage;

import me.xmpl.lib.database.DatabaseProvider;
import me.xmpl.nexusclans.NexusClansExtension;
import me.xmpl.nexusclans.model.ClanData;
import me.xmpl.nexusclans.model.ClanMember;
import me.xmpl.nexusclans.model.ClanRole;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ClanMainDatabaseStorage {
    private final NexusClansExtension extension;
    private final Map<String, ClanData> clansByKey = new ConcurrentHashMap<>();
    private final Map<String, ClanMember> membersByPlayerKey = new ConcurrentHashMap<>();
    // Запрет инвайта после кика на 3 дня: "clanKey:playerKey" -> expireAtMillis
    private final Map<String, Long> kickReinviteCooldowns = new ConcurrentHashMap<>();
    // Оффлайн-уведомление об изгнании из клана: playerKey -> reason
    private final Map<String, String> pendingOfflineKickNotices = new ConcurrentHashMap<>();

    public ClanMainDatabaseStorage(NexusClansExtension extension) {
        this.extension = extension;
        initTables();
        loadAll();
    }

    private DatabaseProvider db() {
        return extension.getDatabase("clans_main");
    }

    private void initTables() {
        db().executeUpdate("CREATE TABLE IF NOT EXISTS nexus_clans (" +
                "clan_key VARCHAR(32) PRIMARY KEY, " +
                "colored_tag VARCHAR(64) NOT NULL, " +
                "leader_name VARCHAR(32) NOT NULL, " +
                "created_at BIGINT NOT NULL, " +
                "balance DOUBLE NOT NULL DEFAULT 0.0, " +
                "total_points INT NOT NULL DEFAULT 0, " +
                "tag_shown INT NOT NULL DEFAULT 0, " +
                "pvp_enabled INT NOT NULL DEFAULT 0, " +
                "sit_allowed INT NOT NULL DEFAULT 0, " +
                "tp_allowed INT NOT NULL DEFAULT 1, " +
                "has_home INT NOT NULL DEFAULT 0, " +
                "home_world VARCHAR(64), " +
                "home_x DOUBLE, " +
                "home_y DOUBLE, " +
                "home_z DOUBLE, " +
                "home_yaw FLOAT, " +
                "home_pitch FLOAT, " +
                "last_leader_transfer BIGINT NOT NULL DEFAULT 0, " +
                "war_cooldown_until BIGINT NOT NULL DEFAULT 0" +
                ");");

        db().executeUpdate("CREATE TABLE IF NOT EXISTS nexus_clan_members (" +
                "player_key VARCHAR(32) PRIMARY KEY, " +
                "player_name VARCHAR(32) NOT NULL, " +
                "clan_key VARCHAR(32) NOT NULL, " +
                "role VARCHAR(16) NOT NULL, " +
                "contributed_points INT NOT NULL DEFAULT 0, " +
                "joined_at BIGINT NOT NULL, " +
                "last_seen BIGINT NOT NULL, " +
                "own_glow INT NOT NULL DEFAULT 1, " +
                "ally_glow INT NOT NULL DEFAULT 1" +
                ");");

        db().executeUpdate("CREATE TABLE IF NOT EXISTS nexus_clan_kick_bans (" +
                "pair_key VARCHAR(68) PRIMARY KEY, " +
                "expire_at BIGINT NOT NULL" +
                ");");

        db().executeUpdate("CREATE TABLE IF NOT EXISTS nexus_clan_offline_kicks (" +
                "player_key VARCHAR(32) PRIMARY KEY, " +
                "reason TEXT NOT NULL" +
                ");");
    }

    public void loadAll() {
        clansByKey.clear();
        membersByPlayerKey.clear();
        kickReinviteCooldowns.clear();
        pendingOfflineKickNotices.clear();

        long now = System.currentTimeMillis();
        try (Connection conn = db().getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM nexus_clans");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Location home = null;
                    if (rs.getInt("has_home") == 1) {
                        World w = Bukkit.getWorld(rs.getString("home_world"));
                        if (w != null) {
                            home = new Location(w, rs.getDouble("home_x"), rs.getDouble("home_y"), rs.getDouble("home_z"),
                                    rs.getFloat("home_yaw"), rs.getFloat("home_pitch"));
                        }
                    }
                    ClanData clan = new ClanData(
                            rs.getString("clan_key"),
                            rs.getString("colored_tag"),
                            rs.getString("leader_name"),
                            rs.getLong("created_at"),
                            rs.getDouble("balance"),
                            rs.getInt("total_points"),
                            rs.getInt("tag_shown") == 1,
                            rs.getInt("pvp_enabled") == 1,
                            rs.getInt("sit_allowed") == 1,
                            rs.getInt("tp_allowed") == 1,
                            home,
                            rs.getLong("last_leader_transfer"),
                            rs.getLong("war_cooldown_until")
                    );
                    clansByKey.put(clan.getClanKey(), clan);
                }
            }

            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM nexus_clan_members");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ClanMember m = new ClanMember(
                            rs.getString("player_name"),
                            rs.getString("clan_key"),
                            ClanRole.valueOf(rs.getString("role")),
                            rs.getInt("contributed_points"),
                            rs.getLong("joined_at"),
                            rs.getLong("last_seen"),
                            rs.getInt("own_glow") == 1,
                            rs.getInt("ally_glow") == 1
                    );
                    ClanData clan = clansByKey.get(m.getClanKey());
                    if (clan != null) {
                        clan.getMembersMap().put(m.getPlayerKey(), m);
                        membersByPlayerKey.put(m.getPlayerKey(), m);
                    }
                }
            }

            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM nexus_clan_kick_bans");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long exp = rs.getLong("expire_at");
                    if (exp > now) {
                        kickReinviteCooldowns.put(rs.getString("pair_key").toLowerCase(), exp);
                    }
                }
            }

            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM nexus_clan_offline_kicks");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    pendingOfflineKickNotices.put(rs.getString("player_key").toLowerCase(), rs.getString("reason"));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void saveClanAsync(ClanData clan) {
        Location h = clan.getHomeLocation();
        int hasHome = (h != null && h.getWorld() != null) ? 1 : 0;
        String hWorld = hasHome == 1 ? h.getWorld().getName() : "";
        double hx = hasHome == 1 ? h.getX() : 0.0;
        double hy = hasHome == 1 ? h.getY() : 0.0;
        double hz = hasHome == 1 ? h.getZ() : 0.0;
        float hyaw = hasHome == 1 ? h.getYaw() : 0.0f;
        float hpitch = hasHome == 1 ? h.getPitch() : 0.0f;

        extension.runTaskAsync(() -> {
            db().executeUpdate("DELETE FROM nexus_clans WHERE clan_key = ?", clan.getClanKey());
            db().executeUpdate("INSERT INTO nexus_clans VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    clan.getClanKey(),
                    clan.getRawColoredTag(),
                    clan.getLeaderName(),
                    clan.getCreatedAtMillis(),
                    clan.getBalance(),
                    clan.getTotalPoints(),
                    clan.isTagShown() ? 1 : 0,
                    clan.isPvpEnabled() ? 1 : 0,
                    clan.isSitAllowed() ? 1 : 0,
                    clan.isTpAllowed() ? 1 : 0,
                    hasHome, hWorld, hx, hy, hz, hyaw, hpitch,
                    clan.getLastLeaderTransferMillis(),
                    clan.getWarCooldownUntilMillis());
        });
    }

    public void saveMemberAsync(ClanMember m) {
        extension.runTaskAsync(() -> {
            db().executeUpdate("DELETE FROM nexus_clan_members WHERE player_key = ?", m.getPlayerKey());
            db().executeUpdate("INSERT INTO nexus_clan_members VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    m.getPlayerKey(),
                    m.getPlayerName(),
                    m.getClanKey(),
                    m.getRole().name(),
                    m.getContributedPoints(),
                    m.getJoinedAt(),
                    m.getLastSeenMillis(),
                    m.isOwnClanGlowEnabled() ? 1 : 0,
                    m.isAllyClanGlowEnabled() ? 1 : 0);
        });
    }

    public void registerNewClan(ClanData clan, ClanMember leaderMember) {
        clansByKey.put(clan.getClanKey(), clan);
        clan.getMembersMap().put(leaderMember.getPlayerKey(), leaderMember);
        membersByPlayerKey.put(leaderMember.getPlayerKey(), leaderMember);
        saveClanAsync(clan);
        saveMemberAsync(leaderMember);
    }

    public void addMemberToClan(ClanData clan, ClanMember member) {
        clan.getMembersMap().put(member.getPlayerKey(), member);
        membersByPlayerKey.put(member.getPlayerKey(), member);
        saveMemberAsync(member);
    }

    /**
     * Удаляет участника из клана.
     * ВАЖНО: по ТЗ при кике игрока (даже с минусовыми очками) глобальные очки клана остаются как есть!
     */
    public void removeMemberFromClan(ClanData clan, String playerName) {
        String pKey = playerName.toLowerCase();
        clan.getMembersMap().remove(pKey);
        membersByPlayerKey.remove(pKey);
        extension.runTaskAsync(() -> db().executeUpdate("DELETE FROM nexus_clan_members WHERE player_key = ?", pKey));
    }

    public void deleteClanCompletely(String clanKey) {
        String key = clanKey.toLowerCase();
        ClanData removed = clansByKey.remove(key);
        if (removed != null) {
            for (ClanMember m : removed.getMembers()) {
                membersByPlayerKey.remove(m.getPlayerKey());
            }
        }
        extension.runTaskAsync(() -> {
            db().executeUpdate("DELETE FROM nexus_clans WHERE clan_key = ?", key);
            db().executeUpdate("DELETE FROM nexus_clan_members WHERE clan_key = ?", key);
        });
    }

    public void setKickReinviteBan(String clanKey, String playerName, long durationSeconds) {
        String pairKey = clanKey.toLowerCase() + ":" + playerName.toLowerCase();
        long expireAt = System.currentTimeMillis() + (durationSeconds * 1000L);
        kickReinviteCooldowns.put(pairKey, expireAt);
        extension.runTaskAsync(() -> {
            db().executeUpdate("DELETE FROM nexus_clan_kick_bans WHERE pair_key = ?", pairKey);
            db().executeUpdate("INSERT INTO nexus_clan_kick_bans VALUES (?, ?)", pairKey, expireAt);
        });
    }

    public long getRemainingKickBanMillis(String clanKey, String playerName) {
        String pairKey = clanKey.toLowerCase() + ":" + playerName.toLowerCase();
        Long exp = kickReinviteCooldowns.get(pairKey);
        if (exp == null) return 0L;
        long diff = exp - System.currentTimeMillis();
        if (diff <= 0L) {
            kickReinviteCooldowns.remove(pairKey);
            return 0L;
        }
        return diff;
    }

    public void setOfflineKickNotice(String playerName, String reason) {
        String pKey = playerName.toLowerCase();
        pendingOfflineKickNotices.put(pKey, reason);
        extension.runTaskAsync(() -> {
            db().executeUpdate("DELETE FROM nexus_clan_offline_kicks WHERE player_key = ?", pKey);
            db().executeUpdate("INSERT INTO nexus_clan_offline_kicks VALUES (?, ?)", pKey, reason);
        });
    }

    public String consumeOfflineKickNotice(String playerName) {
        String pKey = playerName.toLowerCase();
        String reason = pendingOfflineKickNotices.remove(pKey);
        if (reason != null) {
            extension.runTaskAsync(() -> db().executeUpdate("DELETE FROM nexus_clan_offline_kicks WHERE player_key = ?", pKey));
        }
        return reason;
    }

    public ClanData getClanByNameOrTag(String nameOrTag) {
        String key = ClanData.stripToKey(nameOrTag);
        return clansByKey.get(key);
    }

    public ClanData getPlayerClan(String playerName) {
        if (playerName == null) return null;
        ClanMember m = membersByPlayerKey.get(playerName.toLowerCase());
        return m != null ? clansByKey.get(m.getClanKey()) : null;
    }

    public ClanMember getMember(String playerName) {
        return playerName != null ? membersByPlayerKey.get(playerName.toLowerCase()) : null;
    }

    public Collection<ClanData> getAllClans() {
        return clansByKey.values();
    }

    /**
     * Возвращает отсортированный список всех кланов по убыванию очков (для /c top, табличек Топ-3 и скорборда Топ-10).
     */
    public List<ClanData> getSortedTopClans() {
        List<ClanData> list = new ArrayList<>(clansByKey.values());
        list.sort((a, b) -> {
            int cmp = Integer.compare(b.getTotalPoints(), a.getTotalPoints());
            if (cmp != 0) return cmp;
            return Long.compare(a.getCreatedAtMillis(), b.getCreatedAtMillis());
        });
        return list;
    }

    public int getClanRankPosition(String clanKey) {
        List<ClanData> sorted = getSortedTopClans();
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).getClanKey().equalsIgnoreCase(clanKey)) {
                return i + 1;
            }
        }
        return sorted.size() + 1;
    }
}