package cn.codex.pluginlift.model;

import cn.codex.pluginlift.core.Facing;
import cn.codex.pluginlift.core.GridPos;

/** Independently placed hall-call panel linked to exactly one landing station. */
public final class CallPanel {
    private final GridPos root;
    private final Facing facing;
    private GridPos stationRoot;

    public CallPanel(GridPos root, Facing facing, GridPos stationRoot) {
        this.root = root;
        this.facing = facing;
        this.stationRoot = stationRoot;
    }

    public GridPos root() { return root; }
    public Facing facing() { return facing; }
    public GridPos stationRoot() { return stationRoot; }
    public boolean linked() { return stationRoot != null; }
    public void link(GridPos stationRoot) { this.stationRoot = stationRoot; }
    public void unlink() { stationRoot = null; }
}