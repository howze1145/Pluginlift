package cn.codex.pluginlift;

import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

final class PluginLiftCommand implements CommandExecutor, TabCompleter {
    private final PluginLift plugin;

    PluginLiftCommand(PluginLift plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
                help(sender);
                return true;
            }
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "resourcepack" -> plugin.sendResourcePack(player(sender));
                case "list" -> list(sender);
                case "give" -> {
                    admin(sender);
                    give(sender, args);
                }
                case "rename" -> {
                    admin(sender);
                    rename(sender, args);
                }
                case "delete" -> {
                    admin(sender);
                    if (args.length < 2) throw new IllegalArgumentException("用法：/pluginlift delete <ID>");
                    sender.sendMessage(Component.text(plugin.lifts().delete(args[1])
                            ? "电梯已删除，楼层站保留；编号空缺会由下一台新电梯补位"
                            : "找不到电梯 " + args[1]));
                }
                case "respawn" -> {
                    admin(sender);
                    plugin.stations().respawnAll();
                    plugin.panels().respawnAll();
                    plugin.lifts().respawnAll();
                    sender.sendMessage(Component.text("显示实体已重建"));
                }
                case "reload" -> {
                    admin(sender);
                    plugin.reloadEverything();
                    sender.sendMessage(Component.text("PluginLift 0.1.4 已重载"));
                }
                default -> help(sender);
            }
        } catch (IllegalArgumentException | IllegalStateException exception) {
            sender.sendMessage(Component.text(exception.getMessage()));
        }
        return true;
    }

    private void list(CommandSender sender) {
        if (plugin.lifts().all().isEmpty()) {
            sender.sendMessage(Component.text("当前没有已同步电梯"));
            return;
        }
        sender.sendMessage(Component.text("电梯列表："));
        plugin.lifts().all().forEach(lift -> sender.sendMessage(Component.text(
                lift.spec().id() + " " + plugin.lifts().displayName(lift.spec().id())
                        + " " + lift.spec().floors().size() + "层")));
    }

    private void rename(CommandSender sender, String[] args) {
        if (args.length < 3) throw new IllegalArgumentException("用法：/pluginlift rename <ID> <名称>");
        String name = String.join(" ", Arrays.copyOfRange(args, 2, args.length)).trim();
        if (!plugin.lifts().rename(args[1], name)) throw new IllegalArgumentException("找不到电梯 " + args[1]);
        sender.sendMessage(Component.text("电梯 " + args[1] + " 已命名为 " + name));
    }

    private void give(CommandSender sender, String[] args) {
        Player player = player(sender);
        if (args.length < 2) throw new IllegalArgumentException("用法：/pluginlift give <station|panel|connector|sync|config|all> [数量]");
        int amount = args.length > 2 ? Math.max(1, Math.min(64, Integer.parseInt(args[2]))) : 1;
        if (args[1].equalsIgnoreCase("all")) {
            for (ItemType type : ItemType.values()) player.getInventory().addItem(plugin.items().create(type, amount));
            return;
        }
        ItemType type = switch (args[1].toLowerCase(Locale.ROOT)) {
            case "station", "stationkit" -> ItemType.STATION_KIT;
            case "panel", "callpanel" -> ItemType.PANEL_KIT;
            case "connector", "link" -> ItemType.CONNECTOR;
            case "sync", "synchronizer" -> ItemType.SYNC_TOOL;
            case "config", "configurator" -> ItemType.CONFIGURATOR;
            default -> throw new IllegalArgumentException("未知物品");
        };
        player.getInventory().addItem(plugin.items().create(type, amount));
    }

    private void help(CommandSender sender) {
        sender.sendMessage(Component.text("PluginLift 0.1.4：give | list | resourcepack"));
        if (sender.hasPermission("pluginlift.admin")) {
            sender.sendMessage(Component.text("管理：rename | delete | respawn | reload"));
        }
    }

    private static Player player(CommandSender sender) {
        if (!(sender instanceof Player player)) throw new IllegalArgumentException("该命令只能由玩家执行");
        return player;
    }

    private static void admin(CommandSender sender) {
        if (!sender.hasPermission("pluginlift.admin")) throw new IllegalArgumentException("需要 pluginlift.admin 权限");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            result.addAll(List.of("help", "list", "resourcepack", "give", "rename", "delete", "respawn", "reload"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            result.addAll(List.of("station", "panel", "connector", "sync", "config", "all"));
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("delete") || args[0].equalsIgnoreCase("rename"))) {
            result.addAll(plugin.lifts().all().stream().map(value -> value.spec().id()).toList());
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return result.stream().filter(value -> value.startsWith(prefix)).toList();
    }
}