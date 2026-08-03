package cn.codex.pluginlift.model;

import cn.codex.pluginlift.core.Facing;
import cn.codex.pluginlift.core.GridPos;
import cn.codex.pluginlift.core.StationLayout;

public final class FloorStation {
    private final GridPos root;
    private final Facing facing;
    private String floorNumber;
    private String description;
    private boolean ding;
    private String liftId;

    public FloorStation(GridPos root, Facing facing) { this(root, facing, "1", "", true, ""); }
    public FloorStation(GridPos root, Facing facing, String floorNumber, String description, boolean ding, String liftId) {
        this.root = root; this.facing = facing; this.floorNumber = floorNumber; this.description = description;
        this.ding = ding; this.liftId = liftId == null ? "" : liftId;
    }
    public GridPos root() { return root; }
    public Facing facing() { return facing; }
    public String floorNumber() { return floorNumber; }
    public String description() { return description; }
    public boolean ding() { return ding; }
    public String liftId() { return liftId; }
    public String shaftKey() { return StationLayout.shaftKey(root, facing); }
    public void configure(String number, String description, boolean ding) { this.floorNumber = number; this.description = description; this.ding = ding; }
    public void setLiftId(String liftId) { this.liftId = liftId == null ? "" : liftId; }
}