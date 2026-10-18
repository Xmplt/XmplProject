package me.xmpl.wood.model;

public enum WoodTreeType {
    OAK((byte) 0),
    SPRUCE((byte) 1),
    BIRCH((byte) 2),
    JUNGLE((byte) 3),
    ACACIA((byte) 4),
    DARK_OAK((byte) 5);

    private final byte saplingData;

    WoodTreeType(byte saplingData) {
        this.saplingData = saplingData;
    }

    public byte getSaplingData() {
        return saplingData;
    }

    public static WoodTreeType fromSaplingData(byte data) {
        for (WoodTreeType type : values()) {
            if (type.saplingData == data) {
                return type;
            }
        }
        return OAK;
    }
}