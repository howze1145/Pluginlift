package cn.codex.pluginlift;

import cn.codex.pluginlift.core.CollectiveController;
import cn.codex.pluginlift.core.CollisionBox;
import cn.codex.pluginlift.core.Facing;
import cn.codex.pluginlift.core.GridPos;
import cn.codex.pluginlift.core.StationLayout;
import cn.codex.pluginlift.model.FloorStation;
import cn.codex.pluginlift.model.LiftSpec;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.List;

public final class LiftInstance {
    private final PluginLift plugin;
    private final LiftSpec spec;
    private final CollectiveController controller;
    private LiftRenderer renderer;

    LiftInstance(PluginLift plugin, LiftSpec spec) {
        this.plugin = plugin; this.spec = spec;
        double[] floors = spec.floors().stream().mapToDouble(value -> value.root().y()).toArray();
        controller = new CollectiveController(floors, 0, plugin.getConfig().getDouble("defaults.speed-blocks-per-second", 3),
                plugin.getConfig().getInt("defaults.door-move-ticks", 16), plugin.getConfig().getInt("defaults.door-open-ticks", 60));
        respawn();
    }
    public void tick() {
        ensureRenderer();
        holdDoorsForPlayers();
        CollectiveController.TickResult result = controller.tick();
        if (renderer != null) {
            double oldY = controller.position() - result.delta();
            renderer.moveRiders(result, oldY); renderer.update(result);
        }
    }

    /** Keeps the landing and cabin doors open while a player occupies the one-block doorway. */
    private void holdDoorsForPlayers() {
        CollectiveController.State state = controller.state();
        if (state != CollectiveController.State.OPEN && state != CollectiveController.State.CLOSING
                && state != CollectiveController.State.IDLE) return;
        if (doorwayOccupied(state == CollectiveController.State.IDLE)) controller.requestCar(controller.currentFloor());
    }

    private boolean doorwayOccupied(boolean centerOnly) {
        World world = Bukkit.getWorld(spec.world());
        if (world == null) return false;
        FloorStation station = spec.floors().get(controller.currentFloor());
        GridPos first = station.root();
        GridPos second = first.offset(station.facing().viewerRight(), 1);
        double minX = Math.min(first.x(), second.x());
        double maxX = Math.max(first.x(), second.x()) + 1;
        double minZ = Math.min(first.z(), second.z());
        double maxZ = Math.max(first.z(), second.z()) + 1;
        CollisionBox doorway = new CollisionBox(minX, first.y() - .05, minZ, maxX, first.y() + 2.2, maxZ);
        StationLayout.Point center = StationLayout.doorCenter(first, station.facing());
        Location search = new Location(world, center.x(), first.y() + 1, center.z());
        for (Player player : world.getNearbyPlayers(search, 2.6)) {
            if (player.getGameMode() == GameMode.SPECTATOR) continue;
            if (centerOnly) {
                Location location = player.getLocation();
                if (location.getX() > doorway.minX() && location.getX() < doorway.maxX()
                        && location.getY() > doorway.minY() && location.getY() < doorway.maxY()
                        && location.getZ() > doorway.minZ() && location.getZ() < doorway.maxZ()) return true;
            } else {
                BoundingBox box = player.getBoundingBox();
                CollisionBox playerBox = new CollisionBox(box.getMinX(), box.getMinY(), box.getMinZ(),
                        box.getMaxX(), box.getMaxY(), box.getMaxZ());
                if (playerBox.overlaps(doorway)) return true;
            }
        }
        return false;
    }
    public void requestHall(int floor, CollectiveController.Direction direction) { controller.requestHall(floor, direction); }
    public void requestCar(int floor) { controller.requestCar(floor); }
    public int floorIndex(FloorStation station) { for (int i = 0; i < spec.floors().size(); i++) if (spec.floors().get(i).root().equals(station.root())) return i; return -1; }
    public List<CollisionBox> collisionBoxes(CollisionBox region, boolean includeFloor) {
        double y = controller.position(); double minX = spec.centerX() - 1, maxX = spec.centerX() + 1, minZ = spec.centerZ() - 1, maxZ = spec.centerZ() + 1;
        List<CollisionBox> boxes = new ArrayList<>();
        // Keep the floor collision below foot level so horizontal entry is never mistaken for a wall.
        if (includeFloor) boxes.add(new CollisionBox(minX, y - .125, minZ, maxX, y, maxZ));
        boxes.add(new CollisionBox(minX, y + 2.45, minZ, maxX, y + 2.55, maxZ));
        Facing front = spec.facing(); double t = .1;
        if (front == Facing.NORTH || front == Facing.SOUTH) {
            boxes.add(new CollisionBox(minX, y, minZ, minX + t, y + 2.5, maxZ));
            boxes.add(new CollisionBox(maxX - t, y, minZ, maxX, y + 2.5, maxZ));
            double backZ = front == Facing.SOUTH ? minZ : maxZ;
            boxes.add(front == Facing.SOUTH ? new CollisionBox(minX, y, backZ, maxX, y + 2.5, backZ + t) : new CollisionBox(minX, y, backZ - t, maxX, y + 2.5, backZ));
            if (controller.doorProgress() < .03) {
                double frontZ = front == Facing.SOUTH ? maxZ : minZ;
                boxes.add(front == Facing.SOUTH ? new CollisionBox(minX, y, frontZ - t, maxX, y + 2.5, frontZ) : new CollisionBox(minX, y, frontZ, maxX, y + 2.5, frontZ + t));
            }
        } else {
            boxes.add(new CollisionBox(minX, y, minZ, maxX, y + 2.5, minZ + t));
            boxes.add(new CollisionBox(minX, y, maxZ - t, maxX, y + 2.5, maxZ));
            double backX = front == Facing.EAST ? minX : maxX;
            boxes.add(front == Facing.EAST ? new CollisionBox(backX, y, minZ, backX + t, y + 2.5, maxZ) : new CollisionBox(backX - t, y, minZ, backX, y + 2.5, maxZ));
            if (controller.doorProgress() < .03) {
                double frontX = front == Facing.EAST ? maxX : minX;
                boxes.add(front == Facing.EAST ? new CollisionBox(frontX - t, y, minZ, frontX, y + 2.5, maxZ) : new CollisionBox(frontX, y, minZ, frontX + t, y + 2.5, maxZ));
            }
        }
        return boxes.stream().filter(value -> value.overlaps(region)).toList();
    }
    public void respawn() {
        removeRenderer();
        renderer = new LiftRenderer(plugin, this);
        renderer.spawn();
    }
    public void ensureRenderer() {
        World world = Bukkit.getWorld(spec.world());
        if (world == null || !world.isChunkLoaded(((int) Math.floor(spec.centerX())) >> 4,
                ((int) Math.floor(spec.centerZ())) >> 4)) return;
        if (renderer == null || !renderer.isSpawned()) respawn();
    }
    public boolean isInChunk(String world, int chunkX, int chunkZ) {
        return spec.world().equals(world)
                && (((int) Math.floor(spec.centerX())) >> 4) == chunkX
                && (((int) Math.floor(spec.centerZ())) >> 4) == chunkZ;
    }
    public void removeRenderer() { if (renderer != null) renderer.remove(); renderer = null; }
    public LiftSpec spec() { return spec; }
    public CollectiveController controller() { return controller; }
}