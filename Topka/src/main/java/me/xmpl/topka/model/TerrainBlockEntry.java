package me.xmpl.topka.model;

import org.bukkit.Material;

public class TerrainBlockEntry {
    public final int dx;
    public final int dy;
    public final int dz;
    public final Material material;
    public final byte data;

    public TerrainBlockEntry(int dx, int dy, int dz, Material material, byte data) {
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
        this.material = material;
        this.data = data;
    }
}