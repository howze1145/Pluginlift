package cn.codex.pluginlift.core;

import java.util.List;

public final class CollisionResolver {
    private CollisionResolver() { }
    public static Motion resolve(CollisionBox moving, double x, double y, double z, List<CollisionBox> obstacles) {
        y = clipY(moving, y, obstacles); moving = moving.shift(0, y, 0);
        if (Math.abs(x) < Math.abs(z)) {
            z = clipZ(moving, z, obstacles); moving = moving.shift(0, 0, z); x = clipX(moving, x, obstacles);
        } else {
            x = clipX(moving, x, obstacles); moving = moving.shift(x, 0, 0); z = clipZ(moving, z, obstacles);
        }
        return new Motion(x, y, z);
    }
    private static double clipX(CollisionBox moving, double amount, List<CollisionBox> obstacles) {
        for (CollisionBox obstacle : obstacles) {
            if (!horizontalVerticalOverlap(moving, obstacle) || !overlap(moving.minZ(), moving.maxZ(), obstacle.minZ(), obstacle.maxZ())) continue;
            if (amount > 0 && moving.maxX() <= obstacle.minX()) amount = Math.min(amount, obstacle.minX() - moving.maxX());
            else if (amount < 0 && moving.minX() >= obstacle.maxX()) amount = Math.max(amount, obstacle.maxX() - moving.minX());
        }
        return amount;
    }
    private static double clipY(CollisionBox moving, double amount, List<CollisionBox> obstacles) {
        for (CollisionBox obstacle : obstacles) {
            if (!overlap(moving.minX(), moving.maxX(), obstacle.minX(), obstacle.maxX()) || !overlap(moving.minZ(), moving.maxZ(), obstacle.minZ(), obstacle.maxZ())) continue;
            if (amount > 0 && moving.maxY() <= obstacle.minY()) amount = Math.min(amount, obstacle.minY() - moving.maxY());
            else if (amount < 0 && moving.minY() >= obstacle.maxY()) amount = Math.max(amount, obstacle.maxY() - moving.minY());
        }
        return amount;
    }
    private static double clipZ(CollisionBox moving, double amount, List<CollisionBox> obstacles) {
        for (CollisionBox obstacle : obstacles) {
            if (!overlap(moving.minX(), moving.maxX(), obstacle.minX(), obstacle.maxX()) || !horizontalVerticalOverlap(moving, obstacle)) continue;
            if (amount > 0 && moving.maxZ() <= obstacle.minZ()) amount = Math.min(amount, obstacle.minZ() - moving.maxZ());
            else if (amount < 0 && moving.minZ() >= obstacle.maxZ()) amount = Math.max(amount, obstacle.maxZ() - moving.minZ());
        }
        return amount;
    }
    private static boolean horizontalVerticalOverlap(CollisionBox moving, CollisionBox obstacle) {
        // A support surface may be a few floating-point units inside the feet; it must not act as a horizontal wall.
        return moving.maxY() > obstacle.minY() && obstacle.maxY() > moving.minY() + 1.0 / 32.0;
    }
    private static boolean overlap(double a1, double a2, double b1, double b2) { return a2 > b1 && a1 < b2; }
    public record Motion(double x, double y, double z) { }
}