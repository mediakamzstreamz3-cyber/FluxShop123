package me.donutshop;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Simple custom shard currency, stored in plugins/DonutShop/shards.yml. */
public final class ShardManager {
    private final DonutShop plugin;
    private final File file;
    private final Map<UUID, Long> balances = new HashMap<>();
    private boolean dirty;

    public ShardManager(DonutShop plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "shards.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String k : y.getKeys(false)) {
            try {
                balances.put(UUID.fromString(k), y.getLong(k));
            } catch (IllegalArgumentException ignored) { }
        }
    }

    public synchronized long get(UUID id) {
        return balances.getOrDefault(id, (long) plugin.getConfig().getInt("starting-shards", 0));
    }

    public synchronized void set(UUID id, long amount) {
        balances.put(id, Math.max(0, amount));
        dirty = true;
    }

    public synchronized void add(UUID id, long amount) {
        set(id, get(id) + amount);
    }

    /** Returns false (and changes nothing) if the player can't afford it. */
    public synchronized boolean take(UUID id, long amount) {
        long cur = get(id);
        if (cur < amount) return false;
        set(id, cur - amount);
        return true;
    }

    public synchronized void save() {
        if (!dirty) return;
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, Long> e : balances.entrySet()) y.set(e.getKey().toString(), e.getValue());
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
            dirty = false;
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save shards.yml: " + ex.getMessage());
        }
    }
}
