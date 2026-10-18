package me.xmpl.nexusclans.model;

public class PendingLeaderTransfer {
    private final String clanKey;
    private final String leaderName;
    private final String targetModeratorName;
    private final long expireAtMillis;

    public PendingLeaderTransfer(String clanKey, String leaderName, String targetModeratorName, long expireAtMillis) {
        this.clanKey = clanKey.toLowerCase();
        this.leaderName = leaderName;
        this.targetModeratorName = targetModeratorName;
        this.expireAtMillis = expireAtMillis;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() >= expireAtMillis;
    }

    public String getClanKey() { return clanKey; }
    public String getLeaderName() { return leaderName; }
    public String getTargetModeratorName() { return targetModeratorName; }
}