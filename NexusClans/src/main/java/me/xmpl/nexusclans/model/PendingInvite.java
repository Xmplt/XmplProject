package me.xmpl.nexusclans.model;

public class PendingInvite {
    private final String clanKey;
    private final String senderName;
    private final String targetName;
    private final long expireAtMillis;

    public PendingInvite(String clanKey, String senderName, String targetName, long expireAtMillis) {
        this.clanKey = clanKey.toLowerCase();
        this.senderName = senderName;
        this.targetName = targetName;
        this.expireAtMillis = expireAtMillis;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() >= expireAtMillis;
    }

    public String getClanKey() { return clanKey; }
    public String getSenderName() { return senderName; }
    public String getTargetName() { return targetName; }
    public long getExpireAtMillis() { return expireAtMillis; }
}