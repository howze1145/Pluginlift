package cn.codex.pluginlift;

import cn.codex.pluginlift.core.CollectiveController;
import cn.codex.pluginlift.core.Facing;
import cn.codex.pluginlift.model.FloorStation;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class LiftRenderer {
    public static final String CABIN = "cabin";
    private static final double RIDER_HORIZONTAL_LIMIT = .92;
    private static final double RIDER_RELEASE_HORIZONTAL_LIMIT = 1.12;
    private static final double RIDER_MIN_Y_OFFSET = -.12;
    private static final double RIDER_MAX_Y_OFFSET = .22;
    private static final double RIDER_ALIGNMENT_EPSILON = .015;
    private static final double RIDER_HARD_CORRECTION = .38;
    private static final double RIDER_ARRIVAL_ALIGNMENT_EPSILON = 1.0E-4;
    private static final double RIDER_CORRECTION_GAIN = .35;
    private static final double RIDER_MAX_CORRECTION = .06;
    private static final double MOTION_EPSILON = 1.0E-8;
    private static final double DOOR_LEAF_CENTER_OFFSET = .5;

    private final PluginLift plugin;
    private final LiftInstance lift;
    private final List<Entity> entities = new ArrayList<>();
    private final Map<UUID, RiderState> supportedRiders = new HashMap<>();
    private double previousCabinDelta;
    private ItemDisplay shell, leftDoor, rightDoor;
    private TextDisplay indicator;
    private Interaction interaction;

    LiftRenderer(PluginLift plugin, LiftInstance lift) { this.plugin = plugin; this.lift = lift; }

    public void spawn() {
        World world = Bukkit.getWorld(lift.spec().world());
        if (world == null || !world.isChunkLoaded(((int) Math.floor(lift.spec().centerX())) >> 4,
                ((int) Math.floor(lift.spec().centerZ())) >> 4)) return;
        Location anchor = modelAnchor(lift.controller().position());
        shell = item(anchor, "cabin_shell");
        leftDoor = item(anchor, "cabin_door_left");
        rightDoor = item(anchor, "cabin_door_right");
        Facing facing = lift.spec().facing();
        Location textLoc = cabinBase(lift.controller().position()).add(facing.x() * 1.02, 1.72, facing.z() * 1.02);
        indicator = world.spawn(textLoc, TextDisplay.class, display -> {
            tag(display, "cabin_indicator");
            display.text(Component.text(lift.spec().floors().get(0).floorNumber()));
            display.setBillboard(Display.Billboard.FIXED);
            display.setAlignment(TextDisplay.TextAlignment.CENTER);
            display.setSeeThrough(false);
            display.setShadowed(true);
            display.setRotation(facing.modelYaw(), 0);
            display.setTeleportDuration(1);
            display.setInterpolationDuration(1);
            display.setPersistent(false);
        });
        entities.add(indicator);
        interaction = world.spawn(cabinBase(lift.controller().position()), Interaction.class, entity -> {
            tag(entity, CABIN);
            entity.setInteractionWidth(1.8f);
            entity.setInteractionHeight(2.4f);
            entity.setResponsive(true);
            entity.setPersistent(false);
        });
        entities.add(interaction);
        update(null);
    }

    private ItemDisplay item(Location location, String model) {
        ItemDisplay display = location.getWorld().spawn(location, ItemDisplay.class, spawned -> {
            tag(spawned, "cabin_model");
            setModel(spawned, model);
            spawned.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            spawned.setRotation(lift.spec().facing().modelYaw(), 0);
            spawned.setTeleportDuration(1);
            spawned.setInterpolationDuration(1);
            spawned.setPersistent(false);
        });
        entities.add(display);
        return display;
    }

    public void update(CollectiveController.TickResult result) {
        double y = lift.controller().position();
        Location anchor = modelAnchor(y);
        Facing facing = lift.spec().facing();
        Facing right = facing.viewerRight();
        if (shell != null) shell.teleport(anchor);
        double open = lift.controller().doorProgress() * .78;
        if (leftDoor != null) leftDoor.teleport(anchor.clone().add(-right.x() * (DOOR_LEAF_CENTER_OFFSET + open), 0, -right.z() * (DOOR_LEAF_CENTER_OFFSET + open)));
        if (rightDoor != null) rightDoor.teleport(anchor.clone().add(right.x() * (DOOR_LEAF_CENTER_OFFSET + open), 0, right.z() * (DOOR_LEAF_CENTER_OFFSET + open)));
        if (indicator != null) {
            indicator.teleport(cabinBase(y).add(facing.x() * 1.02, 1.72, facing.z() * 1.02));
            int shown = lift.controller().targetFloor() >= 0 ? lift.controller().targetFloor() : lift.controller().currentFloor();
            String number = lift.spec().floors().get(shown).floorNumber();
            String arrow = switch (lift.controller().direction()) { case UP -> " ↑"; case DOWN -> " ↓"; default -> ""; };
            indicator.text(Component.text(number + arrow));
        }
        if (interaction != null) interaction.teleport(cabinBase(y));
        if (result != null && result.previousState() != result.state()) {
            World world = anchor.getWorld();
            if (result.state() == CollectiveController.State.OPENING) {
                FloorStation floor = lift.spec().floors().get(lift.controller().currentFloor());
                if (floor.ding()) world.playSound(anchor, Sound.BLOCK_NOTE_BLOCK_BELL, .8f, 1.3f);
                world.playSound(anchor, Sound.BLOCK_IRON_DOOR_OPEN, .7f, 1f);
            } else if (result.state() == CollectiveController.State.CLOSING) {
                world.playSound(anchor, Sound.BLOCK_IRON_DOOR_CLOSE, .7f, 1f);
            }
        }
    }

    /**
     * Carries riders with continuous Y velocity while remembering their floor-relative height.
     * A tracked rider is not dropped merely because client movement arrives one tick late.
     */
    public void moveRiders(CollectiveController.TickResult result, double oldY) {
        double deltaY = result.delta();
        double newY = lift.controller().position();
        World world = Bukkit.getWorld(lift.spec().world());
        if (world == null) {
            releaseRiders();
            return;
        }

        Map<UUID, RiderState> nextRiders = new HashMap<>();

        // Continue known riders first. Their saved floor offset is the stable source of truth;
        // the narrow boarding window is intentionally not reapplied while the cabin is moving.
        for (Map.Entry<UUID, RiderState> entry : supportedRiders.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (!eligibleRider(player, world, RIDER_RELEASE_HORIZONTAL_LIMIT)
                    || isPlayerJumping(player, previousCabinDelta)) {
                if (player != null) plugin.playerGravity().release(player, false);
                continue;
            }
            RiderState updated = carryRider(player, entry.getValue(), oldY, deltaY, result.arrived(), false);
            nextRiders.put(entry.getKey(), updated);
        }

        // Only first-time boarding uses the tight floor window. A rider released during this tick
        // is not reacquired until a later tick, which prevents release/capture oscillation.
        Location searchCenter = new Location(world, lift.spec().centerX(), (oldY + newY) * .5 + .4, lift.spec().centerZ());
        for (Player player : world.getNearbyPlayers(searchCenter, 2.5)) {
            UUID playerId = player.getUniqueId();
            if (supportedRiders.containsKey(playerId) || nextRiders.containsKey(playerId)
                    || !eligibleRider(player, world, RIDER_HORIZONTAL_LIMIT)
                    || isPlayerJumping(player, deltaY)) continue;
            Location location = player.getLocation();
            double offsetFromOldFloor = location.getY() - oldY;
            double offsetFromNewFloor = location.getY() - newY;
            boolean nearOldFloor = between(offsetFromOldFloor, RIDER_MIN_Y_OFFSET, RIDER_MAX_Y_OFFSET);
            boolean nearNewFloor = between(offsetFromNewFloor, RIDER_MIN_Y_OFFSET, RIDER_MAX_Y_OFFSET);
            if (!nearOldFloor && !nearNewFloor) continue;

            double floorOffset = Math.abs(offsetFromOldFloor) <= Math.abs(offsetFromNewFloor)
                    ? offsetFromOldFloor : offsetFromNewFloor;
            plugin.playerGravity().support(player);
            RiderState updated = carryRider(player, new RiderState(floorOffset, false), oldY,
                    deltaY, result.arrived(), true);
            nextRiders.put(playerId, updated);
        }

        supportedRiders.clear();
        supportedRiders.putAll(nextRiders);
        previousCabinDelta = deltaY;
    }

    private RiderState carryRider(Player player, RiderState state, double oldY, double deltaY,
                                  boolean arrived, boolean newlySupported) {
        plugin.playerGravity().support(player);
        if (arrived) return alignRiderAtArrival(player, oldY + deltaY);

        double expectedY = oldY + state.floorOffset();
        double error = player.getLocation().getY() - expectedY;

        // This is a rare recovery only: normal following is velocity based. It catches a delayed
        // client before an ascending cabin leaves it behind.
        if (Math.abs(error) > RIDER_HARD_CORRECTION) {
            Location corrected = player.getLocation();
            corrected.setY(expectedY);
            player.teleport(corrected, PlayerTeleportEvent.TeleportCause.PLUGIN);
            error = 0;
        }

        double correction = clamp(error * RIDER_CORRECTION_GAIN, -RIDER_MAX_CORRECTION, RIDER_MAX_CORRECTION);
        boolean motionNeeded = Math.abs(deltaY) > MOTION_EPSILON || Math.abs(error) > RIDER_ALIGNMENT_EPSILON;
        boolean mustStopPreviousMotion = !motionNeeded
                && (state.verticalMotionActive() || Math.abs(previousCabinDelta) > MOTION_EPSILON || newlySupported);
        if (motionNeeded || mustStopPreviousMotion) {
            Vector velocity = player.getVelocity();
            velocity.setY(motionNeeded ? deltaY - correction : 0);
            player.setVelocity(velocity);
        }
        player.setFallDistance(0);
        return new RiderState(state.floorOffset(), motionNeeded);
    }

    /** Aligns feet to the exact landing height once, then stops the final carry velocity. */
    private RiderState alignRiderAtArrival(Player player, double landingY) {
        Location current = player.getLocation();
        if (Math.abs(current.getY() - landingY) > RIDER_ARRIVAL_ALIGNMENT_EPSILON) {
            Location corrected = current.clone();
            corrected.setY(landingY);
            player.teleport(corrected, PlayerTeleportEvent.TeleportCause.PLUGIN);
        }
        Vector velocity = player.getVelocity();
        if (Math.abs(velocity.getY()) > MOTION_EPSILON) {
            velocity.setY(0);
            player.setVelocity(velocity);
        }
        player.setFallDistance(0);
        return new RiderState(0, false);
    }

    private boolean eligibleRider(Player player, World world, double horizontalLimit) {
        if (player == null || !player.isValid() || player.isDead() || player.getWorld() != world
                || player.getGameMode() == GameMode.SPECTATOR || player.isFlying()
                || !plugin.playerGravity().canSupport(player)) return false;
        Location location = player.getLocation();
        return Math.abs(location.getX() - lift.spec().centerX()) < horizontalLimit
                && Math.abs(location.getZ() - lift.spec().centerZ()) < horizontalLimit;
    }

    private static boolean isPlayerJumping(Player player, double cabinVelocity) {
        if (player == null) return false;
        return player.getVelocity().getY() > Math.max(.24, cabinVelocity + .08);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static boolean between(double value, double minimum, double maximum) {
        return value >= minimum && value <= maximum;
    }

    private void releaseRiders() {
        for (UUID id : supportedRiders.keySet()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) plugin.playerGravity().release(player, false);
        }
        supportedRiders.clear();
        previousCabinDelta = 0;
    }

    private record RiderState(double floorOffset, boolean verticalMotionActive) { }

    public boolean isSpawned() {
        return shell != null && shell.isValid()
                && leftDoor != null && leftDoor.isValid()
                && rightDoor != null && rightDoor.isValid()
                && indicator != null && indicator.isValid()
                && interaction != null && interaction.isValid();
    }

    public void remove() {
        releaseRiders();
        entities.forEach(entity -> { if (entity.isValid()) entity.remove(); });
        entities.clear();
    }

    private Location cabinBase(double y) { return new Location(Bukkit.getWorld(lift.spec().world()), lift.spec().centerX(), y, lift.spec().centerZ()); }
    private Location modelAnchor(double y) { return new Location(Bukkit.getWorld(lift.spec().world()), lift.spec().centerX(), y + 1, lift.spec().centerZ()); }

    private void tag(Entity entity, String type) {
        entity.getPersistentDataContainer().set(plugin.ownedKey(), PersistentDataType.BYTE, (byte) 1);
        entity.getPersistentDataContainer().set(plugin.entityTypeKey(), PersistentDataType.STRING, type);
        entity.getPersistentDataContainer().set(plugin.liftIdKey(), PersistentDataType.STRING, lift.spec().id());
    }

    private static void setModel(ItemDisplay display, String model) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(new NamespacedKey("pluginlift", model));
        item.setItemMeta(meta);
        display.setItemStack(item);
    }

}
