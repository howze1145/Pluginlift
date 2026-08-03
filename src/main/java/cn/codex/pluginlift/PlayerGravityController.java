package cn.codex.pluginlift;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Owns the temporary no-gravity state used to support riders on the virtual cabin floor. */
final class PlayerGravityController implements Listener {
    private final NamespacedKey controlledKey;
    private final NamespacedKey previousGravityKey;
    private final Map<UUID, Boolean> previousGravity = new HashMap<>();

    PlayerGravityController(PluginLift plugin) {
        controlledKey = new NamespacedKey(plugin, "rider_gravity_controlled");
        previousGravityKey = new NamespacedKey(plugin, "rider_previous_gravity");
    }

    /** Only takes ownership of normal gravity, while allowing an already controlled rider to continue. */
    boolean canSupport(Player player) {
        UUID id = player.getUniqueId();
        return previousGravity.containsKey(id)
                || player.getPersistentDataContainer().has(controlledKey, PersistentDataType.BYTE)
                || player.hasGravity();
    }

    boolean isSupporting(Player player) {
        return player != null && (previousGravity.containsKey(player.getUniqueId())
                || player.getPersistentDataContainer().has(controlledKey, PersistentDataType.BYTE));
    }

    void support(Player player) {
        UUID id = player.getUniqueId();
        if (!previousGravity.containsKey(id)) {
            boolean original = readPersistedOriginal(player);
            previousGravity.put(id, original);
            player.getPersistentDataContainer().set(controlledKey, PersistentDataType.BYTE, (byte) 1);
            player.getPersistentDataContainer().set(previousGravityKey, PersistentDataType.BYTE, original ? (byte) 1 : (byte) 0);
            player.setGravity(false);
        } else if (player.hasGravity()) {
            // Repair an externally toggled state, but do not resend entity gravity metadata each tick.
            player.setGravity(false);
        }
        player.setFallDistance(0);
    }

    void release(Player player, boolean stopVerticalMotion) {
        if (player == null) return;
        Boolean original = previousGravity.remove(player.getUniqueId());
        if (original == null && player.getPersistentDataContainer().has(controlledKey, PersistentDataType.BYTE)) {
            original = readPreviousGravity(player);
        }
        if (original == null) return;
        clearMarker(player);
        player.setGravity(original);
        player.setFallDistance(0);
        if (stopVerticalMotion) stopVerticalMotion(player);
    }

    /** Repairs both marked crash leftovers and no-gravity players left behind by older PluginLift builds. */
    void recover(Player player) {
        if (player.getPersistentDataContainer().has(controlledKey, PersistentDataType.BYTE)) {
            boolean original = readPreviousGravity(player);
            previousGravity.remove(player.getUniqueId());
            clearMarker(player);
            player.setGravity(original);
            player.setFallDistance(0);
            stopVerticalMotion(player);
            return;
        }
        if (player.getGameMode() != GameMode.SPECTATOR && !player.hasGravity()) {
            player.setGravity(true);
            player.setFallDistance(0);
            stopVerticalMotion(player);
        }
    }

    void releaseAll() {
        for (UUID id : previousGravity.keySet().toArray(UUID[]::new)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) release(player, false);
        }
        previousGravity.clear();
    }

    private boolean readPersistedOriginal(Player player) {
        if (player.getPersistentDataContainer().has(controlledKey, PersistentDataType.BYTE)) {
            return readPreviousGravity(player);
        }
        // PluginLift 0.1.0-Beta builds before this fix could leave players with gravity disabled
        // without a marker. Non-spectator players entering a lift in that state must recover to normal.
        return player.hasGravity() || player.getGameMode() != GameMode.SPECTATOR;
    }

    private boolean readPreviousGravity(Player player) {
        Byte value = player.getPersistentDataContainer().get(previousGravityKey, PersistentDataType.BYTE);
        return value == null || value != 0;
    }

    private void clearMarker(Player player) {
        player.getPersistentDataContainer().remove(controlledKey);
        player.getPersistentDataContainer().remove(previousGravityKey);
    }

    private static void stopVerticalMotion(Player player) {
        Vector velocity = player.getVelocity();
        velocity.setY(0);
        player.setVelocity(velocity);
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) { recover(event.getPlayer()); }
    @EventHandler public void onQuit(PlayerQuitEvent event) { release(event.getPlayer(), false); }
    @EventHandler public void onDeath(PlayerDeathEvent event) { release(event.getEntity(), false); }
    @EventHandler public void onWorldChange(PlayerChangedWorldEvent event) { release(event.getPlayer(), false); }
    @EventHandler public void onGameModeChange(PlayerGameModeChangeEvent event) { release(event.getPlayer(), false); }
}