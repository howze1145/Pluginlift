package cn.codex.pluginlift.core;

import java.util.NavigableSet;
import java.util.TreeSet;

/** Collective-selective hall-call controller inspired by MTR's passenger-facing behavior. */
public final class CollectiveController {
    public enum State { IDLE, MOVING, OPENING, OPEN, CLOSING }
    public enum Direction { UP, DOWN, NONE }
    public record TickResult(double delta, State previousState, State state, boolean arrived) { }

    private final double[] floors;
    private final NavigableSet<Integer> carCalls = new TreeSet<>();
    private final NavigableSet<Integer> upCalls = new TreeSet<>();
    private final NavigableSet<Integer> downCalls = new TreeSet<>();
    private final double speedPerTick;
    private final int doorMoveTicks;
    private final int doorOpenTicks;
    private int currentFloor;
    private int targetFloor = -1;
    private int waitTicks;
    private double position;
    private double doorProgress;
    private Direction direction = Direction.NONE;
    private State state = State.IDLE;

    public CollectiveController(double[] floors, int initialFloor, double speedBlocksPerSecond, int doorMoveTicks, int doorOpenTicks) {
        if (floors.length < 2) throw new IllegalArgumentException("A lift needs at least two floors");
        this.floors = floors.clone();
        for (int i = 1; i < floors.length; i++) if (floors[i] <= floors[i - 1]) throw new IllegalArgumentException("Floors must be ascending");
        if (initialFloor < 0 || initialFloor >= floors.length) throw new IllegalArgumentException("Invalid initial floor");
        currentFloor = initialFloor; position = floors[initialFloor];
        speedPerTick = Math.max(0.01, speedBlocksPerSecond / 20.0);
        this.doorMoveTicks = Math.max(1, doorMoveTicks); this.doorOpenTicks = Math.max(1, doorOpenTicks);
    }

    public void requestCar(int floor) {
        validate(floor);
        if (serveCurrentIfPossible(floor)) return;
        carCalls.add(floor);
        considerRetarget(floor, true, Direction.NONE);
    }
    public void requestHall(int floor, Direction requestedDirection) {
        validate(floor);
        if (requestedDirection == Direction.NONE) throw new IllegalArgumentException("Hall call needs a direction");
        if (requestedDirection == Direction.UP && floor == floors.length - 1) throw new IllegalArgumentException("Top floor has no up call");
        if (requestedDirection == Direction.DOWN && floor == 0) throw new IllegalArgumentException("Bottom floor has no down call");
        if (serveCurrentIfPossible(floor)) return;
        (requestedDirection == Direction.UP ? upCalls : downCalls).add(floor);
        considerRetarget(floor, false, requestedDirection);
    }
    private boolean serveCurrentIfPossible(int floor) {
        if (floor != currentFloor || state == State.MOVING) return false;
        if (state == State.IDLE || state == State.CLOSING) state = State.OPENING;
        else if (state == State.OPEN) waitTicks = doorOpenTicks;
        return true;
    }
    private void considerRetarget(int floor, boolean carCall, Direction hallDirection) {
        if (state != State.MOVING || targetFloor < 0 || floor == currentFloor) return;
        if (direction == Direction.UP && floors[floor] > position && floor < targetFloor
                && (carCall || hallDirection == Direction.UP)) targetFloor = floor;
        if (direction == Direction.DOWN && floors[floor] < position && floor > targetFloor
                && (carCall || hallDirection == Direction.DOWN)) targetFloor = floor;
    }

    public TickResult tick() {
        State previous = state; double old = position; boolean arrived = false;
        switch (state) {
            case IDLE -> beginNext();
            case MOVING -> {
                double destination = floors[targetFloor];
                double difference = destination - position;
                if (Math.abs(difference) <= speedPerTick) {
                    position = destination; currentFloor = targetFloor; targetFloor = -1; arrived = true;
                    clearServed(currentFloor); state = State.OPENING;
                } else position += Math.copySign(speedPerTick, difference);
            }
            case OPENING -> {
                clearServed(currentFloor);
                doorProgress = Math.min(1, doorProgress + 1.0 / doorMoveTicks);
                if (doorProgress >= 1) { waitTicks = doorOpenTicks; state = State.OPEN; }
            }
            case OPEN -> { if (--waitTicks <= 0) state = State.CLOSING; }
            case CLOSING -> {
                doorProgress = Math.max(0, doorProgress - 1.0 / doorMoveTicks);
                if (doorProgress <= 0) { state = State.IDLE; beginNext(); }
            }
        }
        return new TickResult(position - old, previous, state, arrived);
    }

