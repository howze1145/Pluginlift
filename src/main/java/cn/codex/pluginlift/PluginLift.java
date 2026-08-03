package cn.codex.pluginlift;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HexFormat;
import java.util.UUID;

public final class PluginLift extends JavaPlugin {
    public static final UUID RESOURCE_PACK_ID = UUID.fromString("a5e3491b-a9cc-4c22-91eb-ad2aee2f76ac");
    private static final String LEGACY_RESOURCE_PACK_URL = "https://mcpacks.dev/pack/747edd78-f930-4d93-9f25-97a32e81fc34/download";
    private static final String LEGACY_RESOURCE_PACK_SHA1 = "8e6fb295c1a09f16b7bdeb98f0c9802777378e3a";
    private static final String BROKEN_RESOURCE_PACK_URL = "https://mcpacks.dev/pack/9f124d8c-9737-409e-9acc-2ee105bcd4f4/download";
    private static final String BROKEN_RESOURCE_PACK_SHA1 = "e1331de540b9cfec45221a84c277b263b234d488";
    private static final String PREVIOUS_RESOURCE_PACK_URL = "https://mcpacks.dev/pack/e0199071-c1d6-4be7-bf96-6f1ab7307232/download";
    private static final String PREVIOUS_RESOURCE_PACK_SHA1 = "bf5b40f9311751923e1a429ccd8cefa3051d88d5";
    private static final String MISLABELED_RESOURCE_PACK_URL = "https://mcpacks.dev/pack/0db9a33e-022e-4b8c-a68b-bf4cc409bf72/download";
    private static final String MISLABELED_RESOURCE_PACK_SHA1 = "b652a1d10aefb1016aabf0e140d8fadb9ee511c5";
    private static final String BUNDLED_RESOURCE_PACK_URL = "https://mcpacks.dev/pack/9391ab9e-1a96-4ecd-b96a-9e3c6477aa46/download";
    private static final String BUNDLED_RESOURCE_PACK_SHA1 = "4aa8cccb4502a5163c39d8a1b99ce6659a40f90f";
    private StationManager stations;
    private LiftManager lifts;
    private PanelManager panels;
    private ItemCatalog items;
    private PlayerGravityController playerGravity;
    private NamespacedKey ownedKey, entityTypeKey, stationRootKey, panelRootKey, liftIdKey, floorIndexKey, itemTypeKey, menuFloorKey;

