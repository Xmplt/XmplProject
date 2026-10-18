package me.xmpl.airdrop.model;

import org.bukkit.inventory.ItemStack;

public class AirDropLootItem {
    private final String id;
    private ItemStack itemStack;
    private double chance;

    public AirDropLootItem(String id, ItemStack itemStack, double chance) {
        this.id = id;
        this.itemStack = itemStack;
        this.chance = Math.max(0.1, Math.min(100.0, chance));
    }

    public String getId() { return id; }
    public ItemStack getItemStack() { return itemStack; }
    public void setItemStack(ItemStack itemStack) { this.itemStack = itemStack; }
    public double getChance() { return chance; }
    public void setChance(double chance) { this.chance = Math.max(0.1, Math.min(100.0, Math.round(chance * 10.0) / 10.0)); }
}