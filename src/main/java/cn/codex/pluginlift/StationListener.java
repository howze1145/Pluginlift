package cn.codex.pluginlift;

import cn.codex.pluginlift.core.CollectiveController;
import cn.codex.pluginlift.core.Facing;
import cn.codex.pluginlift.core.GridPos;
import cn.codex.pluginlift.model.CallPanel;
import cn.codex.pluginlift.model.FloorStation;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Placement, configuration, connector linking and hall-call interaction. */
public final class StationListener implements Listener {
    private final PluginLift plugin;
    private final Map<UUID, GridPos> edits = new ConcurrentHashMap<>();
    private final Map<UUID, GridPos> connectorStations = new ConcurrentHashMap<>();
    private final Map<UUID, GridPos> connectorPanels = new ConcurrentHashMap<>();

    StationListener(PluginLift plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlace(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null) return;
        ItemType type = plugin.items().type(event.getItem());
        if (type != ItemType.STATION_KIT && type != ItemType.PANEL_KIT) return;
        event.setCancelled(true);
        if (event.getBlockFace() != BlockFace.UP) {
            event.getPlayer().sendActionBar(Component.text(type == ItemType.STATION_KIT ? "请右键地面放置楼层站" : "请右键地面放置呼梯面板"));
            return;
        }
        Block target = event.getClickedBlock().getRelative(BlockFace.UP);
        Facing facing = fromBlockFace(event.getPlayer().getFacing()).opposite();
        try {
            if (type == ItemType.STATION_KIT) {
                plugin.stations().place(target, facing);
                event.getPlayer().sendActionBar(Component.text("楼层站已放置；请放置呼梯面板并用组件连接器连接"));
            } else {
                plugin.panels().place(target, facing);
                event.getPlayer().sendActionBar(Component.text("呼梯面板已放置；请用组件连接器连接楼层站"));
            }
            consume(event.getPlayer());
        } catch (IllegalArgumentException exception) {
            event.getPlayer().sendActionBar(Component.text(exception.getMessage()));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onComponentInteract(PlayerInteractAtEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        GridPos stationRoot = stationRoot(event.getRightClicked());
        GridPos panelRoot = panelRoot(event.getRightClicked());
        if (stationRoot == null && panelRoot == null) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        ItemType held = plugin.items().type(player.getInventory().getItemInMainHand());

        if (panelRoot != null) {
            CallPanel panel = plugin.panels().get(panelRoot);
            if (panel == null) return;
            if (held == ItemType.CONNECTOR) {
                selectPanel(player, panel);
                return;
            }
            handleHallCall(event, player, panel);
            return;
        }

        FloorStation station = plugin.stations().get(stationRoot);
        if (station == null) return;
        if (held == ItemType.CONNECTOR) {
            selectStation(player, station);
            return;
        }
        if (held == ItemType.CONFIGURATOR) {
            edits.put(player.getUniqueId(), station.root());
            player.sendMessage(Component.text("请输入：楼层编号 | 楼层描述 | 提示音开/关；输入取消退出"));
            return;
        }
        if (held == ItemType.SYNC_TOOL) {
            try {
                LiftInstance lift = plugin.lifts().sync(station);
                player.sendActionBar(Component.text("已组装 " + lift.spec().id() + "，共 " + lift.spec().floors().size() + " 层"));
            } catch (IllegalArgumentException exception) {
                player.sendActionBar(Component.text(exception.getMessage()));
            }
            return;
        }
        CallPanel panel = plugin.panels().linkedPanel(station);
        String panelState = panel == null ? "；未连接呼梯面板" : "；呼梯面板已连接";
        player.sendActionBar(Component.text(station.liftId().isBlank() ? "楼层站尚未同步" + panelState : station.floorNumber() + "  " + station.description() + panelState));
    }

    private void selectStation(Player player, FloorStation station) {
        GridPos selectedPanel = connectorPanels.remove(player.getUniqueId());
        if (selectedPanel == null) {
            connectorStations.put(player.getUniqueId(), station.root());
            player.sendActionBar(Component.text("已选择楼层站，请再右键呼梯面板"));
            return;
        }
        CallPanel panel = plugin.panels().get(selectedPanel);
        if (panel == null) {
            player.sendActionBar(Component.text("之前选择的呼梯面板已不存在，请重新连接"));
            return;
        }
        completeLink(player, panel, station);
    }

    private void selectPanel(Player player, CallPanel panel) {
        GridPos selectedStation = connectorStations.remove(player.getUniqueId());
        if (selectedStation == null) {
            connectorPanels.put(player.getUniqueId(), panel.root());
            player.sendActionBar(Component.text("已选择呼梯面板，请再右键对应楼层站"));
            return;
        }
        FloorStation station = plugin.stations().get(selectedStation);
        if (station == null) {
            player.sendActionBar(Component.text("之前选择的楼层站已不存在，请重新连接"));
            return;
        }
        completeLink(player, panel, station);
    }

    private void completeLink(Player player, CallPanel panel, FloorStation station) {
        try {
            plugin.panels().link(panel, station);
            connectorStations.remove(player.getUniqueId());
            connectorPanels.remove(player.getUniqueId());
            player.sendActionBar(Component.text("连接完成：呼梯面板 → 楼层 " + station.floorNumber()));
        } catch (IllegalArgumentException exception) {
            player.sendActionBar(Component.text(exception.getMessage()));
        }
    }

    private void handleHallCall(PlayerInteractAtEntityEvent event, Player player, CallPanel panel) {
        FloorStation station = plugin.panels().linkedStation(panel);
        if (station == null) {
            player.sendActionBar(Component.text("呼梯面板尚未连接楼层站"));
            return;
        }
        LiftInstance lift = plugin.lifts().get(station.liftId());
        if (lift == null) {
            player.sendActionBar(Component.text("对应楼层站尚未用竖井同步器组装"));
            return;
        }
        int floor = lift.floorIndex(station);
        boolean upper = event.getClickedPosition().getY() >= .5;
        CollectiveController.Direction direction = upper ? CollectiveController.Direction.UP : CollectiveController.Direction.DOWN;
        if ((direction == CollectiveController.Direction.UP && floor == lift.spec().floors().size() - 1)
                || (direction == CollectiveController.Direction.DOWN && floor == 0)) {
            player.sendActionBar(Component.text("这个方向在当前楼层不可用"));
            return;
        }
        try {
            lift.requestHall(floor, direction);
            plugin.panels().update(panel);
            player.sendActionBar(Component.text(direction == CollectiveController.Direction.UP ? "已登记上行呼梯" : "已登记下行呼梯"));
        } catch (IllegalArgumentException exception) {
            player.sendActionBar(Component.text(exception.getMessage()));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onChat(AsyncChatEvent event) {
        GridPos root = edits.get(event.getPlayer().getUniqueId());
        if (root == null) return;
        event.setCancelled(true);
        String input = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> applyConfig(event.getPlayer(), root, input));
    }

    private void applyConfig(Player player, GridPos root, String input) {
        if (input.equalsIgnoreCase("cancel") || input.equals("取消")) {
            edits.remove(player.getUniqueId());
            player.sendMessage(Component.text("已取消楼层设置"));
            return;
        }
        FloorStation station = plugin.stations().get(root);
        if (station == null) {
            edits.remove(player.getUniqueId());
            player.sendMessage(Component.text("目标楼层站已不存在"));
            return;
        }
        try {
            StationConfig config = StationConfig.parse(input, station.ding());
            plugin.stations().configure(station, config.number(), config.description(), config.ding());
            edits.remove(player.getUniqueId());
            player.sendMessage(Component.text("楼层已设置为 " + config.number() + " | " + config.description() + " | 提示音" + (config.ding() ? "开" : "关")));
        } catch (IllegalArgumentException exception) {
            player.sendMessage(Component.text(exception.getMessage()));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        edits.remove(id);
        connectorStations.remove(id);
        connectorPanels.remove(id);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent event) {
        GridPos stationRoot = stationRoot(event.getEntity());
        GridPos panelRoot = panelRoot(event.getEntity());
        if (stationRoot == null && panelRoot == null) return;
        event.setCancelled(true);
        if (!(event instanceof EntityDamageByEntityEvent byEntity) || !(byEntity.getDamager() instanceof Player player)
                || !player.hasPermission("pluginlift.admin")) return;
        if (panelRoot != null) {
            CallPanel removed = plugin.panels().remove(panelRoot);
            if (removed != null) {
                player.getInventory().addItem(plugin.items().create(ItemType.PANEL_KIT, 1));
                player.sendActionBar(Component.text("已拆除呼梯面板"));
            }
            return;
        }
        FloorStation removed = plugin.stations().remove(stationRoot);
        if (removed != null) {
            player.getInventory().addItem(plugin.items().create(ItemType.STATION_KIT, 1));
            player.sendActionBar(Component.text("已拆除楼层站，关联呼梯面板已自动解除连接"));
        }
    }

    private GridPos stationRoot(Entity entity) { return readRoot(entity, plugin.stationRootKey()); }
    private GridPos panelRoot(Entity entity) { return readRoot(entity, plugin.panelRootKey()); }
    private static GridPos readRoot(Entity entity, org.bukkit.NamespacedKey key) {
        String value = entity.getPersistentDataContainer().get(key, org.bukkit.persistence.PersistentDataType.STRING);
        if (value == null) return null;
        try { return GridPos.parse(value); } catch (RuntimeException ignored) { return null; }
    }
    private static Facing fromBlockFace(BlockFace face) {
        return switch (face) { case NORTH -> Facing.NORTH; case EAST -> Facing.EAST; case WEST -> Facing.WEST; default -> Facing.SOUTH; };
    }
    private static void consume(Player player) {
        if (player.getGameMode() == GameMode.CREATIVE) return;
        var item = player.getInventory().getItemInMainHand();
        item.setAmount(item.getAmount() - 1);
    }
}