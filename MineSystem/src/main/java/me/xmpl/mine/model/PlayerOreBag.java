package me.xmpl.mine.model;

import java.util.EnumMap;
import java.util.Map;

public class PlayerOreBag {
    private final Map<MineOreType, Integer> counts = new EnumMap<>(MineOreType.class);

    public PlayerOreBag() {
        for (MineOreType type : MineOreType.values()) {
            counts.put(type, 0);
        }
    }

    public int getAmount(MineOreType type) {
        return counts.getOrDefault(type, 0);
    }

    public void setAmount(MineOreType type, int amount) {
        counts.put(type, Math.max(0, amount));
    }

    public void addOne(MineOreType type) {
        counts.put(type, getAmount(type) + 1);
    }

    public boolean isEmpty() {
        for (int val : counts.values()) {
            if (val > 0) return false;
        }
        return true;
    }

    public int getTotalItems() {
        int sum = 0;
        for (int val : counts.values()) {
            sum += val;
        }
        return sum;
    }

    public void clearAll() {
        for (MineOreType type : MineOreType.values()) {
            counts.put(type, 0);
        }
    }
}