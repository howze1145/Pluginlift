package cn.codex.pluginlift.core;

public enum Facing {
    NORTH(0, -1), EAST(1, 0), SOUTH(0, 1), WEST(-1, 0);

    private final int x;
    private final int z;

    Facing(int x, int z) { this.x = x; this.z = z; }
    public int x() { return x; }
    public int z() { return z; }
    public Facing opposite() { return values()[(ordinal() + 2) & 3]; }
    public Facing clockwise() { return values()[(ordinal() + 1) & 3]; }
    public Facing counterClockwise() { return values()[(ordinal() + 3) & 3]; }
    /** Right-hand direction for a player standing in front of the station. */
    public Facing viewerRight() { return counterClockwise(); }
    /** Models are authored facing south. */
    public float modelYaw() {
        return switch (this) { case SOUTH -> 0; case WEST -> 90; case NORTH -> 180; case EAST -> 270; };
    }
}