package cn.codex.pluginlift;

import cn.codex.pluginlift.core.CollisionBox;
import cn.codex.pluginlift.core.Facing;
import cn.codex.pluginlift.core.GridPos;
import cn.codex.pluginlift.core.StationLayout;
import cn.codex.pluginlift.model.FloorStation;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class StationManager {
    private final PluginLift plugin;
    private final File dataFile;
    private final File legacyDataFile;
    private final Map<GridPos, FloorStation> stations = new LinkedHashMap<>();
    private final Map<GridPos, GridPos> owners = new HashMap<>();
    private final Map<GridPos, List<UUID>> rendered = new HashMap<>();
    private final Map<GridPos, List<DoorDisplay>> doors = new HashMap<>();

    private final Map<GridPos, TextDisplay> texts = new HashMap<>();

    StationManager(PluginLift plugin) {
        this.plugin = plugin;
        dataFile = new File(plugin.getDataFolder(), "stations-v6.yml");
        legacyDataFile = new File(plugin.getDataFolder(), "stations-v5.yml");
    }

    public void load() {
        removeRenderers(); stations.clear(); owners.clear();
        boolean migrateLegacy = !dataFile.isFile() && legacyDataFile.isFile();
        File source = migrateLegacy ? legacyDataFile : dataFile;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(source);
        ConfigurationSection root = yaml.getConfigurationSection("stations");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key); if (section == null) continue;
            try {
                Facing facing = Facing.valueOf(section.getString("facing", "SOUTH"));
                GridPos stationRoot = GridPos.parse(section.getString("root", ""));
                if (migrateLegacy) stationRoot = stationRoot.offset(facing.viewerRight(), 1);
                FloorStation station = new FloorStation(stationRoot, facing, section.getString("floor.number", "1"),
                        section.getString("floor.description", ""), section.getBoolean("floor.ding", true),
                        section.getString("lift", ""));
                stations.put(station.root(), station); register(station); spawn(station);
            } catch (RuntimeException exception) { plugin.getLogger().warning("无法载入楼层站 " + key + ": " + exception.getMessage()); }
        }
        if (migrateLegacy) {
            save();
            plugin.getLogger().info("已将 stations-v5.yml 的旧面板锚点坐标迁移到 stations-v6.yml 门体坐标；旧文件已保留。");
        }
    }
    public void save() {
        YamlConfiguration yaml = new YamlConfiguration(); int index = 0;
        for (FloorStation station : stations.values()) {
            String path = "stations.s" + index++;
            yaml.set(path + ".root", station.root().serialize()); yaml.set(path + ".facing", station.facing().name());
            yaml.set(path + ".floor.number", station.floorNumber()); yaml.set(path + ".floor.description", station.description());
            yaml.set(path + ".floor.ding", station.ding()); yaml.set(path + ".lift", station.liftId());
        }
        try { yaml.save(dataFile); } catch (IOException exception) { plugin.getLogger().severe("无法保存 stations-v6.yml: " + exception.getMessage()); }
    }

    public FloorStation place(Block block, Facing facing) {
        GridPos root = pos(block); FloorStation station = new FloorStation(root, facing, Integer.toString(root.y()), "", true, "");
        String shaftKey = StationLayout.shaftKey(root, facing);
        for (StationLayout.Cell cell : StationLayout.cells(root, facing)) requireEmpty(cell.position(), shaftKey);
        for (int y = 0; y < 3; y++) for (GridPos cell : StationLayout.cabinCells(root, facing, y)) requireEmpty(cell, shaftKey);
        stations.put(root, station); register(station); spawn(station); save(); return station;
    }
    private void requireEmpty(GridPos position, String shaftKey) {
        if (owners.containsKey(position) || plugin.panels().occupies(position) || plugin.lifts().shaftConflicts(position, shaftKey)) {
            throw new IllegalArgumentException("楼层站需要的空间已有 PluginLift 组件或其他电梯竖井");
        }
        Block block = block(position);
        if (block == null || !block.isEmpty()) throw new IllegalArgumentException("楼层站或 2×2 轿厢空间被方块占用");
    }
    public FloorStation remove(GridPos anyPart) {
        GridPos root = owners.getOrDefault(anyPart, anyPart); FloorStation removed = stations.remove(root); if (removed == null) return null;
        removeRenderer(root); owners.entrySet().removeIf(entry -> entry.getValue().equals(root));
        plugin.lifts().stationRemoved(removed); plugin.panels().stationRemoved(removed); save(); return removed;
    }
    public void configure(FloorStation station, String number, String description, boolean ding) {
        String cleanNumber = number.trim();
        String cleanDescription = description.trim();
        plugin.lifts().validateFloorNumber(station, cleanNumber);
        station.configure(cleanNumber, cleanDescription, ding);
        save();
        plugin.lifts().stationChanged(station);
        update(station);
    }
    public FloorStation get(GridPos anyPart) { GridPos root = owners.getOrDefault(anyPart, anyPart); return stations.get(root); }
    public Collection<FloorStation> all() { return List.copyOf(stations.values()); }
    public boolean occupies(GridPos position) { return owners.containsKey(position); }
    public List<FloorStation> sameShaft(FloorStation station) { return stations.values().stream().filter(value -> value.shaftKey().equals(station.shaftKey())).toList(); }

    private void register(FloorStation station) {
        for (StationLayout.Cell cell : StationLayout.cells(station.root(), station.facing())) owners.put(cell.position(), station.root());
        for (int y = 0; y < 3; y++) for (GridPos cell : StationLayout.cabinCells(station.root(), station.facing(), y)) owners.put(cell, station.root());
    }

    public void updateAll() {
        for (FloorStation station : stations.values()) {
            ensureRenderer(station);
            update(station);
        }
    }

    private void ensureRenderer(FloorStation station) {
        if (rendererComplete(station.root())) return;
        removeRenderer(station.root());
        spawn(station);
    }

    private boolean rendererComplete(GridPos root) {
        List<UUID> ids = rendered.get(root);
        return ids != null && ids.size() == 6 && ids.stream()
                .map(Bukkit::getEntity).allMatch(entity -> entity != null && entity.isValid());
    }
    public void update(FloorStation station) {
        LiftInstance lift = plugin.lifts().get(station.liftId()); int floor = lift == null ? -1 : lift.floorIndex(station);



        String display = station.floorNumber();
        double progress = 0;
        if (lift != null) {
            int shown = lift.controller().targetFloor() >= 0 ? lift.controller().targetFloor() : lift.controller().currentFloor();
            if (shown >= 0 && shown < lift.spec().floors().size()) display = lift.spec().floors().get(shown).floorNumber();
            display += switch (lift.controller().direction()) { case UP -> " ↑"; case DOWN -> " ↓"; default -> ""; };
            if (floor == lift.controller().currentFloor() && lift.controller().state() != cn.codex.pluginlift.core.CollectiveController.State.MOVING) progress = lift.controller().doorProgress();
        }
        TextDisplay text = texts.get(station.root()); if (text != null && text.isValid()) text.text(Component.text(display));
        Facing right = station.facing().viewerRight();
        for (DoorDisplay door : doors.getOrDefault(station.root(), List.of())) {
            double slide = (door.rightLeaf() ? 1 : -1) * progress * 0.78;
            door.display().teleport(door.closed().clone().add(right.x() * slide, 0, right.z() * slide));
        }
    }

    public List<CollisionBox> collisionBoxes(String world, CollisionBox region) {
        List<CollisionBox> result = new ArrayList<>();
        for (FloorStation station : stations.values()) {
            if (!station.root().world().equals(world)) continue;
            LiftInstance lift = plugin.lifts().get(station.liftId()); int floor = lift == null ? -1 : lift.floorIndex(station);
            double progress = lift != null && floor == lift.controller().currentFloor() ? lift.controller().doorProgress() : 0;
            if (progress > 0.03) continue;
            for (CollisionBox box : StationLayout.closedDoorCollision(station.root(), station.facing())) if (box.overlaps(region)) result.add(box);
        }
        return result;
    }

    private void spawn(FloorStation station) {
        if (!allChunksLoaded(station)) return;
        List<UUID> ids = new ArrayList<>(); float yaw = station.facing().modelYaw();

        GridPos left = StationLayout.cells(station.root(), station.facing()).stream().filter(value -> value.part() == StationLayout.Part.DOOR_LEFT_LOWER).findFirst().orElseThrow().position();
        GridPos right = StationLayout.cells(station.root(), station.facing()).stream().filter(value -> value.part() == StationLayout.Part.DOOR_RIGHT_LOWER).findFirst().orElseThrow().position();
        ItemDisplay leftDoor = itemDisplay(center(left), "landing_door_left", yaw, station.root());
        ItemDisplay rightDoor = itemDisplay(center(right), "landing_door_right", yaw, station.root());
        doors.put(station.root(), List.of(new DoorDisplay(leftDoor, leftDoor.getLocation(), false), new DoorDisplay(rightDoor, rightDoor.getLocation(), true)));
        ids.add(leftDoor.getUniqueId()); ids.add(rightDoor.getUniqueId());
        for (StationLayout.Cell cell : StationLayout.cells(station.root(), station.facing())) {
            if (cell.part() == StationLayout.Part.DISPLAY_LEFT || cell.part() == StationLayout.Part.DISPLAY_RIGHT) {
                ItemDisplay display = itemDisplay(center(cell.position()), cell.part() == StationLayout.Part.DISPLAY_LEFT ? "floor_display_left" : "floor_display_right", yaw, station.root()); ids.add(display.getUniqueId());
            }
        }
        StationLayout.Point doorCenter = StationLayout.doorCenter(station.root(), station.facing());
        Location textLocation = new Location(world(station.root()), doorCenter.x(), station.root().y() + 2.52, doorCenter.z()).add(station.facing().x() * 0.51, 0, station.facing().z() * 0.51);
        TextDisplay text = textLocation.getWorld().spawn(textLocation, TextDisplay.class, display -> {
            tag(display, station.root(), "station_text"); display.text(Component.text(station.floorNumber())); display.setBillboard(Display.Billboard.FIXED);
            display.setAlignment(TextDisplay.TextAlignment.CENTER); display.setSeeThrough(false); display.setShadowed(true); display.setRotation(yaw, 0); display.setPersistent(false);
        }); texts.put(station.root(), text); ids.add(text.getUniqueId());


        Location doorHit = new Location(world(station.root()), doorCenter.x(), station.root().y(), doorCenter.z());
        Interaction hit = doorHit.getWorld().spawn(doorHit, Interaction.class, entity -> { tag(entity, station.root(), "station_body"); entity.setInteractionWidth(2f); entity.setInteractionHeight(3f); entity.setResponsive(true); }); ids.add(hit.getUniqueId());
        rendered.put(station.root(), ids); update(station);
    }

    public void loadChunk(String world, int chunkX, int chunkZ) {
        stations.values().stream().filter(value -> touchesChunk(value, world, chunkX, chunkZ)
                && !rendered.containsKey(value.root())).forEach(this::spawn);
    }
    public void unloadChunk(String world, int chunkX, int chunkZ) {
        stations.values().stream().filter(value -> touchesChunk(value, world, chunkX, chunkZ))
                .map(FloorStation::root).toList().forEach(this::removeRenderer);
    }
    private boolean touchesChunk(FloorStation station, String world, int chunkX, int chunkZ) {
        return station.root().world().equals(world) && StationLayout.cells(station.root(), station.facing()).stream()
                .anyMatch(cell -> (cell.position().x() >> 4) == chunkX && (cell.position().z() >> 4) == chunkZ);
    }
    public void respawnAll() { removeRenderers(); stations.values().forEach(this::spawn); }
    public void removeRenderers() { new ArrayList<>(rendered.keySet()).forEach(this::removeRenderer); }
    private void removeRenderer(GridPos root) { texts.remove(root); doors.remove(root); List<UUID> ids = rendered.remove(root); if (ids != null) ids.stream().map(Bukkit::getEntity).filter(java.util.Objects::nonNull).forEach(Entity::remove); }
    private boolean allChunksLoaded(FloorStation station) { World world = world(station.root()); return world != null && StationLayout.cells(station.root(), station.facing()).stream().allMatch(cell -> world.isChunkLoaded(cell.position().x() >> 4, cell.position().z() >> 4)); }
    private ItemDisplay itemDisplay(Location location, String model, float yaw, GridPos root) { return location.getWorld().spawn(location, ItemDisplay.class, display -> { tag(display, root, "station_model"); setModel(display, model); display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE); display.setRotation(yaw, 0); display.setTeleportDuration(1); display.setInterpolationDuration(1); display.setPersistent(false); }); }
    private static void setModel(ItemDisplay display, String model) { ItemStack item = new ItemStack(Material.PAPER); ItemMeta meta = item.getItemMeta(); meta.setItemModel(new NamespacedKey("pluginlift", model)); item.setItemMeta(meta); display.setItemStack(item); }
    private void tag(Entity entity, GridPos root, String type) { entity.getPersistentDataContainer().set(plugin.ownedKey(), PersistentDataType.BYTE, (byte) 1); entity.getPersistentDataContainer().set(plugin.stationRootKey(), PersistentDataType.STRING, root.serialize()); entity.getPersistentDataContainer().set(plugin.entityTypeKey(), PersistentDataType.STRING, type); entity.setPersistent(false); }
    private static GridPos pos(Block block) { return new GridPos(block.getWorld().getName(), block.getX(), block.getY(), block.getZ()); }
    private static Block block(GridPos position) { World world = Bukkit.getWorld(position.world()); return world == null ? null : world.getBlockAt(position.x(), position.y(), position.z()); }
    private static World world(GridPos position) { return Bukkit.getWorld(position.world()); }
    private static Location center(GridPos position) { return new Location(world(position), position.x() + .5, position.y() + .5, position.z() + .5); }
    private record DoorDisplay(ItemDisplay display, Location closed, boolean rightLeaf) { }
}