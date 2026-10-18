package me.xmpl.wood.model;

import java.util.EnumMap;
import java.util.Map;

public class PlayerWoodBag {
    private final Map<WoodTreeType, Integer> counts = new EnumMap<>(WoodTreeType.class);

    public PlayerWoodBag() {
        for (WoodTreeType type : WoodTreeType.values()) {
            counts.put(type, 0);
        }
    }

    public int getAmount(WoodTreeType type) {
        return counts.getOrDefault(type, 0);
    }

    public void setAmount(WoodTreeType type, int amount) {
        counts.put(type, Math.max(0, amount));
    }

    public void addOne(WoodTreeType type) {
        counts.put(type, getAmount(type) + 1);
    }

    public int getTotalTrees() {
        int sum = 0;
        for (int v : counts.values()) {
            sum += v;
        }
        return sum;
    }

    public boolean isEmpty() {
        return getTotalTrees() <= 0;
    }

    public void clearAll() {
        for (WoodTreeType type : WoodTreeType.values()) {
            counts.put(type, 0);
        }
    }
}