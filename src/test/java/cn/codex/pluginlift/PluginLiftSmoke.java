package cn.codex.pluginlift;

import cn.codex.pluginlift.core.CollectiveController;
import cn.codex.pluginlift.core.CollisionBox;
import cn.codex.pluginlift.core.CollisionResolver;
import cn.codex.pluginlift.core.Facing;
import cn.codex.pluginlift.core.GridPos;
import cn.codex.pluginlift.core.StationLayout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PluginLiftSmoke {
    private static final Path ROOT = Path.of(System.getProperty("user.dir"));

    public static void main(String[] args) throws Exception {
        controllerSmoke();
        stationLayoutSmoke();
        collisionSmoke();
        liftIdentitySmoke();
        managementImplementationSmoke();
        resourcePackSmoke();
        datapackSmoke();
        itemTextSmoke();
        riderImplementationSmoke();
        System.out.println("PluginLift 0.1.4 smoke tests passed");
    }

    private static void controllerSmoke() {
        CollectiveController sameDirection = controller();
        sameDirection.requestCar(3);
        sameDirection.tick();
        sameDirection.tick();
        sameDirection.requestHall(1, CollectiveController.Direction.UP);
        check(sameDirection.targetFloor() == 1, "same-direction en-route call must become the next stop");
        runUntilArrival(sameDirection);
        check(sameDirection.currentFloor() == 1, "same-direction hall call must be served before the farther car call");
        check(sameDirection.carRequested(3), "farther car call must remain queued");

        CollectiveController reverse = controller();
        reverse.requestCar(3);
        reverse.tick();
        reverse.tick();
        reverse.requestHall(1, CollectiveController.Direction.DOWN);
        check(reverse.targetFloor() == 3, "opposite-direction hall call must not reverse the moving cabin early");
        runUntilArrival(reverse);
        check(reverse.currentFloor() == 3, "cabin must reach the upward endpoint before reversing");
        runUntilFloor(reverse, 1);
        check(reverse.currentFloor() == 1, "opposite-direction call must be served after reversal");

        CollectiveController limits = controller();
        expectFailure(() -> limits.requestHall(3, CollectiveController.Direction.UP), "top floor up call must be rejected");
        expectFailure(() -> limits.requestHall(0, CollectiveController.Direction.DOWN), "bottom floor down call must be rejected");
        limits.requestCar(2);
        check(limits.carRequested(2), "car selection must be queued");

        CollectiveController doors = new CollectiveController(new double[]{0, 4}, 0, 20, 2, 2);
        doors.requestCar(0);
        check(doors.state() == CollectiveController.State.OPENING, "current-floor request must open immediately");
        doors.tick();
        doors.tick();
        check(doors.state() == CollectiveController.State.OPEN && close(doors.doorProgress(), 1), "door must fully open");
        doors.tick();
        doors.requestCar(0);
        doors.tick();
        check(doors.state() == CollectiveController.State.OPEN, "current-floor request must extend door hold time");
        for (int i = 0; i < 8; i++) doors.tick();
        check(doors.state() == CollectiveController.State.IDLE && close(doors.doorProgress(), 0), "door must complete the close cycle");

        CollectiveController obstruction = new CollectiveController(new double[]{0, 4}, 0, 20, 2, 1);
        obstruction.requestCar(0);
        obstruction.tick();
        obstruction.tick();
        obstruction.tick();
        check(obstruction.state() == CollectiveController.State.CLOSING, "door safety test must reach closing state");
        obstruction.requestCar(obstruction.currentFloor());
        check(obstruction.state() == CollectiveController.State.OPENING, "current-floor obstruction must reverse a closing door");
    }

    private static CollectiveController controller() {
        return new CollectiveController(new double[]{0, 4, 8, 12}, 0, 20, 1, 1);
    }

    private static void runUntilArrival(CollectiveController controller) {
        for (int i = 0; i < 200; i++) if (controller.tick().arrived()) return;
        throw new AssertionError("controller did not arrive");
    }

    private static void runUntilFloor(CollectiveController controller, int floor) {
        for (int i = 0; i < 500; i++) {
            CollectiveController.TickResult result = controller.tick();
            if (result.arrived() && controller.currentFloor() == floor) return;
        }
        throw new AssertionError("controller did not reach floor " + floor);
    }

    private static void stationLayoutSmoke() {
        GridPos root = new GridPos("world", 20, 64, -10);
        for (Facing facing : Facing.values()) {
            List<StationLayout.Cell> cells = StationLayout.cells(root, facing);
            check(cells.size() == 6, "station must contain six station cells after panel separation");
            check(new HashSet<>(cells.stream().map(StationLayout.Cell::position).toList()).size() == 6,
                    "station component cells must be unique for " + facing);
            GridPos left = part(cells, StationLayout.Part.DOOR_LEFT_LOWER);
            check(left.equals(root), "station root must be the rendered lower-left door cell");
            GridPos right = part(cells, StationLayout.Part.DOOR_RIGHT_LOWER);
            check(right.equals(left.offset(facing.viewerRight(), 1)), "landing door must be exactly two cells wide");
            check(part(cells, StationLayout.Part.DOOR_LEFT_UPPER).equals(left.up(1)), "left door must be two cells high");
            check(part(cells, StationLayout.Part.DOOR_RIGHT_UPPER).equals(right.up(1)), "right door must be two cells high");

            List<GridPos> cabin = StationLayout.cabinCells(root, facing, 0);
            check(cabin.size() == 4 && new HashSet<>(cabin).size() == 4, "cabin footprint must be 2x2");
            StationLayout.Point center = StationLayout.cabinCenter(root, facing);
            double averageX = cabin.stream().mapToDouble(value -> value.x() + .5).average().orElseThrow();
            double averageZ = cabin.stream().mapToDouble(value -> value.z() + .5).average().orElseThrow();
            check(close(center.x(), averageX) && close(center.z(), averageZ), "cabin center must match its four cells");

            List<CollisionBox> doors = StationLayout.closedDoorCollision(root, facing);
            check(doors.size() == 4, "closed landing door must have four one-cell collision boxes");
            for (CollisionBox box : doors) {
                double x = box.maxX() - box.minX(), y = box.maxY() - box.minY(), z = box.maxZ() - box.minZ();
                check(close(y, 1), "each landing collision segment must be one block high");
                check(close(x, 1) && close(y, 1) && close(z, 1),
                        "closed landing doors must fill the whole threshold cell to prevent trapping pockets");
            }
        }
        check(Facing.SOUTH.modelYaw() == 0 && Facing.WEST.modelYaw() == 90
                && Facing.NORTH.modelYaw() == 180 && Facing.EAST.modelYaw() == 270, "model yaw mapping changed");
    }

    private static GridPos part(List<StationLayout.Cell> cells, StationLayout.Part part) {
        return cells.stream().filter(value -> value.part() == part).findFirst().orElseThrow().position();
    }

    private static void collisionSmoke() {
        CollisionBox moving = new CollisionBox(.2, 0, .2, .8, 1.8, .8);
        CollisionBox wall = new CollisionBox(1, 0, 0, 2, 2, 1);
        CollisionResolver.Motion hit = CollisionResolver.resolve(moving, 1, 0, 0, List.of(wall));
        check(close(hit.x(), .2), "horizontal collision must stop at the wall");
        CollisionBox floor = new CollisionBox(0, -1, 0, 1, 0, 1);
        CollisionResolver.Motion fall = CollisionResolver.resolve(moving.shift(0, 1, 0), 0, -2, 0, List.of(floor));
        check(close(fall.y(), -1), "vertical collision must stop on the floor");
        CollisionBox belowFeet = new CollisionBox(-1, -.125, -1, 2, 0, 2);
        CollisionResolver.Motion walkAcrossFloor = CollisionResolver.resolve(moving.shift(0, -.01, 0), 1, 0, 0, List.of(belowFeet));
        check(close(walkAcrossFloor.x(), 1), "floor below foot level must not block horizontal elevator entry");
        CollisionResolver.Motion free = CollisionResolver.resolve(moving, 0, 0, 1, List.of(wall));
        check(close(free.z(), 1), "unobstructed axis must remain unchanged");
    }

    private static void liftIdentitySmoke() {
        check(LiftManager.nextAvailableId(List.of()).equals("0001"), "first elevator ID must be 0001");
        check(LiftManager.nextAvailableId(List.of("0001", "0002", "0004")).equals("0003"),
                "new elevator must fill the lowest available ID");
        check(LiftManager.nextAvailableId(List.of("lift_old", "0001", "0010")).equals("0002"),
                "legacy IDs must not block numbered allocation");
        check(LiftManager.isNumberedId("0001") && LiftManager.isNumberedId("9999")
                        && !LiftManager.isNumberedId("0000") && !LiftManager.isNumberedId("001"),
                "elevator IDs must use exactly four digits from 0001 to 9999");
    }
    private static void managementImplementationSmoke() throws IOException {
        String manager = Files.readString(ROOT.resolve("src/main/java/cn/codex/pluginlift/LiftManager.java"));
        check(manager.contains("lifts-v1.yml") && manager.contains("displayNames")
                        && manager.contains("public boolean rename(String id, String name)"),
                "elevator display names must be stored independently from internal IDs");
        check(manager.contains("nextAvailableId(currentIds())")
                        && manager.contains("String.format(Locale.ROOT, \"%04d\", number)"),
                "new elevators must use the lowest available four-digit ID");
        check(manager.contains("已将旧电梯 ID") && manager.contains("station.setLiftId(id)"),
                "legacy shaft-hash IDs must migrate to numbered IDs");
        check(manager.contains("Comparator.comparing(value -> value.spec().id())"),
                "elevator lists must be sorted by internal ID");

        String command = Files.readString(ROOT.resolve("src/main/java/cn/codex/pluginlift/PluginLiftCommand.java"));
        check(command.contains("case \"rename\"") && command.contains("/pluginlift rename <ID> <名称>")
                        && command.contains("lift.spec().floors().size() + \"层\""),
                "rename and detailed list commands must remain available");
        String stationManager = Files.readString(ROOT.resolve("src/main/java/cn/codex/pluginlift/StationManager.java"));
        check(stationManager.contains("stations = new LinkedHashMap<>()"),
                "station persistence must preserve creation order for deterministic legacy migration");
    }
    private static void resourcePackSmoke() throws IOException {
        Path pack = ROOT.resolve("resource-pack");
        check(Files.isRegularFile(pack.resolve("pack.mcmeta")), "resource pack metadata missing");
        check(Files.isRegularFile(pack.resolve("pack.png")), "resource pack icon missing");
        String defaultConfig = Files.readString(ROOT.resolve("src/main/resources/config.yml"));
        check(defaultConfig.contains("https://mcpacks.dev/pack/9391ab9e-1a96-4ecd-b96a-9e3c6477aa46/download")
                        && defaultConfig.contains("4aa8cccb4502a5163c39d8a1b99ce6659a40f90f"),
                "default config must point to the 0.1.4 resource pack");
        List<String> names = List.of("station_kit", "panel_kit", "connector", "sync_tool", "configurator", "call_panel", "call_panel_up",
                "call_panel_down", "call_panel_both", "landing_door_left", "landing_door_right",
                "floor_display_left", "floor_display_right", "cabin_shell", "cabin_door_left", "cabin_door_right");
        for (String name : names) {
            Path definition = pack.resolve("assets/pluginlift/items/" + name + ".json");
            Path model = pack.resolve("assets/pluginlift/models/item/" + name + ".json");
            check(Files.isRegularFile(definition), "missing item definition " + name);
            check(Files.isRegularFile(model), "missing item model " + name);
            check(Files.readString(definition).contains("pluginlift:item/" + name), "definition points to wrong model: " + name);
            String modelText = Files.readString(model);
            verifyTextureReferences(pack, modelText, name);
            verifyModelElementBounds(modelText, name);
        }
        String leftCabinDoor = Files.readString(pack.resolve("assets/pluginlift/models/item/cabin_door_left.json"));
        String rightCabinDoor = Files.readString(pack.resolve("assets/pluginlift/models/item/cabin_door_right.json"));
        List<Double> leftCabinBounds = modelElementBounds(leftCabinDoor);
        List<Double> rightCabinBounds = modelElementBounds(rightCabinDoor);
        check(leftCabinBounds.equals(rightCabinBounds),
                "left and right cabin doors must use identical local geometry");
        check(leftCabinBounds.subList(0, 6).equals(List.of(0.0, -6.0, 22.0, 16.0, 30.0, 24.0)),
                "cabin door leaf must keep the same centered vertical range");
        check(leftCabinDoor.contains("29.5") && leftCabinDoor.contains("-5.5") && leftCabinDoor.contains("\"uv\""),
                "both cabin doors must have identical visible caps and explicit full-height UV mapping");
        for (String side : List.of("left", "right")) {
            String landing = Files.readString(pack.resolve("assets/pluginlift/models/item/landing_door_" + side + ".json"));
            String header = Files.readString(pack.resolve("assets/pluginlift/models/item/floor_display_" + side + ".json"));
            check(modelElementBounds(landing).subList(0, 6).equals(List.of(0.0, -0.02, 12.98, 16.0, 32.0, 16.02)),
                    "landing door must overlap adjoining header and floor without a light gap: " + side);
            check(modelElementBounds(header).subList(0, 6).equals(List.of(0.0, -0.02, 12.98, 16.0, 16.02, 16.02)),
                    "floor display must fill its complete header cell: " + side);
            check(landing.contains("pluginlift:block/metal_light") && header.contains("pluginlift:block/metal_light"),
                    "landing outer seams must be covered by metal trim: " + side);
        }
        try (var paths = Files.walk(pack)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                String normalized = path.toString().replace('\\', '/').toLowerCase();
                check(!normalized.contains("/mtr/"), "MTR namespace is forbidden: " + normalized);
                if (path.getFileName().toString().endsWith(".json")) {
                    check(!Files.readString(path).toLowerCase().contains("\"mtr:"), "MTR model reference is forbidden: " + path);
                }
            }
        }
    }

    private static List<Double> modelElementBounds(String modelText) {
        Matcher matcher = Pattern.compile("\"(?:from|to)\"\\s*:\\s*\\[\\s*(-?\\d+(?:\\.\\d+)?)\\s*,\\s*(-?\\d+(?:\\.\\d+)?)\\s*,\\s*(-?\\d+(?:\\.\\d+)?)\\s*\\]").matcher(modelText);
        java.util.ArrayList<Double> bounds = new java.util.ArrayList<>();
        while (matcher.find()) for (int index = 1; index <= 3; index++) bounds.add(Double.parseDouble(matcher.group(index)));
        return List.copyOf(bounds);
    }
    private static void verifyModelElementBounds(String modelText, String model) {
        if (!modelText.contains("\"elements\"")) return;
        List<Double> bounds = modelElementBounds(modelText);
        check(!bounds.isEmpty() && bounds.size() % 6 == 0, "model has malformed element bounds: " + model);
        for (double value : bounds) {
            check(value >= -16 && value <= 32, "model element coordinate exceeds Minecraft range [-16, 32]: " + model + " = " + value);
        }
        for (int index = 0; index < bounds.size(); index += 6) {
            check(bounds.get(index) < bounds.get(index + 3)
                            && bounds.get(index + 1) < bounds.get(index + 4)
                            && bounds.get(index + 2) < bounds.get(index + 5),
                    "model element must have positive volume: " + model);
        }
    }
    private static void verifyTextureReferences(Path pack, String modelText, String model) {
        Matcher matcher = Pattern.compile("pluginlift:(block|item)/([a-z0-9_./-]+)").matcher(modelText);
        Set<String> references = new HashSet<>();
        while (matcher.find()) references.add(matcher.group(1) + "/" + matcher.group(2));
        check(!references.isEmpty(), "model has no original texture reference: " + model);
        for (String reference : references) {
            check(Files.isRegularFile(pack.resolve("assets/pluginlift/textures/" + reference + ".png")),
                    "missing texture " + reference + " for " + model);
        }
    }

    private static void datapackSmoke() throws IOException {
        Path pack = ROOT.resolve("datapack");
        check(Files.isRegularFile(pack.resolve("pack.mcmeta")), "datapack metadata missing");
        for (String name : List.of("station_kit", "panel_kit", "connector", "sync_tool", "configurator")) {
            Path recipe = pack.resolve("data/pluginlift/recipe/" + name + ".json");
            check(Files.isRegularFile(recipe), "missing recipe " + name);
            String text = Files.readString(recipe);
            check(text.contains("\"minecraft:item_model\": \"pluginlift:" + name + "\""), "recipe model mismatch: " + name);
            check(Files.isRegularFile(ROOT.resolve("resource-pack/assets/pluginlift/items/" + name + ".json")), "recipe has no item definition: " + name);
        }
    }


    private static void itemTextSmoke() throws IOException {
        Path recipes = ROOT.resolve("datapack/data/pluginlift/recipe");
        List<String> recipeFiles = List.of("station_kit", "panel_kit", "connector", "sync_tool", "configurator");
        List<String> displayNames = List.of("楼层站套件", "呼梯面板", "组件连接器", "竖井同步器", "楼层设置器");
        String itemSource = Files.readString(ROOT.resolve("src/main/java/cn/codex/pluginlift/ItemType.java"));
        for (int index = 0; index < recipeFiles.size(); index++) {
            String name = displayNames.get(index);
            String recipe = Files.readString(recipes.resolve(recipeFiles.get(index) + ".json"));
            check(recipe.contains("\"minecraft:custom_name\": {\"text\": \"" + name + "\""),
                    "datapack item name mismatch: " + recipeFiles.get(index));
            check(itemSource.contains("\"" + name + "\""), "plugin item name missing: " + name);
        }
        for (String obsolete : List.of("独立呼梯面板", "不再包含呼梯面板", "单独放置呼梯面板", "完整原创楼层站")) {
            check(!itemSource.contains(obsolete), "obsolete item wording remains in plugin: " + obsolete);
            for (String recipeFile : recipeFiles) {
                check(!Files.readString(recipes.resolve(recipeFile + ".json")).contains(obsolete),
                        "obsolete item wording remains in recipe: " + obsolete);
            }
        }
    }

    private static void riderImplementationSmoke() throws IOException {
        String source = Files.readString(ROOT.resolve("src/main/java/cn/codex/pluginlift/LiftRenderer.java"));
        check(!source.contains("Shulker") && !source.contains("cabin_floor_platform")
                        && !source.contains("floorPlatform") && !source.contains("Attribute.SCALE"),
                "cabin must not create a hidden mob or entity collision platform");
        check(source.contains("double deltaY = result.delta()")
                        && source.contains("Vector velocity = player.getVelocity()")
                        && source.contains("deltaY - correction")
                        && source.contains("player.setVelocity(velocity)"),
                "riders must follow cabin Y velocity with bounded error correction and preserve X/Z velocity");
        check(!source.contains("player.setGravity")
                        && !source.contains("player.setFlying") && !source.contains("player.setAllowFlight"),
                "renderer must not directly alter gravity or any flight state");
        check(source.split("player\\.teleport\\(", -1).length == 3
                        && source.contains("RIDER_HARD_CORRECTION")
                        && source.contains("RIDER_ARRIVAL_ALIGNMENT_EPSILON"),
                "player teleport must be limited to guarded recovery and one-time arrival alignment");
        check(source.contains("Map<UUID, RiderState> supportedRiders")
                        && source.contains("RIDER_RELEASE_HORIZONTAL_LIMIT")
                        && source.contains("supportedRiders.containsKey(playerId)"),
                "known riders must remain tracked outside the narrow first-boarding floor window");
        check(source.contains("playerGravity().support(player)")
                        && source.contains("playerGravity().release(player, false)")
                        && source.contains("isPlayerJumping"),
                "virtual-floor support must preserve jumps and release gravity without stopping vertical motion");
        check(source.contains("verticalMotionActive")
                        && source.contains("mustStopPreviousMotion")
                        && source.contains("MOTION_EPSILON"),
                "idle support must stop Y once without resending velocity every tick");
        check(source.contains("RIDER_HORIZONTAL_LIMIT") && source.contains("RIDER_MIN_Y_OFFSET")
                        && source.contains("GameMode.SPECTATOR"),
                "rider movement must only select non-spectator players already inside the cabin");
        check(source.contains("shell != null && shell.isValid()")
                        && source.contains("interaction != null && interaction.isValid()"),
                "renderer health must require the complete cabin entity set");

        String collisionSource = Files.readString(ROOT.resolve("src/main/java/cn/codex/pluginlift/CollisionListener.java"));
        check(!collisionSource.contains("cabin_floor_platform") && !collisionSource.contains("isCabinFloorPlatform"),
                "collision listener must not retain hidden-platform special cases");
        check(collisionSource.contains("playerGravity().isSupporting(player)")
                        && collisionSource.contains("includeCabinFloor"),
                "supported riders must not collide twice with the moving cabin floor");

        String pluginSource = Files.readString(ROOT.resolve("src/main/java/cn/codex/pluginlift/PluginLift.java"));
        check(pluginSource.contains("MISLABELED_RESOURCE_PACK_URL") && pluginSource.contains("legacyPack || brokenPack || previousPack || mislabeledPack"),
                "servers using any earlier or mislabeled bundled pack must migrate to the corrected pack automatically");
        String gravitySource = Files.readString(ROOT.resolve("src/main/java/cn/codex/pluginlift/PlayerGravityController.java"));
        check(gravitySource.contains("rider_gravity_controlled") && gravitySource.contains("rider_previous_gravity")
                        && gravitySource.contains("void recover(Player player)")
                        && gravitySource.contains("boolean isSupporting(Player player)" ),
                "players left without gravity by older builds must still be repaired on startup and join");
        check(source.contains("alignRiderAtArrival(player, oldY + deltaY)")
                        && source.contains("return new RiderState(0, false)")
                        && source.contains("RIDER_ARRIVAL_ALIGNMENT_EPSILON")
                        && !source.contains("RIDER_ARRIVAL_CORRECTION"),
                "arrival must align rider feet to the exact landing and clear the saved boarding offset");

        check(source.contains("DOOR_LEAF_CENTER_OFFSET") && source.contains("DOOR_LEAF_CENTER_OFFSET + open"),
                "cabin door leaves must share one centered model and symmetric entity offsets");

        String liftSource = Files.readString(ROOT.resolve("src/main/java/cn/codex/pluginlift/LiftInstance.java"));
        check(liftSource.contains("public void tick()") && liftSource.contains("ensureRenderer();")
                        && liftSource.contains("!renderer.isSpawned()") && liftSource.contains("world.isChunkLoaded"),
                "killed cabin displays must rebuild automatically without loading inactive chunks");
        check(liftSource.contains("y - .125") && liftSource.contains("maxX, y, maxZ"),
                "cabin floor collision must stay below the walking surface");
        check(liftSource.contains("holdDoorsForPlayers") && liftSource.contains("doorwayOccupied")
                        && liftSource.contains("controller.requestCar(controller.currentFloor())"),
                "doorway obstruction must hold or reopen both doors");

        String stationSource = Files.readString(ROOT.resolve("src/main/java/cn/codex/pluginlift/StationManager.java"));
        String panelSource = Files.readString(ROOT.resolve("src/main/java/cn/codex/pluginlift/PanelManager.java"));
        check(stationSource.contains("rendererComplete") && stationSource.contains("ids.size() == 6")
                        && stationSource.contains("entity != null && entity.isValid()"),
                "killed landing components must rebuild automatically");
        check(panelSource.contains("rendererComplete") && panelSource.contains("ids.size() == 2")
                        && panelSource.contains("entity != null && entity.isValid()"),
                "killed call-panel components must rebuild automatically");
    }    private static void expectFailure(Runnable action, String message) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError(message);
    }

    private static boolean close(double first, double second) { return Math.abs(first - second) < 1.0E-8; }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
