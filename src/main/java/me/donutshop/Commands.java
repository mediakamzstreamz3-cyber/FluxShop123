package me.donutshop;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class Commands implements CommandExecutor, TabCompleter {
    private final DonutShop plugin;

    public Commands(DonutShop plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        switch (cmd.getName().toLowerCase()) {
            case "shop" -> {
                if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                    if (!sender.hasPermission("donutshop.admin")) { plugin.msg(sender, "no-permission"); return true; }
                    plugin.reload();
                    plugin.msg(sender, "reloaded");
                    return true;
                }
                if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
                plugin.menus().open(p, args.length > 0 ? args[0] : "main", 0);
            }
            case "shardshop" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
                plugin.menus().open(p, "shard-main", 0);
            }
            case "shards" -> shards(sender, args);
            default -> { return false; }
        }
        return true;
    }

    @SuppressWarnings("deprecation")
    private void shards(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player p)) { sender.sendMessage("Usage: /shards <player>"); return; }
            plugin.msg(sender, "shards-balance", "%player%", "You", "%amount%", String.valueOf(plugin.shards().get(p.getUniqueId())));
            return;
        }
        String sub = args[0].toLowerCase();
        if (sub.equals("give") || sub.equals("take") || sub.equals("set")) {
            if (!sender.hasPermission("donutshop.admin")) { plugin.msg(sender, "no-permission"); return; }
            if (args.length < 3) { sender.sendMessage("Usage: /shards " + sub + " <player> <amount>"); return; }
            OfflinePlayer t = Bukkit.getOfflinePlayer(args[1]);
            long amt;
            try {
                amt = Long.parseLong(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage("Amount must be a whole number.");
                return;
            }
            switch (sub) {
                case "give" -> plugin.shards().add(t.getUniqueId(), amt);
                case "take" -> plugin.shards().set(t.getUniqueId(), plugin.shards().get(t.getUniqueId()) - amt);
                default -> plugin.shards().set(t.getUniqueId(), amt);
            }
            plugin.msg(sender, "shards-changed", "%player%", String.valueOf(t.getName()), "%amount%", String.valueOf(plugin.shards().get(t.getUniqueId())));
            if (t.isOnline() && t.getPlayer() != null) plugin.sidebar().refresh(t.getPlayer());
            return;
        }
        if (!sender.hasPermission("donutshop.admin")) { plugin.msg(sender, "no-permission"); return; }
        OfflinePlayer t = Bukkit.getOfflinePlayer(args[0]);
        plugin.msg(sender, "shards-balance", "%player%", String.valueOf(t.getName()), "%amount%", String.valueOf(plugin.shards().get(t.getUniqueId())));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        String n = cmd.getName().toLowerCase();
        if (n.equals("shop") && args.length == 1) {
            out.addAll(plugin.menus().ids());
            if (sender.hasPermission("donutshop.admin")) out.add("reload");
        } else if (n.equals("shards")) {
            if (args.length == 1) {
                if (sender.hasPermission("donutshop.admin")) out.addAll(List.of("give", "take", "set"));
                if (sender.hasPermission("donutshop.admin")) Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
            } else if (args.length == 2 && sender.hasPermission("donutshop.admin")) {
                Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
            }
        }
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase();
        out.removeIf(s -> !s.toLowerCase().startsWith(last));
        return out;
    }
}
