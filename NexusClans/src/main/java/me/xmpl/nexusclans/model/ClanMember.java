package me.xmpl.nexusclans.model;

public class ClanMember {
    private final String playerKey;
    private String playerName;
    private String clanKey;
    private ClanRole role;
    private int contributedPoints;
    private long joinedAt;
    private long lastSeenMillis;
    private boolean ownClanGlowEnabled;
    private boolean allyClanGlowEnabled;

    public ClanMember(String playerName, String clanKey, ClanRole role, int contributedPoints,
                      long joinedAt, long lastSeenMillis, boolean ownClanGlowEnabled, boolean allyClanGlowEnabled) {
        this.playerKey = playerName.toLowerCase();
        this.playerName = playerName;
        this.clanKey = clanKey.toLowerCase();
        this.role = role;
        this.contributedPoints = contributedPoints;
        this.joinedAt = joinedAt;
        this.lastSeenMillis = lastSeenMillis;
        this.ownClanGlowEnabled = ownClanGlowEnabled;
        this.allyClanGlowEnabled = allyClanGlowEnabled;
    }

    public String getPlayerKey() { return playerKey; }
    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }
    public String getClanKey() { return clanKey; }
    public void setClanKey(String clanKey) { this.clanKey = clanKey.toLowerCase(); }
    public ClanRole getRole() { return role; }
    public void setRole(ClanRole role) { this.role = role; }
    public int getContributedPoints() { return contributedPoints; }
    public void setContributedPoints(int contributedPoints) { this.contributedPoints = contributedPoints; }
    public void addContributedPoints(int delta) { this.contributedPoints += delta; }
    public long getJoinedAt() { return joinedAt; }
    public long getLastSeenMillis() { return lastSeenMillis; }
    public void setLastSeenMillis(long lastSeenMillis) { this.lastSeenMillis = lastSeenMillis; }
    public boolean isOwnClanGlowEnabled() { return ownClanGlowEnabled; }
    public void setOwnClanGlowEnabled(boolean ownClanGlowEnabled) { this.ownClanGlowEnabled = ownClanGlowEnabled; }
    public boolean isAllyClanGlowEnabled() { return allyClanGlowEnabled; }
    public void setAllyClanGlowEnabled(boolean allyClanGlowEnabled) { this.allyClanGlowEnabled = allyClanGlowEnabled; }
}