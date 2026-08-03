package cn.codex.pluginlift;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

/** Rebuilds non-persistent display entities when their chunks return. */
public final class ChunkListener implements Listener {
    private final PluginLift plugin;

    ChunkListener(PluginLift plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkUnload(ChunkUnloadEvent event) {
        String world = event.getWorld().getName();
        int chunkX = event.getChunk().getX(), chunkZ = event.getChunk().getZ();
        plugin.stations().unloadChunk(world, chunkX, chunkZ);
        plugin.panels().unloadChunk(world, chunkX, chunkZ);
        plugin.lifts().unloadChunk(world, chunkX, chunkZ);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            var loadedWorld = plugin.getServer().getWorld(world);
            if (loadedWorld != null && loadedWorld.isChunkLoaded(chunkX, chunkZ)) {
                plugin.stations().loadChunk(world, chunkX, chunkZ);
                plugin.panels().loadChunk(world, chunkX, chunkZ);
                plugin.lifts().loadChunk(world, chunkX, chunkZ);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        String world = event.getWorld().getName();
        int chunkX = event.getChunk().getX(), chunkZ = event.getChunk().getZ();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            plugin.stations().loadChunk(world, chunkX, chunkZ);
            plugin.panels().loadChunk(world, chunkX, chunkZ);
            plugin.lifts().loadChunk(world, chunkX, chunkZ);
        });
    }
}