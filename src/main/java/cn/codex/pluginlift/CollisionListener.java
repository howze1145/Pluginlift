package cn.codex.pluginlift;

import cn.codex.pluginlift.core.CollisionBox;
import cn.codex.pluginlift.core.CollisionResolver;
import io.papermc.paper.event.entity.EntityMoveEvent;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.List;

/** Applies collision to the virtual station doors and the moving 2x2 cabin. */
public final class CollisionListener implements Listener {
    private static final double EPSILON = 1.0E-9;
    private final PluginLift plugin;

    CollisionListener(PluginLift plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event instanceof PlayerTeleportEvent || event.getPlayer().getGameMode() == GameMode.SPECTATOR) return;
        Location clipped = clip(event.getPlayer(), event.getFrom(), event.getTo());
        if (clipped != null) event.setTo(clipped);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityMove(EntityMoveEvent event) {
        if (event.getEntity() instanceof Player) return;
        Location clipped = clip(event.getEntity(), event.getFrom(), event.getTo());
        if (clipped != null) event.setTo(clipped);
    }

    private Location clip(LivingEntity entity, Location from, Location to) {
        if (from.getWorld() == null || to.getWorld() == null || from.getWorld() != to.getWorld()) return null;
        double x = to.getX() - from.getX(), y = to.getY() - from.getY(), z = to.getZ() - from.getZ();
        if (Math.abs(x) < EPSILON && Math.abs(y) < EPSILON && Math.abs(z) < EPSILON) return null;
        Location current = entity.getLocation();
        BoundingBox box = entity.getBoundingBox();
        CollisionBox moving = new CollisionBox(box.getMinX(), box.getMinY(), box.getMinZ(),
                box.getMaxX(), box.getMaxY(), box.getMaxZ())
                .shift(from.getX() - current.getX(), from.getY() - current.getY(), from.getZ() - current.getZ());
        CollisionBox sweep = moving.expandDirectional(x, y, z);
        List<CollisionBox> obstacles = new ArrayList<>();
        obstacles.addAll(plugin.stations().collisionBoxes(from.getWorld().getName(), sweep));
        obstacles.addAll(plugin.panels().collisionBoxes(from.getWorld().getName(), sweep));
        boolean includeCabinFloor = !(entity instanceof Player player && plugin.playerGravity().isSupporting(player));
        obstacles.addAll(plugin.lifts().collisionBoxes(from.getWorld().getName(), sweep, includeCabinFloor));
        if (obstacles.isEmpty()) return null;
        CollisionResolver.Motion motion = CollisionResolver.resolve(moving, x, y, z, obstacles);
        if (same(x, motion.x()) && same(y, motion.y()) && same(z, motion.z())) return null;
        return new Location(to.getWorld(), from.getX() + motion.x(), from.getY() + motion.y(),
                from.getZ() + motion.z(), to.getYaw(), to.getPitch());
    }

    private static boolean same(double first, double second) { return Math.abs(first - second) < EPSILON; }
}