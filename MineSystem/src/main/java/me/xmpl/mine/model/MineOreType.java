package me.xmpl.mine.model;

import org.bukkit.Material;

public enum MineOreType {
    COAL(Material.COAL_ORE),
    IRON(Material.IRON_ORE),
    GOLD(Material.GOLD_ORE),
    REDSTONE(Material.REDSTONE_ORE),
    LAPIS(Material.LAPIS_ORE),
    QUARTZ(Material.QUARTZ_ORE),
    DIAMOND(Material.DIAMOND_ORE),
    EMERALD(Material.EMERALD_ORE);

    private final Material defaultBlockMaterial;

    MineOreType(Material defaultBlockMaterial) {
        this.defaultBlockMaterial = defaultBlockMaterial;
    }

    public Material getDefaultBlockMaterial() {
        return defaultBlockMaterial;
    }

    /**
     * Определяет тип руды по блоку (GLOWING_REDSTONE_ORE считается как обычный REDSTONE!)
     */
    public static MineOreType fromBlockMaterial(Material material) {
        if (material == null) return null;
        switch (material) {
            case COAL_ORE: return COAL;
            case IRON_ORE: return IRON;
            case GOLD_ORE: return GOLD;
            case REDSTONE_ORE:
            case GLOWING_REDSTONE_ORE: return REDSTONE;
            case LAPIS_ORE: return LAPIS;
            case QUARTZ_ORE: return QUARTZ;
            case DIAMOND_ORE: return DIAMOND;
            case EMERALD_ORE: return EMERALD;
            default: return null;
        }
    }
}