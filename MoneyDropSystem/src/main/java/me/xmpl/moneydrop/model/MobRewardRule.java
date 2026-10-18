package me.xmpl.moneydrop.model;

public class MobRewardRule {
    private final boolean enabled;
    private final double min;
    private final double max;
    private final String displayName;

    public MobRewardRule(boolean enabled, double min, double max, String displayName) {
        this.enabled = enabled;
        this.min = min;
        this.max = max;
        this.displayName = displayName;
    }

    public boolean isEnabled() { return enabled; }
    public double getMin() { return min; }
    public double getMax() { return max; }
    public String getDisplayName() { return displayName; }
}