package me.xmpl.fishing.model;

public class WeightTier {
    private final String id;
    private final double chance;
    private final double minPercent;
    private final double maxPercent;
    private final String color;

    public WeightTier(String id, double chance, double minPercent, double maxPercent, String color) {
        this.id = id;
        this.chance = chance;
        this.minPercent = minPercent;
        this.maxPercent = maxPercent;
        this.color = color;
    }

    public String getId() { return id; }
    public double getChance() { return chance; }
    public double getMinPercent() { return minPercent; }
    public double getMaxPercent() { return maxPercent; }
    public String getColor() { return color; }
}