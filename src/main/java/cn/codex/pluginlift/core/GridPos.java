package cn.codex.pluginlift.core;

public record GridPos(String world, int x, int y, int z) {
    public GridPos offset(Facing facing, int amount) {
        return new GridPos(world, x + facing.x() * amount, y, z + facing.z() * amount);
    }
    public GridPos up(int amount) { return new GridPos(world, x, y + amount, z); }
    public String serialize() { return world + ";" + x + ";" + y + ";" + z; }
    public static GridPos parse(String value) {
        String[] split = value.split(";", 4);
        if (split.length != 4) throw new IllegalArgumentException("Invalid station position");
        return new GridPos(split[0], Integer.parseInt(split[1]), Integer.parseInt(split[2]), Integer.parseInt(split[3]));
    }
}