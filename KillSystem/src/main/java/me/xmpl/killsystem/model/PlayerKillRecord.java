package me.xmpl.killsystem.model;

public class PlayerKillRecord {
    private final String playerName;
    private int kills;
    private int deaths;

    public PlayerKillRecord(String playerName, int kills, int deaths) {
        this.playerName = playerName;
        this.kills = kills;
        this.deaths = deaths;
    }

    public void incrementKills() { this.kills++; }
    public void incrementDeaths() { this.deaths++; }

    public String getPlayerName() { return playerName; }
    public int getKills() { return kills; }
    public int getDeaths() { return deaths; }
}