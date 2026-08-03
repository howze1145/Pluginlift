package cn.codex.pluginlift.core;

import java.util.ArrayList;
import java.util.List;

/** Two-wide, two-high landing door and two-wide header. Hall panels are independent components. */
public final class StationLayout {
    private StationLayout() { }
    public enum Part { DOOR_LEFT_LOWER, DOOR_LEFT_UPPER, DOOR_RIGHT_LOWER, DOOR_RIGHT_UPPER, DISPLAY_LEFT, DISPLAY_RIGHT }
    public record Cell(Part part, GridPos position) { }
    public record Point(double x, double y, double z) { }

    /**
     * The station root is the lower-left door cell. This keeps rendered models, interaction and
     * collision on the exact cells selected by the builder.
     */
    public static List<Cell> cells(GridPos root, Facing facing) {
        Facing right = facing.viewerRight();
        GridPos left = root;
        GridPos rightDoor = root.offset(right, 1);
        return List.of(
                new Cell(Part.DOOR_LEFT_LOWER, left), new Cell(Part.DOOR_LEFT_UPPER, left.up(1)),
                new Cell(Part.DOOR_RIGHT_LOWER, rightDoor), new Cell(Part.DOOR_RIGHT_UPPER, rightDoor.up(1)),
                new Cell(Part.DISPLAY_LEFT, left.up(2)), new Cell(Part.DISPLAY_RIGHT, rightDoor.up(2)));
    }

    public static Point doorCenter(GridPos root, Facing facing) {
        Facing right = facing.viewerRight();
        return new Point(root.x() + 0.5 + right.x() * 0.5, root.y(), root.z() + 0.5 + right.z() * 0.5);
    }

    public static Point cabinCenter(GridPos root, Facing facing) {
        Point door = doorCenter(root, facing);
        Facing back = facing.opposite();
        return new Point(door.x() + back.x() * 1.5, root.y(), door.z() + back.z() * 1.5);
    }

    public static List<GridPos> cabinCells(GridPos root, Facing facing, int yOffset) {
        Facing right = facing.viewerRight();
        Facing back = facing.opposite();
        GridPos leftDoor = root;
        GridPos rightDoor = root.offset(right, 1);
        return List.of(leftDoor.offset(back, 1).up(yOffset), rightDoor.offset(back, 1).up(yOffset),
                leftDoor.offset(back, 2).up(yOffset), rightDoor.offset(back, 2).up(yOffset));
    }

    public static String shaftKey(GridPos root, Facing facing) {
        Point center = cabinCenter(root, facing);
        return root.world() + ";" + center.x() + ";" + center.z() + ";" + facing.name();
    }

    public static List<CollisionBox> closedDoorCollision(GridPos root, Facing facing) {
        List<CollisionBox> result = new ArrayList<>();
        for (Cell cell : cells(root, facing)) {
            if (!cell.part().name().startsWith("DOOR_")) continue;
            GridPos p = cell.position();
            // A closed landing door owns the full threshold cell. Thin outer and inner planes leave a
            // one-block pocket where players can be trapped between the landing and cabin doors.
            result.add(new CollisionBox(p.x(), p.y(), p.z(), p.x() + 1, p.y() + 1, p.z() + 1));
        }
        return List.copyOf(result);
    }
}