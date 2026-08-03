package cn.codex.pluginlift;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class LiftListener implements Listener {
    private final PluginLift plugin;
    LiftListener(PluginLift plugin) { this.plugin = plugin; }
    @EventHandler public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return; Entity entity = event.getRightClicked();
        String type = entity.getPersistentDataContainer().get(plugin.entityTypeKey(), PersistentDataType.STRING);
        if (!LiftRenderer.CABIN.equals(type)) return;
        String id = entity.getPersistentDataContainer().get(plugin.liftIdKey(), PersistentDataType.STRING); LiftInstance lift = plugin.lifts().get(id); if (lift == null) return;
        event.setCancelled(true); openMenu(event.getPlayer(), lift);
    }
    private void openMenu(Player player, LiftInstance lift) {
        int size = Math.max(9, Math.min(54, ((lift.spec().floors().size() + 8) / 9) * 9));
        LiftMenu holder = new LiftMenu(lift.spec().id());
        String title = "电梯 " + lift.spec().id() + " " + plugin.lifts().displayName(lift.spec().id());
        Inventory inventory = Bukkit.createInventory(holder, size, Component.text(title)); holder.inventory(inventory);
        for (int i = 0; i < lift.spec().floors().size() && i < 54; i++) {
            var floor = lift.spec().floors().get(i); boolean selected = lift.controller().carRequested(i);
            ItemStack item = new ItemStack(selected ? Material.CYAN_CONCRETE : Material.LIGHT_GRAY_CONCRETE); ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(floor.floorNumber() + (floor.description().isBlank() ? "" : "  " + floor.description())));
            meta.lore(java.util.List.of(Component.text(selected ? "已登记" : "点击选层"))); meta.getPersistentDataContainer().set(plugin.menuFloorKey(), PersistentDataType.INTEGER, i); item.setItemMeta(meta); inventory.setItem(i, item);
        }
        player.openInventory(inventory);
    }
    @EventHandler public void onMenu(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof LiftMenu menu)) return; event.setCancelled(true); if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack item = event.getCurrentItem(); if (item == null || !item.hasItemMeta()) return; Integer floor = item.getItemMeta().getPersistentDataContainer().get(plugin.menuFloorKey(), PersistentDataType.INTEGER); if (floor == null) return;
        LiftInstance lift = plugin.lifts().get(menu.liftId()); if (lift == null) { player.closeInventory(); return; }
        lift.requestCar(floor); player.sendActionBar(Component.text("已选择 " + lift.spec().floors().get(floor).floorNumber())); player.closeInventory();
    }
    @EventHandler public void onJoin(PlayerJoinEvent event) { Bukkit.getScheduler().runTaskLater(plugin, () -> { if (event.getPlayer().isOnline()) plugin.sendResourcePack(event.getPlayer()); }, 20); }
    @EventHandler public void onPack(PlayerResourcePackStatusEvent event) {
        if (!PluginLift.RESOURCE_PACK_ID.equals(event.getID())) return;
        switch (event.getStatus()) { case DECLINED, FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED -> event.getPlayer().sendMessage(Component.text("PluginLift 0.1.4 资源包未加载：" + event.getStatus())); default -> { } }
    }
}