package me.xmpl.ess.model;

public class PunishmentRecord {
    private final String playerName;
    private final String adminName;
    private final String reason;
    private final boolean customReason;
    private final long expireAt; // -1 = Навсегда

    public PunishmentRecord(String playerName, String adminName, String reason, boolean customReason, long expireAt) {
        this.playerName = playerName;
        this.adminName = adminName;
        this.reason = reason;
        this.customReason = customReason;
        this.expireAt = expireAt;
    }

    public boolean isPermanent() {
        return expireAt == -1L;
    }

    public boolean isExpired() {
        return !isPermanent() && System.currentTimeMillis() >= expireAt;
    }

    public long getRemainingMillis() {
        if (isPermanent()) return -1L;
        return Math.max(0L, expireAt - System.currentTimeMillis());
    }

    public String getPlayerName() { return playerName; }
    public String getAdminName() { return adminName; }
    public String getReason() { return reason; }
    public boolean isCustomReason() { return customReason; }
    public long getExpireAt() { return expireAt; }
}