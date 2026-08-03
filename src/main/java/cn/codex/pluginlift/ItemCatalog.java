package cn.codex.pluginlift;

import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public final class ItemCatalog {
    private final PluginLift plugin;
    ItemCatalog(PluginLift plugin) { this.plugin = plugin; }
    public ItemStack create(ItemType type, int amount) {
        ItemStack item = new ItemStack(type.material(), Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(type.displayName()));
        meta.lore(List.of(Component.text(type.usage())));
        meta.setItemModel(new NamespacedKey("pluginlift", type.model()));
        meta.getPersistentDataContainer().set(plugin.itemTypeKey(), PersistentDataType.STRING, type.name());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }
    public ItemType type(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String value = item.getItemMeta().getPersistentDataContainer().get(plugin.itemTypeKey(), PersistentDataType.STRING);
        if (value != null) {
            try { return ItemType.valueOf(value); } catch (IllegalArgumentException ignored) { return null; }
        }
        NamespacedKey model = item.getItemMeta().getItemModel();
        if (model == null || !model.getNamespace().equals("pluginlift")) return null;
        for (ItemType type : ItemType.values()) {
            if (type.material() == item.getType() && type.model().equals(model.getKey())) return type;
        }
        return null;
    }
}