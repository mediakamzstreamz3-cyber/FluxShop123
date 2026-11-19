package me.donutshop;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Per-player sidebar showing money, shards, kills, etc. */
public final class SidebarManager implements Listener {
    private final DonutShop plugin;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private BukkitTask task;

    public SidebarManager(DonutShop plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        if (!plugin.getConfig().getBoolean("scoreboard.enabled", true)) return;
        long iv = Math.max(5L, plugin.getConfig().getLong("scoreboard.interval-ticks", 20L));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::updateAll, 20L, iv);
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
        if (Bukkit.getScoreboardManager() != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (boards.containsKey(p.getUniqueId())) p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
        }
        boards.clear();
    }

    public void refresh(Player p) {
        if (task != null) update(p);
    }

    private void updateAll() {
        for (Player p : Bukkit.getOnlinePlayers()) update(p);
    }

    private List<String> lines() {
        List<String> l = plugin.getConfig().getStringList("scoreboard.lines");
        return l.size() > 15 ? l.subList(0, 15) : l;
    }

    private void update(Player p) {
        List<String> lines = lines();
        Scoreboard sb = boards.get(p.getUniqueId());
        if (sb == null || sb.getTeams().size() != lines.size()) {
            sb = create(lines.size());
            boards.put(p.getUniqueId(), sb);
        }
        if (p.getScoreboard() != sb) p.setScoreboard(sb);
        for (int i = 0; i < lines.size(); i++) {
            Team t = sb.getTeam("l" + i);
            if (t != null) t.prefix(parse(lines.get(i), p));
        }
    }

    private Scoreboard create(int n) {
        Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
        Component title = DonutShop.MM.deserialize(plugin.getConfig().getString("scoreboard.title", "Server"));
        Objective obj = sb.registerNewObjective("donutshop", Criteria.DUMMY, title);
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        obj.numberFormat(NumberFormat.blank());
        for (int i = 0; i < n; i++) {
            Team t = sb.registerNewTeam("l" + i);
            String entry = "\u00a7" + Integer.toHexString(i);
            t.addEntry(entry);
            obj.getScore(entry).setScore(n - i);
        }
        return sb;
    }

    private Component parse(String line, Player p) {
        Economy eco = plugin.economy();
        String money = eco == null ? "0" : plugin.compact(eco.getBalance(p));
        String s = line
                .replace("%player%", p.getName())
                .replace("%money%", money)
                .replace("%shards%", plugin.compact(plugin.shards().get(p.getUniqueId())))
                .replace("%kills%", String.valueOf(stat(p, "PLAYER_KILLS")))
                .replace("%deaths%", String.valueOf(stat(p, "DEATHS")))
                .replace("%playtime%", playtime(p));
        return DonutShop.MM.deserialize(s).decoration(TextDecoration.ITALIC, false);
    }

    private int stat(Player p, String name) {
        try {
            return p.getStatistic(Statistic.valueOf(name));
        } catch (Exception e) {
            return 0;
        }
    }

    private String playtime(Player p) {
        int ticks = stat(p, "PLAY_ONE_MINUTE");
        if (ticks == 0) ticks = stat(p, "PLAY_TIME");
        long h = ticks / 72000L;
        long m = (ticks / 1200L) % 60L;
        return h + "h " + m + "m";
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> refresh(e.getPlayer()), 5L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        boards.remove(e.getPlayer().getUniqueId());
    }
}
