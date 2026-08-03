package cn.codex.pluginlift;

import cn.codex.pluginlift.core.CollisionBox;
import cn.codex.pluginlift.core.GridPos;
import cn.codex.pluginlift.core.StationLayout;
import cn.codex.pluginlift.model.FloorStation;
import cn.codex.pluginlift.model.LiftSpec;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class LiftManager {
    private static final int MAX_LIFT_NUMBER = 9999;
    private static final String DEFAULT_DISPLAY_NAME = "未命名";

    private final PluginLift plugin;
    private final File metadataFile;
    private final Map<String, LiftInstance> lifts = new HashMap<>();
    private final Map<String, String> displayNames = new HashMap<>();

    LiftManager(PluginLift plugin) {
        this.plugin = plugin;
        metadataFile = new File(plugin.getDataFolder(), "lifts-v1.yml");
    }

    public void rebuildFromStations() {
        removeRenderers();
        lifts.clear();
        loadMetadata();

        Map<String, List<FloorStation>> sourceGroups = new LinkedHashMap<>();
        for (FloorStation station : plugin.stations().all()) {
            if (!station.liftId().isBlank()) {
                sourceGroups.computeIfAbsent(station.liftId(), ignored -> new ArrayList<>()).add(station);
            }
        }

        Set<String> reservedIds = new HashSet<>();
        sourceGroups.keySet().stream().map(LiftManager::normalize).filter(LiftManager::isNumberedId)
                .forEach(reservedIds::add);

        Map<String, List<FloorStation>> groups = new LinkedHashMap<>();
        boolean migrated = false;
        for (var entry : sourceGroups.entrySet()) {
            String oldId = entry.getKey();
            String id = normalize(oldId);
            if (!isNumberedId(id)) {
                id = nextAvailableId(reservedIds);
                reservedIds.add(id);
                for (FloorStation station : entry.getValue()) station.setLiftId(id);
                String legacyName = displayNames.remove(normalize(oldId));
                if (legacyName != null && !legacyName.isBlank()) displayNames.put(id, legacyName);
                migrated = true;
                plugin.getLogger().info("已将旧电梯 ID " + oldId + " 迁移为 " + id + "。");
            }
            groups.put(id, entry.getValue());
        }

        for (var entry : groups.entrySet()) {
            try {
                ensureUniqueFloors(entry.getValue());
                if (entry.getValue().size() >= 2) {
                    String id = entry.getKey();
                    lifts.put(id, new LiftInstance(plugin, LiftSpec.from(id, entry.getValue())));
                    displayNames.putIfAbsent(id, DEFAULT_DISPLAY_NAME);
                }
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("无法恢复电梯 " + entry.getKey() + ": " + exception.getMessage());
            }
        }

        displayNames.keySet().retainAll(lifts.keySet());
        if (migrated) plugin.stations().save();
        saveMetadata();
        plugin.stations().updateAll();
        plugin.panels().updateAll();
    }

    public LiftInstance sync(FloorStation selected) {
        List<FloorStation> stations = plugin.stations().sameShaft(selected).stream()
                .sorted(Comparator.comparingInt(value -> value.root().y())).toList();
        if (stations.size() < 2) throw new IllegalArgumentException("同一竖井至少需要两个上下对齐的楼层站");
        ensureUniqueFloors(stations);
        ensureClearShaft(stations);

        List<String> existingIds = stations.stream().map(FloorStation::liftId).filter(value -> !value.isBlank())
                .map(LiftManager::normalize).distinct().toList();
        if (existingIds.size() > 1) throw new IllegalStateException("该竖井的楼层站属于不同电梯，请先拆除错误连接后重新同步");
        String id = existingIds.isEmpty() ? nextAvailableId(currentIds()) : existingIds.get(0);
        if (!isNumberedId(id)) id = nextAvailableId(currentIds());

        LiftInstance old = lifts.remove(id);
        if (old != null) old.removeRenderer();
        for (FloorStation station : stations) station.setLiftId(id);
        plugin.stations().save();
        LiftInstance lift = new LiftInstance(plugin, LiftSpec.from(id, stations));
        lifts.put(id, lift);
        displayNames.putIfAbsent(id, DEFAULT_DISPLAY_NAME);
        saveMetadata();
        plugin.stations().updateAll();
        plugin.panels().updateAll();
        return lift;
    }

    public void validateFloorNumber(FloorStation station, String number) {
        String normalized = number.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) throw new IllegalArgumentException("楼层编号不能为空");
        if (station.liftId().isBlank()) return;
        boolean duplicate = plugin.stations().all().stream()
                .filter(other -> other != station && other.liftId().equalsIgnoreCase(station.liftId()))
                .anyMatch(other -> other.floorNumber().trim().equalsIgnoreCase(normalized));
        if (duplicate) throw new IllegalArgumentException("同一电梯内的楼层编号不能重复");
    }

    private static void ensureUniqueFloors(List<FloorStation> stations) {
        long count = stations.stream().map(value -> value.floorNumber().trim().toLowerCase(Locale.ROOT)).distinct().count();
        if (count != stations.size()) throw new IllegalArgumentException("楼层编号不能重复，请先用楼层设置器修改");
    }

    private static void ensureClearShaft(List<FloorStation> stations) {
        FloorStation first = stations.get(0);
        int minY = first.root().y();
        int maxY = stations.get(stations.size() - 1).root().y() + 2;
        World world = Bukkit.getWorld(first.root().world());
        if (world == null) throw new IllegalArgumentException("楼层站世界未加载");
        for (int y = minY; y <= maxY; y++) {
            for (GridPos pos : StationLayout.cabinCells(first.root(), first.facing(), y - first.root().y())) {
                if (!world.getBlockAt(pos.x(), pos.y(), pos.z()).isEmpty()) {
                    throw new IllegalArgumentException("2×2 竖井中有方块阻挡：" + pos.x() + "," + pos.y() + "," + pos.z());
                }
            }
        }
    }

    public void stationRemoved(FloorStation station) {
        String id = normalize(station.liftId());
        if (id.isBlank()) return;
        LiftInstance old = lifts.remove(id);
        if (old != null) old.removeRenderer();
        List<FloorStation> remaining = plugin.stations().all().stream()
                .filter(value -> value.liftId().equalsIgnoreCase(id)).toList();
        if (remaining.size() < 2) {
            remaining.forEach(value -> value.setLiftId(""));
            displayNames.remove(id);
        } else {
            ensureUniqueFloors(remaining);
            lifts.put(id, new LiftInstance(plugin, LiftSpec.from(id, remaining)));
        }
        plugin.stations().save();
        saveMetadata();
        plugin.stations().updateAll();
        plugin.panels().updateAll();
    }

    public void stationChanged(FloorStation station) {
        if (station.liftId().isBlank()) return;
        String id = normalize(station.liftId());
        List<FloorStation> group = plugin.stations().all().stream()
                .filter(value -> value.liftId().equalsIgnoreCase(id)).toList();
        ensureUniqueFloors(group);
        LiftInstance old = lifts.remove(id);
        if (old != null) old.removeRenderer();
        lifts.put(id, new LiftInstance(plugin, LiftSpec.from(id, group)));
    }

    public boolean delete(String id) {
        String normalized = normalize(id);
        LiftInstance removed = lifts.remove(normalized);
        if (removed == null) return false;
        removed.removeRenderer();
        plugin.stations().all().stream().filter(value -> value.liftId().equalsIgnoreCase(normalized))
                .forEach(value -> value.setLiftId(""));
        displayNames.remove(normalized);
        plugin.stations().save();
        saveMetadata();
        plugin.stations().updateAll();
        plugin.panels().updateAll();
        return true;
    }

    public boolean rename(String id, String name) {
        String normalized = normalize(id);
        if (!lifts.containsKey(normalized)) return false;
        String cleaned = name == null ? "" : name.trim();
        if (cleaned.isEmpty()) throw new IllegalArgumentException("电梯名称不能为空");
        if (cleaned.codePointCount(0, cleaned.length()) > 32) throw new IllegalArgumentException("电梯名称不能超过 32 个字符");
        if (cleaned.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("电梯名称不能包含控制字符");
        displayNames.put(normalized, cleaned);
        saveMetadata();
        return true;
    }

    public String displayName(String id) {
        return displayNames.getOrDefault(normalize(id), DEFAULT_DISPLAY_NAME);
    }

    public void tick() {
        lifts.values().forEach(LiftInstance::tick);
        plugin.stations().updateAll();
        plugin.panels().updateAll();
    }

    public LiftInstance get(String id) { return id == null || id.isBlank() ? null : lifts.get(normalize(id)); }
    public Collection<LiftInstance> all() {
        return lifts.values().stream().sorted(Comparator.comparing(value -> value.spec().id())).toList();
    }
    public void removeRenderers() { lifts.values().forEach(LiftInstance::removeRenderer); }
    public void respawnAll() { lifts.values().forEach(LiftInstance::respawn); }

    public void loadChunk(String world, int chunkX, int chunkZ) {
        lifts.values().stream().filter(lift -> lift.isInChunk(world, chunkX, chunkZ)).forEach(LiftInstance::ensureRenderer);
    }

    public void unloadChunk(String world, int chunkX, int chunkZ) {
        lifts.values().stream().filter(lift -> lift.isInChunk(world, chunkX, chunkZ)).forEach(LiftInstance::removeRenderer);
    }

    public List<CollisionBox> collisionBoxes(String world, CollisionBox region, boolean includeCabinFloor) {
        return lifts.values().stream().filter(value -> value.spec().world().equals(world))
                .flatMap(value -> value.collisionBoxes(region, includeCabinFloor).stream()).toList();
    }

    public boolean shaftOccupies(GridPos position) { return shaftOwner(position) != null; }

    public boolean shaftConflicts(GridPos position, String allowedShaftKey) {
        String owner = shaftOwner(position);
        return owner != null && !owner.equals(allowedShaftKey);
    }

    private String shaftOwner(GridPos position) {
        for (LiftInstance lift : lifts.values()) {
            if (!lift.spec().world().equals(position.world())) continue;
            FloorStation first = lift.spec().floors().get(0);
            int minY = first.root().y();
            int maxY = lift.spec().floors().get(lift.spec().floors().size() - 1).root().y() + 2;
            if (position.y() < minY || position.y() > maxY) continue;
            if (StationLayout.cabinCells(first.root(), first.facing(), position.y() - first.root().y()).contains(position)) {
                return first.shaftKey();
            }
        }
        return null;
    }

    private void loadMetadata() {
        displayNames.clear();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(metadataFile);
        ConfigurationSection root = yaml.getConfigurationSection("lifts");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            String name = root.getString(id + ".name", DEFAULT_DISPLAY_NAME).trim();
            displayNames.put(normalize(id), name.isEmpty() ? DEFAULT_DISPLAY_NAME : name);
        }
    }

    private void saveMetadata() {
        YamlConfiguration yaml = new YamlConfiguration();
        displayNames.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
                yaml.set("lifts." + entry.getKey() + ".name", entry.getValue()));
        try {
            yaml.save(metadataFile);
        } catch (IOException exception) {
            plugin.getLogger().severe("无法保存 lifts-v1.yml: " + exception.getMessage());
        }
    }

    private Set<String> currentIds() {
        Set<String> ids = new HashSet<>(lifts.keySet());
        plugin.stations().all().stream().map(FloorStation::liftId).map(LiftManager::normalize)
                .filter(LiftManager::isNumberedId).forEach(ids::add);
        return ids;
    }

    static String nextAvailableId(Collection<String> usedIds) {
        Set<String> normalized = new HashSet<>();
        usedIds.stream().map(LiftManager::normalize).filter(LiftManager::isNumberedId).forEach(normalized::add);
        for (int number = 1; number <= MAX_LIFT_NUMBER; number++) {
            String candidate = String.format(Locale.ROOT, "%04d", number);
            if (!normalized.contains(candidate)) return candidate;
        }
        throw new IllegalStateException("电梯编号已达到上限 9999");
    }

    static boolean isNumberedId(String id) {
        return id != null && id.matches("\\d{4}") && !id.equals("0000");
    }

    private static String normalize(String id) { return id == null ? "" : id.trim().toLowerCase(Locale.ROOT); }
}