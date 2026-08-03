package cn.codex.pluginlift;

import cn.codex.pluginlift.core.GridPos;
import net.kyori.adventure.text.Component;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.event.world.StructureGrowEvent;

import java.util.List;

/** Keeps the carrier-free virtual station and shaft cells free of vanilla blocks. */
public final class WorldProtectionListener implements Listener {
    private final PluginLift plugin;

    WorldProtectionListener(PluginLift plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!occupies(event.getBlockPlaced())) return;
        event.setCancelled(true);
        event.getPlayer().sendActionBar(Component.text("该位置属于 PluginLift 楼层站或 2×2 电梯竖井"));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (!occupies(event.getBlock())) return;
        event.setCancelled(true);
        event.getPlayer().sendActionBar(Component.text("液体不能进入 PluginLift 楼层站或电梯竖井"));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFluidFlow(BlockFromToEvent event) { if (occupies(event.getToBlock())) event.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (occupies(event.getBlock().getRelative(event.getDirection()))
                || entersOccupiedCell(event.getBlocks(), event.getDirection())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(block -> occupies(block)
                || occupies(block.getRelative(event.getDirection().getOppositeFace())))) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) { if (occupies(event.getBlock())) event.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockGrow(BlockGrowEvent event) { if (occupies(event.getBlock())) event.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockForm(BlockFormEvent event) { if (occupies(event.getBlock())) event.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) { if (occupies(event.getBlock())) event.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) { if (occupies(event.getBlock())) event.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFertilize(BlockFertilizeEvent event) { if (statesTouchLift(event.getBlocks())) event.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onStructureGrow(StructureGrowEvent event) { if (statesTouchLift(event.getBlocks())) event.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPortalCreate(PortalCreateEvent event) { if (statesTouchLift(event.getBlocks())) event.setCancelled(true); }

    private boolean entersOccupiedCell(List<Block> blocks, org.bukkit.block.BlockFace direction) {
        return blocks.stream().anyMatch(block -> occupies(block) || occupies(block.getRelative(direction)));
    }

    private boolean statesTouchLift(List<BlockState> states) {
        return states.stream().anyMatch(state -> occupies(state.getBlock()));
    }

    private boolean occupies(Block block) {
        GridPos position = new GridPos(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
        return plugin.stations().occupies(position) || plugin.panels().occupies(position) || plugin.lifts().shaftOccupies(position);
    }
}