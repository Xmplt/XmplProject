package me.xmpl.fishing.model;

public class FishSpeciesConfig {
    private final String key;
    private final short data;
    private final String displayName;
    private final double minWeight;
    private final double maxWeight;
    private final double pricePerKg;

    public FishSpeciesConfig(String key, short data, String displayName, double minWeight, double maxWeight, double pricePerKg) {
        this.key = key;
        this.data = data;
        this.displayName = displayName;
        this.minWeight = minWeight;
        this.maxWeight = maxWeight;
        this.pricePerKg = pricePerKg;
    }

    public String getKey() { return key; }
    public short getData() { return data; }
    public String getDisplayName() { return displayName; }
    public double getMinWeight() { return minWeight; }
    public double getMaxWeight() { return maxWeight; }
    public double getPricePerKg() { return pricePerKg; }
}