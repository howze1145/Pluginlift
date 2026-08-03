package cn.codex.pluginlift;

import cn.codex.pluginlift.core.CollisionBox;
import cn.codex.pluginlift.core.Facing;
import cn.codex.pluginlift.core.GridPos;
import cn.codex.pluginlift.model.CallPanel;
import cn.codex.pluginlift.model.FloorStation;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Owns independently placed hall-call panels and their connector links. */
public final class PanelManager {
    public static final String ENTITY_TYPE = "hall_panel";
    private final PluginLift plugin;
    private final File dataFile;
    private final Map<GridPos, CallPanel> panels = new HashMap<>();
    private final Map<GridPos, List<UUID>> rendered = new HashMap<>();
    private final Map<GridPos, ItemDisplay> displays = new HashMap<>();

    PanelManager(PluginLift plugin) {
        this.plugin = plugin;
        dataFile = new File(plugin.getDataFolder(), "panels-v6.yml");
    }

    public void load() {
        removeRenderers();
        panels.clear();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection root = yaml.getConfigurationSection("panels");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) continue;
            try {
                String stationText = section.getString("station", "");
                GridPos stationRoot = stationText == null || stationText.isBlank() ? null : GridPos.parse(stationText);
                if (stationRoot != null && plugin.stations().get(stationRoot) == null) stationRoot = null;
                CallPanel panel = new CallPanel(GridPos.parse(section.getString("root", "")),
                        Facing.valueOf(section.getString("facing", "SOUTH")), stationRoot);
                panels.put(panel.root(), panel);
                spawn(panel);
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("无法载入呼梯面板 " + key + ": " + exception.getMessage());
            }
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        int index = 0;
        for (CallPanel panel : panels.values()) {
            String path = "panels.p" + index++;
            yaml.set(path + ".root", panel.root().serialize());
            yaml.set(path + ".facing", panel.facing().name());
            yaml.set(path + ".station", panel.linked() ? panel.stationRoot().serialize() : "");
        }
        try {
            yaml.save(dataFile);
        } catch (IOException exception) {
            plugin.getLogger().severe("无法保存 panels-v6.yml: " + exception.getMessage());
        }
    }

    public CallPanel place(Block block, Facing facing) {
        GridPos root = pos(block);
        if (panels.containsKey(root) || plugin.stations().occupies(root) || plugin.lifts().shaftOccupies(root)) {
            throw new IllegalArgumentException("呼梯面板位置已有 PluginLift 组件或电梯竖井");
        }
        if (!block.isEmpty()) throw new IllegalArgumentException("呼梯面板位置必须为空");
        CallPanel panel = new CallPanel(root, facing, null);
        panels.put(root, panel);
        spawn(panel);
        save();
        return panel;
    }

    public CallPanel remove(GridPos root) {
        CallPanel removed = panels.remove(root);
        if (removed == null) return null;
        removeRenderer(root);
        save();
        return removed;
    }

    public void link(CallPanel panel, FloorStation station) {
        if (!panel.root().world().equals(station.root().world())) {
            throw new IllegalArgumentException("呼梯面板和楼层站必须位于同一世界");
        }
        double dx = panel.root().x() - station.root().x();
        double dy = panel.root().y() - station.root().y();
        double dz = panel.root().z() - station.root().z();
        if (dx * dx + dy * dy + dz * dz > 256) {
            throw new IllegalArgumentException("连接距离不能超过 16 格");
        }
        panels.values().stream().filter(other -> other != panel && station.root().equals(other.stationRoot()))
                .forEach(CallPanel::unlink);
        panel.link(station.root());
        save();
        updateAll();
    }

    public void stationRemoved(FloorStation station) {
        boolean changed = false;
        for (CallPanel panel : panels.values()) {
            if (station.root().equals(panel.stationRoot())) {
                panel.unlink();
                changed = true;
            }
        }
        if (changed) {
            save();
            updateAll();
        }
    }

    public CallPanel get(GridPos root) { return panels.get(root); }
    public Collection<CallPanel> all() { return List.copyOf(panels.values()); }
    public boolean occupies(GridPos position) { return panels.containsKey(position); }

    public FloorStation linkedStation(CallPanel panel) {
        return panel == null || !panel.linked() ? null : plugin.stations().get(panel.stationRoot());
    }

    public CallPanel linkedPanel(FloorStation station) {
        return panels.values().stream().filter(panel -> station.root().equals(panel.stationRoot())).findFirst().orElse(null);
    }

    public void updateAll() {
        for (CallPanel panel : panels.values()) {
            ensureRenderer(panel);
            update(panel);
        }
    }

    private void ensureRenderer(CallPanel panel) {
        if (rendererComplete(panel.root())) return;
        removeRenderer(panel.root());
        spawn(panel);
    }

    private boolean rendererComplete(GridPos root) {
        List<UUID> ids = rendered.get(root);
        return ids != null && ids.size() == 2 && ids.stream()
                .map(Bukkit::getEntity).allMatch(entity -> entity != null && entity.isValid());
    }

    public void update(CallPanel panel) {
        ItemDisplay display = displays.get(panel.root());
        if (display == null || !display.isValid()) return;
        String model = "call_panel";
        FloorStation station = linkedStation(panel);
        LiftInstance lift = station == null ? null : plugin.lifts().get(station.liftId());
        if (lift != null) {
            int floor = lift.floorIndex(station);
            boolean up = floor >= 0 && lift.controller().hallRequested(floor, cn.codex.pluginlift.core.CollectiveController.Direction.UP);
            boolean down = floor >= 0 && lift.controller().hallRequested(floor, cn.codex.pluginlift.core.CollectiveController.Direction.DOWN);
            model = up && down ? "call_panel_both" : up ? "call_panel_up" : down ? "call_panel_down" : "call_panel";
        }
        setModel(display, model);
    }

    public List<CollisionBox> collisionBoxes(String world, CollisionBox region) {
        List<CollisionBox> result = new ArrayList<>();
        for (CallPanel panel : panels.values()) {
            if (!panel.root().world().equals(world)) continue;
            CollisionBox box = panelCollision(panel);
            if (box.overlaps(region)) result.add(box);
        }
        return result;
    }

    private static CollisionBox panelCollision(CallPanel panel) {
        GridPos p = panel.root();
        double low = 2.0 / 16.0, high = 14.0 / 16.0, depth = 3.0 / 16.0;
        return switch (panel.facing()) {
            case NORTH -> new CollisionBox(p.x() + low, p.y() + 1.0 / 16.0, p.z(), p.x() + high, p.y() + 15.0 / 16.0, p.z() + depth);
            case SOUTH -> new CollisionBox(p.x() + low, p.y() + 1.0 / 16.0, p.z() + 1 - depth, p.x() + high, p.y() + 15.0 / 16.0, p.z() + 1);
            case WEST -> new CollisionBox(p.x(), p.y() + 1.0 / 16.0, p.z() + low, p.x() + depth, p.y() + 15.0 / 16.0, p.z() + high);
            case EAST -> new CollisionBox(p.x() + 1 - depth, p.y() + 1.0 / 16.0, p.z() + low, p.x() + 1, p.y() + 15.0 / 16.0, p.z() + high);
        };
    }

    private void spawn(CallPanel panel) {
        World world = world(panel.root());
        if (world == null || !world.isChunkLoaded(panel.root().x() >> 4, panel.root().z() >> 4)) return;
        List<UUID> ids = new ArrayList<>();
        Location anchor = center(panel.root());
        ItemDisplay display = world.spawn(anchor, ItemDisplay.class, entity -> {
            tag(entity, panel.root(), "panel_model");
            setModel(entity, "call_panel");
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            entity.setRotation(panel.facing().modelYaw(), 0);
            entity.setTeleportDuration(0);
            entity.setInterpolationDuration(0);
            entity.setPersistent(false);
        });
        displays.put(panel.root(), display);
        ids.add(display.getUniqueId());
        Location hitLocation = new Location(world, panel.root().x() + .5, panel.root().y(), panel.root().z() + .5);
        Interaction hit = world.spawn(hitLocation, Interaction.class, entity -> {
            tag(entity, panel.root(), ENTITY_TYPE);
            entity.setInteractionWidth(.75f);
            entity.setInteractionHeight(1f);
            entity.setResponsive(true);
            entity.setPersistent(false);
        });
        ids.add(hit.getUniqueId());
        rendered.put(panel.root(), ids);
        update(panel);
    }

    public void loadChunk(String world, int chunkX, int chunkZ) {
        panels.values().stream().filter(panel -> panel.root().world().equals(world)
                        && (panel.root().x() >> 4) == chunkX && (panel.root().z() >> 4) == chunkZ
                        && !rendered.containsKey(panel.root())).forEach(this::spawn);
    }

    public void unloadChunk(String world, int chunkX, int chunkZ) {
        panels.values().stream().filter(panel -> panel.root().world().equals(world)
                        && (panel.root().x() >> 4) == chunkX && (panel.root().z() >> 4) == chunkZ)
                .map(CallPanel::root).toList().forEach(this::removeRenderer);
    }

    public void respawnAll() { removeRenderers(); panels.values().forEach(this::spawn); }
    public void removeRenderers() { new ArrayList<>(rendered.keySet()).forEach(this::removeRenderer); }

    private void removeRenderer(GridPos root) {
        displays.remove(root);
        List<UUID> ids = rendered.remove(root);
        if (ids != null) ids.stream().map(Bukkit::getEntity).filter(Objects::nonNull).forEach(Entity::remove);
    }

    private void tag(Entity entity, GridPos root, String type) {
        entity.getPersistentDataContainer().set(plugin.ownedKey(), PersistentDataType.BYTE, (byte) 1);
        entity.getPersistentDataContainer().set(plugin.panelRootKey(), PersistentDataType.STRING, root.serialize());
        entity.getPersistentDataContainer().set(plugin.entityTypeKey(), PersistentDataType.STRING, type);
    }

    private static void setModel(ItemDisplay display, String model) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(new NamespacedKey("pluginlift", model));
        item.setItemMeta(meta);
        display.setItemStack(item);
    }

    private static GridPos pos(Block block) { return new GridPos(block.getWorld().getName(), block.getX(), block.getY(), block.getZ()); }
    private static World world(GridPos position) { return Bukkit.getWorld(position.world()); }
    private static Location center(GridPos position) { return new Location(world(position), position.x() + .5, position.y() + .5, position.z() + .5); }
}