package cn.codex.pluginlift;

import org.bukkit.Material;

public enum ItemType {
    STATION_KIT("楼层站套件", "station_kit", Material.IRON_DOOR, "右键地面放置楼层站"),
    PANEL_KIT("呼梯面板", "panel_kit", Material.COMPARATOR, "右键地面放置呼梯面板"),
    CONNECTOR("组件连接器", "connector", Material.TRIPWIRE_HOOK, "依次右键楼层站和呼梯面板进行连接"),
    SYNC_TOOL("竖井同步器", "sync_tool", Material.BLAZE_ROD, "右键楼层站，将同一竖井内对齐的楼层站组装为电梯"),
    CONFIGURATOR("楼层设置器", "configurator", Material.BRUSH, "右键楼层站，设置楼层编号、描述和到站提示音");

    private final String displayName;
    private final String model;
    private final Material material;
    private final String usage;
    ItemType(String displayName, String model, Material material, String usage) {
        this.displayName = displayName; this.model = model; this.material = material; this.usage = usage;
    }
    public String displayName() { return displayName; }
    public String model() { return model; }
    public Material material() { return material; }
    public String usage() { return usage; }
}