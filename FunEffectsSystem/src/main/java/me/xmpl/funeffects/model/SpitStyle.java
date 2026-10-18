package me.xmpl.funeffects.model;

import org.bukkit.Material;
import java.util.List;

public class SpitStyle {
    private final String id;
    private final int page;
    private final int slot;
    private final boolean defaultUnlocked;
    private final String permission;
    private final Material icon;
    private final String displayName;
    private final List<String> description;
    private final String sound;
    private final float pitch;
    private final List<String> particles;

    public SpitStyle(String id, int page, int slot, boolean defaultUnlocked, String permission,
                     Material icon, String displayName, List<String> description,
                     String sound, float pitch, List<String> particles) {
        this.id = id;
        this.page = page;
        this.slot = slot;
        this.defaultUnlocked = defaultUnlocked;
        this.permission = permission;
        this.icon = icon;
        this.displayName = displayName;
        this.description = description;
        this.sound = sound;
        this.pitch = pitch;
        this.particles = particles;
    }

    public String getId() { return id; }
    public int getPage() { return page; }
    public int getSlot() { return slot; }
    public boolean isDefaultUnlocked() { return defaultUnlocked; }
    public String getPermission() { return permission; }
    public Material getIcon() { return icon; }
    public String getDisplayName() { return displayName; }
    public List<String> getDescription() { return description; }
    public String getSound() { return sound; }
    public float getPitch() { return pitch; }
    public List<String> getParticles() { return particles; }
}