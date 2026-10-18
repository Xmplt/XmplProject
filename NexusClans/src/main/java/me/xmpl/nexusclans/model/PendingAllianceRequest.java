package me.xmpl.nexusclans.model;

public class PendingAllianceRequest {
    private final String requesterClanKey;
    private final String targetClanKey;
    private final long expireAtMillis;

    public PendingAllianceRequest(String requesterClanKey, String targetClanKey, long expireAtMillis) {
        this.requesterClanKey = requesterClanKey.toLowerCase();
        this.targetClanKey = targetClanKey.toLowerCase();
        this.expireAtMillis = expireAtMillis;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() >= expireAtMillis;
    }

    public String getRequesterClanKey() { return requesterClanKey; }
    public String getTargetClanKey() { return targetClanKey; }
    public long getExpireAtMillis() { return expireAtMillis; }
}