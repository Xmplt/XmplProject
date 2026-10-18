package me.xmpl.nexusclans.model;

import org.bukkit.ChatColor;
import org.bukkit.Location;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ClanData {
    private final String clanKey; // Чистое имя клана без цветов в нижнем регистре
    private String rawColoredTag; // Название/тег клана с цветами, жирным и курсивом
    private String leaderName;
    private final long createdAtMillis;
    private double balance;
    private int totalPoints;
    private boolean tagShown; // Куплен ли единоразовый /c showtag за 25 000$
    private boolean pvpEnabled;
    private boolean sitAllowed;
    private boolean tpAllowed;
    private Location homeLocation;
    private long lastLeaderTransferMillis;
    private long warCooldownUntilMillis;

    private final Map<String, ClanMember> members = new ConcurrentHashMap<>();

    public ClanData(String clanKey, String rawColoredTag, String leaderName, long createdAtMillis,
                    double balance, int totalPoints, boolean tagShown,
                    boolean pvpEnabled, boolean sitAllowed, boolean tpAllowed,
                    Location homeLocation, long lastLeaderTransferMillis, long warCooldownUntilMillis) {
        this.clanKey = clanKey.toLowerCase();
        this.rawColoredTag = rawColoredTag;
        this.leaderName = leaderName;
        this.createdAtMillis = createdAtMillis;
        this.balance = balance;
        this.totalPoints = totalPoints;
        this.tagShown = tagShown;
        this.pvpEnabled = pvpEnabled;
        this.sitAllowed = sitAllowed;
        this.tpAllowed = tpAllowed;
        this.homeLocation = homeLocation;
        this.lastLeaderTransferMillis = lastLeaderTransferMillis;
        this.warCooldownUntilMillis = warCooldownUntilMillis;
    }

    public static String stripToKey(String input) {
        if (input == null) return "";
        String colored = ChatColor.translateAlternateColorCodes('&', input);
        return ChatColor.stripColor(colored).trim().toLowerCase();
    }

    public long getAgeInDays() {
        long diff = Math.max(0L, System.currentTimeMillis() - createdAtMillis);
        return Math.max(1L, diff / (86400L * 1000L) + 1L);
    }

    public String getClanKey() { return clanKey; }
    public String getRawColoredTag() { return rawColoredTag; }
    public void setRawColoredTag(String rawColoredTag) { this.rawColoredTag = rawColoredTag; }
    public String getLeaderName() { return leaderName; }
    public void setLeaderName(String leaderName) { this.leaderName = leaderName; }
    public long getCreatedAtMillis() { return createdAtMillis; }
    public double getBalance() { return balance; }
    public void setBalance(double balance) { this.balance = Math.max(0.0, balance); }
    public int getTotalPoints() { return totalPoints; }
    public void setTotalPoints(int totalPoints) { this.totalPoints = totalPoints; }
    public void addTotalPoints(int delta) { this.totalPoints += delta; }
    public boolean isTagShown() { return tagShown; }
    public void setTagShown(boolean tagShown) { this.tagShown = tagShown; }
    public boolean isPvpEnabled() { return pvpEnabled; }
    public void setPvpEnabled(boolean pvpEnabled) { this.pvpEnabled = pvpEnabled; }
    public boolean isSitAllowed() { return sitAllowed; }
    public void setSitAllowed(boolean sitAllowed) { this.sitAllowed = sitAllowed; }
    public boolean isTpAllowed() { return tpAllowed; }
    public void setTpAllowed(boolean tpAllowed) { this.tpAllowed = tpAllowed; }
    public Location getHomeLocation() { return homeLocation != null ? homeLocation.clone() : null; }
    public void setHomeLocation(Location homeLocation) { this.homeLocation = homeLocation != null ? homeLocation.clone() : null; }
    public long getLastLeaderTransferMillis() { return lastLeaderTransferMillis; }
    public void setLastLeaderTransferMillis(long lastLeaderTransferMillis) { this.lastLeaderTransferMillis = lastLeaderTransferMillis; }
    public long getWarCooldownUntilMillis() { return warCooldownUntilMillis; }
    public void setWarCooldownUntilMillis(long warCooldownUntilMillis) { this.warCooldownUntilMillis = warCooldownUntilMillis; }

    public Map<String, ClanMember> getMembersMap() { return members; }
    public Collection<ClanMember> getMembers() { return members.values(); }
    public ClanMember getMember(String name) { return name != null ? members.get(name.toLowerCase()) : null; }
}