    private void beginNext() {
        if (!hasAny()) { direction = Direction.NONE; targetFloor = -1; state = State.IDLE; return; }
        if (requestedAt(currentFloor)) { clearServed(currentFloor); state = State.OPENING; return; }
        Integer next = switch (direction) {
            case UP -> nextUp();
            case DOWN -> nextDown();
            case NONE -> nearestAny();
        };
        if (next == null) { direction = Direction.NONE; state = State.IDLE; return; }
        targetFloor = next;
        direction = next > currentFloor ? Direction.UP : Direction.DOWN;
        state = State.MOVING;
    }

    private Integer nextUp() {
        Integer serviceable = minAbove(union(carCalls, upCalls));
        if (serviceable != null) return serviceable;
        Integer anyAbove = maxAbove(allCalls());
        if (anyAbove != null) return anyAbove;
        direction = Direction.DOWN;
        return nextDown();
    }
    private Integer nextDown() {
        Integer serviceable = maxBelow(union(carCalls, downCalls));
        if (serviceable != null) return serviceable;
        Integer anyBelow = minBelow(allCalls());
        if (anyBelow != null) return anyBelow;
        direction = Direction.UP;
        return nextUp();
    }
    private Integer nearestAny() {
        Integer above = minAbove(allCalls()); Integer below = maxBelow(allCalls());
        if (above == null) return below; if (below == null) return above;
        return above - currentFloor <= currentFloor - below ? above : below;
    }

    private void clearServed(int floor) {
        carCalls.remove(floor);
        if (direction == Direction.UP) {
            upCalls.remove(floor);
            if (!hasAbove(floor)) downCalls.remove(floor);
        } else if (direction == Direction.DOWN) {
            downCalls.remove(floor);
            if (!hasBelow(floor)) upCalls.remove(floor);
        } else {
            upCalls.remove(floor); downCalls.remove(floor);
        }
    }
    private boolean requestedAt(int floor) { return carCalls.contains(floor) || upCalls.contains(floor) || downCalls.contains(floor); }
    private boolean hasAny() { return !(carCalls.isEmpty() && upCalls.isEmpty() && downCalls.isEmpty()); }
    private boolean hasAbove(int floor) { return allCalls().stream().anyMatch(value -> value > floor); }
    private boolean hasBelow(int floor) { return allCalls().stream().anyMatch(value -> value < floor); }
    private NavigableSet<Integer> allCalls() { return union(union(carCalls, upCalls), downCalls); }
    private static NavigableSet<Integer> union(NavigableSet<Integer> a, NavigableSet<Integer> b) { TreeSet<Integer> result = new TreeSet<>(a); result.addAll(b); return result; }
    private Integer minAbove(NavigableSet<Integer> values) { return values.higher(currentFloor); }
    private Integer maxAbove(NavigableSet<Integer> values) { return values.isEmpty() || values.last() <= currentFloor ? null : values.last(); }
    private Integer maxBelow(NavigableSet<Integer> values) { return values.lower(currentFloor); }
    private Integer minBelow(NavigableSet<Integer> values) { return values.isEmpty() || values.first() >= currentFloor ? null : values.first(); }
    private void validate(int floor) { if (floor < 0 || floor >= floors.length) throw new IllegalArgumentException("Invalid floor"); }

    public int currentFloor() { return currentFloor; }
    public int targetFloor() { return targetFloor; }
    public double position() { return position; }
    public double doorProgress() { return doorProgress; }
    public Direction direction() { return direction; }
    public State state() { return state; }
    public boolean carRequested(int floor) { return carCalls.contains(floor); }
    public boolean hallRequested(int floor, Direction direction) { return (direction == Direction.UP ? upCalls : downCalls).contains(floor); }
    public boolean anyRequested(int floor) { return requestedAt(floor); }
}