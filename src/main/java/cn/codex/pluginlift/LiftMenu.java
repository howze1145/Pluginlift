package cn.codex.pluginlift;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

final class LiftMenu implements InventoryHolder {
    private final String liftId; private Inventory inventory;
    LiftMenu(String liftId) { this.liftId = liftId; }
    String liftId() { return liftId; }
    void inventory(Inventory inventory) { this.inventory = inventory; }
    @Override public @NotNull Inventory getInventory() { return inventory; }
}