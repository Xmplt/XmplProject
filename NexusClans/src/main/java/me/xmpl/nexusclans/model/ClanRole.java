package me.xmpl.nexusclans.model;

public enum ClanRole {
    MEMBER(1, "Участник"),
    MODERATOR(2, "Модератор"),
    LEADER(3, "Глава");

    private final int weight;
    private final String display;

    ClanRole(int weight, String display) {
        this.weight = weight;
        this.display = display;
    }

    public boolean isAtLeast(ClanRole other) {
        return this.weight >= other.weight;
    }

    public int getWeight() { return weight; }
    public String getDisplay() { return display; }
}