package me.xmpl.redline.model;

public class PlayerMarkData {
    private RedLineZone lastZone = RedLineZone.WILDERNESS;
    private boolean marked = false;
    private boolean timerRunningInSafeSpawn = false;
    private long vulnerableUntilMillis = 0L;
    private long lastMessageSentMillis = 0L;
    private long lastSpawnCommandTypedMillis = 0L;

    /**
     * Активна ли метка прямо сейчас (либо игрок стоит в боевом кольце за линией, либо вернулся на спавн и его 20 секунд еще не истекли)
     */
    public boolean isCurrentlyVulnerable() {
        if (!marked) return false;
        if (timerRunningInSafeSpawn) {
            return System.currentTimeMillis() < vulnerableUntilMillis;
        }
        return true;
    }

    public int getRemainingSeconds(int defaultDuration) {
        if (!marked) return 0;
        if (!timerRunningInSafeSpawn) return defaultDuration;
        long diff = vulnerableUntilMillis - System.currentTimeMillis();
        if (diff <= 0L) return 0;
        return (int) Math.ceil(diff / 1000.0);
    }

    public void activateInPvpRing() {
        this.marked = true;
        this.timerRunningInSafeSpawn = false;
        this.vulnerableUntilMillis = 0L;
    }

    public void startSafeSpawnCountdown(int seconds) {
        this.marked = true;
        this.timerRunningInSafeSpawn = true;
        this.vulnerableUntilMillis = System.currentTimeMillis() + (seconds * 1000L);
    }

    public void clearMark() {
        this.marked = false;
        this.timerRunningInSafeSpawn = false;
        this.vulnerableUntilMillis = 0L;
    }

    public RedLineZone getLastZone() { return lastZone; }
    public void setLastZone(RedLineZone lastZone) { this.lastZone = lastZone; }

    public boolean isMarked() { return marked; }
    public boolean isTimerRunningInSafeSpawn() { return timerRunningInSafeSpawn; }

    public long getLastMessageSentMillis() { return lastMessageSentMillis; }
    public void setLastMessageSentMillis(long millis) { this.lastMessageSentMillis = millis; }

    public long getLastSpawnCommandTypedMillis() { return lastSpawnCommandTypedMillis; }
    public void setLastSpawnCommandTypedMillis(long millis) { this.lastSpawnCommandTypedMillis = millis; }
}