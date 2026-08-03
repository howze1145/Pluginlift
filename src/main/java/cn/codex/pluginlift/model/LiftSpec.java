package cn.codex.pluginlift.model;

import cn.codex.pluginlift.core.Facing;
import cn.codex.pluginlift.core.StationLayout;

import java.util.Comparator;
import java.util.List;

public record LiftSpec(String id, String world, Facing facing, double centerX, double centerZ, List<FloorStation> floors) {
    public LiftSpec {
        floors = floors.stream().sorted(Comparator.comparingInt(value -> value.root().y())).toList();
        if (floors.size() < 2) throw new IllegalArgumentException("A lift needs at least two stations");
    }
    public static LiftSpec from(String id, List<FloorStation> stations) {
        if (stations.size() < 2) throw new IllegalArgumentException("至少需要两个对齐的楼层站");
        FloorStation first = stations.get(0);
        if (stations.stream().anyMatch(value -> !value.shaftKey().equals(first.shaftKey()))) throw new IllegalArgumentException("楼层站不属于同一竖井");
        StationLayout.Point center = StationLayout.cabinCenter(first.root(), first.facing());
        return new LiftSpec(id, first.root().world(), first.facing(), center.x(), center.z(), stations);
    }
}