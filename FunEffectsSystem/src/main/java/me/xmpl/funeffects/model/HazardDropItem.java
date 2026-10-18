package me.xmpl.funeffects.model;

import org.bukkit.entity.Item;

public class HazardDropItem {
    public enum HazardType {
        KAKA,
        PIPI
    }

    private final Item itemEntity;
    private final String ownerName;
    private final HazardType type;
    private final long spawnedAtMillis;
    private final long expireAtMillis;
    private boolean ownerLeftArea = false;

    public HazardDropItem(Item itemEntity, String ownerName, HazardType type, long expireAtMillis) {
        this.itemEntity = itemEntity;
        this.ownerName = ownerName;
        this.type = type;
        this.spawnedAtMillis = System.currentTimeMillis();
        this.expireAtMillis = expireAtMillis;
    }

    public Item getItemEntity() { return itemEntity; }
    public String getOwnerName() { return ownerName; }
    public HazardType getType() { return type; }
    public long getSpawnedAtMillis() { return spawnedAtMillis; }
    public long getExpireAtMillis() { return expireAtMillis; }
    public boolean isOwnerLeftArea() { return ownerLeftArea; }
    public void setOwnerLeftArea(boolean ownerLeftArea) { this.ownerLeftArea = ownerLeftArea; }
}