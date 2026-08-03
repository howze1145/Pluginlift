package cn.codex.pluginlift.core;

public record CollisionBox(double minX, double minY, double minZ,
                           double maxX, double maxY, double maxZ) {
    public CollisionBox shift(double x, double y, double z) {
        return new CollisionBox(minX + x, minY + y, minZ + z, maxX + x, maxY + y, maxZ + z);
    }
    public CollisionBox expandDirectional(double x, double y, double z) {
        return new CollisionBox(x < 0 ? minX + x : minX, y < 0 ? minY + y : minY,
                z < 0 ? minZ + z : minZ, x > 0 ? maxX + x : maxX,
                y > 0 ? maxY + y : maxY, z > 0 ? maxZ + z : maxZ);
    }
    public boolean overlaps(CollisionBox other) {
        return maxX > other.minX && minX < other.maxX && maxY > other.minY && minY < other.maxY
                && maxZ > other.minZ && minZ < other.maxZ;
    }
}