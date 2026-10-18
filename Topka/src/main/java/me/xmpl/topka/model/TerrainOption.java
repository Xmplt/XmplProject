package me.xmpl.topka.model;

import org.bukkit.Material;
import java.util.List;

public class TerrainOption {
    private final String id;
    private final boolean enabled;
    private final String displayName;
    private final Material iconMaterial;
    private final short iconData;
    private final List<String> lore;
    private final String worldName;
    private final int originX;
    private final int originY;
    private final int originZ;

    public TerrainOption(String id, boolean enabled, String displayName, Material iconMaterial, short iconData,
                         List<String> lore, String worldName, int originX, int originY, int originZ) {
        this.id = id;
        this.enabled = enabled;
        this.displayName = displayName;
        this.iconMaterial = iconMaterial;
        this.iconData = iconData;
        this.lore = lore;
        this.worldName = worldName;
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
    }

    public String getId() { return id; }
    public boolean isEnabled() { return enabled; }
    public String getDisplayName() { return displayName; }
    public Material getIconMaterial() { return iconMaterial; }
    public short getIconData() { return iconData; }
    public List<String> getLore() { return lore; }
    public String getWorldName() { return worldName; }
    public int getOriginX() { return originX; }
    public int getOriginY() { return originY; }
    public int getOriginZ() { return originZ; }
}