    @Override public void onEnable() {
        saveDefaultConfig();
        migrateBundledResourcePack();
        ownedKey = key("owned_v5"); entityTypeKey = key("entity_type"); stationRootKey = key("station_root");
        panelRootKey = key("panel_root"); liftIdKey = key("lift_id"); floorIndexKey = key("floor_index"); itemTypeKey = key("item_type"); menuFloorKey = key("menu_floor");
        playerGravity = new PlayerGravityController(this);
        Bukkit.getOnlinePlayers().forEach(playerGravity::recover);
        cleanupEntities();
        lifts = new LiftManager(this);
        stations = new StationManager(this);
        panels = new PanelManager(this);
        items = new ItemCatalog(this);
        stations.load();
        lifts.rebuildFromStations();
        panels.load();
        StationListener listener = new StationListener(this);
        Bukkit.getPluginManager().registerEvents(listener, this);
        Bukkit.getPluginManager().registerEvents(new LiftListener(this), this);
        Bukkit.getPluginManager().registerEvents(playerGravity, this);
        Bukkit.getPluginManager().registerEvents(new CollisionListener(this), this);
        Bukkit.getPluginManager().registerEvents(new WorldProtectionListener(this), this);
        Bukkit.getPluginManager().registerEvents(new ChunkListener(this), this);
        PluginLiftCommand command = new PluginLiftCommand(this);
        PluginCommand pluginCommand = getCommand("pluginlift");
        if (pluginCommand == null) throw new IllegalStateException("plugin.yml missing pluginlift command");
        pluginCommand.setExecutor(command); pluginCommand.setTabCompleter(command);
        Bukkit.getScheduler().runTaskTimer(this, lifts::tick, 1, 1);
        getLogger().info("PluginLift 0.1.4 loaded with " + stations.all().size() + " station(s) and " + panels.all().size() + " independent panel(s).");
    }
    @Override public void onDisable() {
        if (stations != null) { stations.save(); stations.removeRenderers(); }
        if (panels != null) { panels.save(); panels.removeRenderers(); }
        if (lifts != null) lifts.removeRenderers();
        if (playerGravity != null) playerGravity.releaseAll();
    }
    public void reloadEverything() {
        reloadConfig(); stations.load(); lifts.rebuildFromStations(); panels.load();
    }
    private void migrateBundledResourcePack() {
        String configuredUrl = getConfig().getString("resource-pack.url", "").trim();
        String configuredSha1 = getConfig().getString("resource-pack.sha1", "").trim();
        boolean legacyPack = LEGACY_RESOURCE_PACK_URL.equals(configuredUrl) && LEGACY_RESOURCE_PACK_SHA1.equalsIgnoreCase(configuredSha1);
        boolean brokenPack = BROKEN_RESOURCE_PACK_URL.equals(configuredUrl) && BROKEN_RESOURCE_PACK_SHA1.equalsIgnoreCase(configuredSha1);
        boolean previousPack = PREVIOUS_RESOURCE_PACK_URL.equals(configuredUrl) && PREVIOUS_RESOURCE_PACK_SHA1.equalsIgnoreCase(configuredSha1);
        boolean mislabeledPack = MISLABELED_RESOURCE_PACK_URL.equals(configuredUrl) && MISLABELED_RESOURCE_PACK_SHA1.equalsIgnoreCase(configuredSha1);
        if (legacyPack || brokenPack || previousPack || mislabeledPack) {
            getConfig().set("resource-pack.url", BUNDLED_RESOURCE_PACK_URL);
            getConfig().set("resource-pack.sha1", BUNDLED_RESOURCE_PACK_SHA1);
            saveConfig();
            getLogger().info("Updated the bundled PluginLift resource pack to the corrected 0.1.4 landing-door revision.");
        }
    }
    public void sendResourcePack(Player player) {
        String url = getConfig().getString("resource-pack.url", "").trim();
        String sha1 = getConfig().getString("resource-pack.sha1", "").trim();
        if (url.isEmpty()) { player.sendMessage("PluginLift 0.1.4 资源包 URL 尚未设置。"); return; }
        if (!sha1.matches("[0-9a-fA-F]{40}")) { player.sendMessage("资源包 SHA-1 必须是 40 位十六进制。"); return; }
        player.addResourcePack(RESOURCE_PACK_ID, url, HexFormat.of().parseHex(sha1), "PluginLift 0.1.4 原创电梯资源", getConfig().getBoolean("resource-pack.required", false));
    }
    private NamespacedKey key(String value) { return new NamespacedKey(this, value); }
    private void cleanupEntities() {
        for (var world : Bukkit.getWorlds()) for (Entity entity : world.getEntities())
            if (entity.getPersistentDataContainer().has(ownedKey, PersistentDataType.BYTE)) entity.remove();
    }
    public StationManager stations() { return stations; }
    public LiftManager lifts() { return lifts; }
    public PanelManager panels() { return panels; }
    public ItemCatalog items() { return items; }
    public PlayerGravityController playerGravity() { return playerGravity; }
    public NamespacedKey ownedKey() { return ownedKey; }
    public NamespacedKey entityTypeKey() { return entityTypeKey; }
    public NamespacedKey stationRootKey() { return stationRootKey; }
    public NamespacedKey panelRootKey() { return panelRootKey; }
    public NamespacedKey liftIdKey() { return liftIdKey; }
    public NamespacedKey floorIndexKey() { return floorIndexKey; }
    public NamespacedKey itemTypeKey() { return itemTypeKey; }
    public NamespacedKey menuFloorKey() { return menuFloorKey; }
}
