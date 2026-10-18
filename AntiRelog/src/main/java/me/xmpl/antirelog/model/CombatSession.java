package me.xmpl.antirelog.model;

import org.bukkit.boss.BossBar;
import java.util.UUID;

public class CombatSession {
    private final UUID playerUuid;
    private String opponentName;
    private final int maxDurationSeconds;
    private int remainingSeconds;
    private BossBar bossBar;

    public CombatSession(UUID playerUuid, String opponentName, int maxDurationSeconds, BossBar bossBar) {
        this.playerUuid = playerUuid;
        this.opponentName = opponentName;
        this.maxDurationSeconds = maxDurationSeconds;
        this.remainingSeconds = maxDurationSeconds;
        this.bossBar = bossBar;
    }

    public void resetTimer(String newOpponentName, int maxDuration) {
        this.opponentName = newOpponentName;
        this.remainingSeconds = maxDuration;
    }

    public boolean tickSecond() {
        this.remainingSeconds--;
        return this.remainingSeconds <= 0;
    }

    public double getProgressRatio() {
        if (maxDurationSeconds <= 0) return 0.0;
        return (double) remainingSeconds / (double) maxDurationSeconds;
    }

    public UUID getPlayerUuid() { return playerUuid; }
    public String getOpponentName() { return opponentName; }
    public int getRemainingSeconds() { return remainingSeconds; }
    public BossBar getBossBar() { return bossBar; }
    public void setBossBar(BossBar bossBar) { this.bossBar = bossBar; }
}