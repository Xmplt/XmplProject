package me.xmpl.nexusclans.model;

public class ClanLogEntry {
    private final String clanKey;
    private final String playerName; // null или имя игрока, к которому относится лог
    private final int pointsDelta;
    private final String message;
    private final long timestamp;

    public ClanLogEntry(String clanKey, String playerName, int pointsDelta, String message, long timestamp) {
        this.clanKey = clanKey.toLowerCase();
        this.playerName = playerName;
        this.pointsDelta = pointsDelta;
        this.message = message;
        this.timestamp = timestamp;
    }

    public String getClanKey() { return clanKey; }
    public String getPlayerName() { return playerName; }
    public int getPointsDelta() { return pointsDelta; }
    public String getMessage() { return message; }
    public long getTimestamp() { return timestamp; }
}