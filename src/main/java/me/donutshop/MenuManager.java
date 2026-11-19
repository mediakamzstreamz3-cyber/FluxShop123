package me.donutshop;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MenuManager implements Listener {

    interface SlotAction {
        void click(Player p, ClickType type);
    }

    static final class Entry {
        ItemStack stack;
        double buy, sell;
        List<String> commands = List.of();
        String opens;
        int slot = -1;
    }

    static final class Def {
        String id, type, title, currency, back;
        int columns = 7, rows = 4, backSlot = -1;
        final List<Entry> entries = new ArrayList<>();
    }

    static final class Holder implements InventoryHolder {
        Inventory inventory;
        final Map<Integer, SlotAction> actions = new HashMap<>();

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final DonutShop plugin;
    private final Map<String, Def> defs = new HashMap<>();

    public MenuManager(DonutShop plugin) {
        this.plugin = plugin;
    }

    public Set<String> ids() {
        return defs.keySet();
    }

    private static Component noItalic(String mini) {
        return DonutShop.MM.deserialize(mini).decoration(TextDecoration.ITALIC, false);
    }

    // ------------------------------------------------------------------ loading

    public void load() {
        defs.clear();
        File dir = new File(plugin.getDataFolder(), "menus");
        File[] files = dir.listFiles((d, n) -> n.endsWith(".yml"));
        if (files == null) return;
        int items = 0;
        for (File f : files) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
            Def d = new Def();
            d.id = f.getName().substring(0, f.getName().length() - 4).toLowerCase();
            d.type = y.getString("type", "shop");
            d.title = y.getString("title", d.id);
            d.currency = y.getString("currency", "money");
            d.back = y.getString("back", "main");
            d.columns = y.getInt("columns", 7);
            d.rows = y.getInt("rows", 4);
            d.backSlot = y.getInt("back-slot", -1);
            for (Map<?, ?> raw : y.getMapList("items")) {
                Entry e = parseEntry(d, raw);
                if (e != null) d.entries.add(e);
            }
            items += d.entries.size();
            defs.put(d.id, d);
        }
        plugin.getLogger().info("Loaded " + defs.size() + " menus with " + items + " entries.");
    }

    private Entry parseEntry(Def d, Map<?, ?> raw) {
        ConfigurationSection s = new YamlConfiguration().createSection("e", raw);
        String matName = s.getString("material", "");
        Material m = Material.matchMaterial(matName);
        if (m == null || m.isAir() || !m.isItem()) {
            plugin.getLogger().warning("[" + d.id + "] skipped unknown material '" + matName + "'");
            return null;
        }
        Entry e = new Entry();
        ItemStack st = new ItemStack(m, Math.max(1, s.getInt("amount", 1)));
        ItemMeta meta = st.getItemMeta();
        if (meta != null) {
            if (meta instanceof PotionMeta pm && s.isString("potion")) {
                try {
                    pm.setBasePotionType(PotionType.valueOf(s.getString("potion", "").toUpperCase()));
                } catch (IllegalArgumentException ex) {
                    plugin.getLogger().warning("[" + d.id + "] unknown potion '" + s.getString("potion") + "'");
                }
            }
            ConfigurationSection en = s.getConfigurationSection("enchants");
            if (en != null) {
                for (String k : en.getKeys(false)) {
                    Enchantment ench = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(k.toLowerCase()));
                    if (ench == null) {
                        plugin.getLogger().warning("[" + d.id + "] unknown enchantment '" + k + "' on " + matName);
                    } else {
                        meta.addEnchant(ench, en.getInt(k), true);
                    }
                }
            }
            if (s.isString("name")) {
                String color = s.getString("color", "#ffffff");
                meta.displayName(noItalic("<bold><" + color + ">" + s.getString("name")));
                meta.lore(tooltip(s, color));
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            }
            st.setItemMeta(meta);
        }
        e.stack = st;
        e.buy = s.getDouble("buy", 0);
        e.sell = s.getDouble("sell", 0);
        e.commands = s.getStringList("commands");
        e.opens = s.getString("opens");
        e.slot = s.getInt("slot", -1);
        return e;
    }

    /** Builds the DonutSMP-style tooltip: [TAG], Information, description, CLICK to Open. */
    private List<Component> tooltip(ConfigurationSection s, String color) {
        List<Component> l = new ArrayList<>();
        l.add(noItalic("<gray>[" + s.getString("tag", "SHOP") + "]"));
        l.add(Component.empty());
        l.add(noItalic("<" + color + ">Information"));
        for (String line : s.getStringList("description")) {
            l.add(noItalic("<" + color + ">\u00a6 <white>" + line));
        }
        l.add(Component.empty());
        l.add(noItalic("<" + color + ">\u25b6 <underlined><bold>CLICK</bold></underlined> to Open"));
        return l;
    }

    // ------------------------------------------------------------------ opening

    public void open(Player p, String id, int page) {
        Def d = defs.get(id.toLowerCase());
        if (d == null) {
            plugin.msg(p, "unknown-menu", "%menu%", id);
            return;
        }
        Holder h = new Holder();
        boolean shards = d.currency.equalsIgnoreCase("shards");

        if (d.type.equalsIgnoreCase("menu")) {
            h.inventory = Bukkit.createInventory(h, d.rows * 9, DonutShop.MM.deserialize(d.title));
            for (Entry e : d.entries) {
                if (e.slot < 0 || e.slot >= d.rows * 9) continue;
                h.inventory.setItem(e.slot, e.stack.clone());
                h.actions.put(e.slot, (pl, t) -> handle(pl, d, e, t));
            }
            addBack(h, d, d.backSlot);
        } else {
            int perPage = d.columns * 3;
            int pages = Math.max(1, (int) Math.ceil(d.entries.size() / (double) perPage));
            page = Math.max(0, Math.min(page, pages - 1));
            int rows = pages > 1 ? 5 : 4;
            h.inventory = Bukkit.createInventory(h, rows * 9, DonutShop.MM.deserialize(d.title));
            int startCol = d.columns >= 7 ? 1 : 2;
            for (int i = 0; i < perPage; i++) {
                int idx = page * perPage + i;
                if (idx >= d.entries.size()) break;
                Entry e = d.entries.get(idx);
                int slot = (i / d.columns + 1) * 9 + startCol + (i % d.columns);
                h.inventory.setItem(slot, display(e, shards));
                h.actions.put(slot, (pl, t) -> handle(pl, d, e, t));
            }
            int backSlot = d.backSlot >= 0 ? d.backSlot : (rows * 9 - 5);
            addBack(h, d, backSlot);
            if (pages > 1) {
                ItemStack sign = new ItemStack(Material.OAK_SIGN);
                ItemMeta sm = sign.getItemMeta();
                sm.displayName(noItalic("<yellow>Page " + (page + 1) + "/" + pages));
                sign.setItemMeta(sm);
                h.inventory.setItem(4, sign);
                final int cur = page;
                if (page > 0) {
                    h.inventory.setItem(rows * 9 - 6, pane(Material.RED_STAINED_GLASS_PANE, "<red>Previous Page"));
                    h.actions.put(rows * 9 - 6, (pl, t) -> later(() -> open(pl, d.id, cur - 1)));
                }
                if (page < pages - 1) {
                    h.inventory.setItem(rows * 9 - 4, pane(Material.LIME_STAINED_GLASS_PANE, "<green>Next Page"));
                    h.actions.put(rows * 9 - 4, (pl, t) -> later(() -> open(pl, d.id, cur + 1)));
                }
            }
        }
        p.openInventory(h.inventory);
    }

    private void addBack(Holder h, Def d, int slot) {
        if (d.back == null || d.back.equalsIgnoreCase("none")) return;
        int s = slot >= 0 ? slot : h.inventory.getSize() - 5;
        ItemStack paper = new ItemStack(Material.PAPER);
        ItemMeta m = paper.getItemMeta();
        m.displayName(noItalic("<gray>Back to Menu"));
        paper.setItemMeta(m);
        h.inventory.setItem(s, paper);
        h.actions.put(s, (pl, t) -> later(() -> open(pl, d.back, 0)));
    }

    private ItemStack pane(Material mat, String name) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.displayName(noItalic(name));
        it.setItemMeta(m);
        return it;
    }

    private ItemStack display(Entry e, boolean shards) {
        ItemStack it = e.stack.clone();
        ItemMeta m = it.getItemMeta();
        List<Component> lore = m.lore() != null ? new ArrayList<>(m.lore()) : new ArrayList<>();
        lore.add(Component.empty());
        if (e.buy > 0) lore.add(noItalic("<gray>Buy: <green>" + plugin.price(shards, e.buy) + " <dark_gray>(Left Click)"));
        if (e.sell > 0 && !shards) lore.add(noItalic("<gray>Sell: <red>" + plugin.price(false, e.sell) + " <dark_gray>(Right Click)"));
        if (e.buy > 0) lore.add(noItalic("<dark_gray>Shift-Click: stack of 64"));
        m.lore(lore);
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        it.setItemMeta(m);
        return it;
    }

    private void later(Runnable r) {
        Bukkit.getScheduler().runTask(plugin, r);
    }

    // ------------------------------------------------------------------ clicking

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) return;
        SlotAction a = h.actions.get(e.getSlot());
        if (a != null) a.click(p, e.getClick());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }

    private void handle(Player p, Def d, Entry e, ClickType t) {
        if (e.opens != null) {
            later(() -> open(p, e.opens, 0));
            return;
        }
        boolean shards = d.currency.equalsIgnoreCase("shards");
        if (t.isLeftClick()) buy(p, e, shards, t.isShiftClick());
        else if (t.isRightClick() && !shards) sell(p, e, t.isShiftClick());
    }

    private String itemName(Entry e) {
        String n = e.stack.getType().name().toLowerCase().replace('_', ' ');
        return e.stack.getAmount() > 1 ? e.stack.getAmount() + "x " + n : n;
    }

    private void buy(Player p, Entry e, boolean shards, boolean stack) {
        if (e.buy <= 0) {
            plugin.msg(p, "not-for-sale");
            return;
        }
        int times = stack ? Math.max(1, 64 / Math.max(1, e.stack.getAmount())) : 1;
        double cost = e.buy * times;

        if (shards) {
            long c = (long) Math.ceil(cost);
            if (!plugin.shards().take(p.getUniqueId(), c)) {
                plugin.msg(p, "not-enough", "%price%", plugin.price(true, c));
                return;
            }
        } else {
            Economy eco = plugin.economy();
            if (eco == null) {
                plugin.msg(p, "no-economy");
                return;
            }
            if (!eco.has(p, cost)) {
                plugin.msg(p, "not-enough", "%price%", plugin.price(false, cost));
                return;
            }
            eco.withdrawPlayer(p, cost);
        }

        ItemStack give = e.stack.clone();
        give.setAmount(e.stack.getAmount() * times);
        if (e.commands.isEmpty()) {
            for (ItemStack left : p.getInventory().addItem(give).values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), left);
            }
        }
        for (int i = 0; i < times; i++) {
            for (String c : e.commands) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c.replace("%player%", p.getName()));
            }
        }
        String label = e.commands.isEmpty()
                ? (e.stack.getAmount() * times) + "x " + e.stack.getType().name().toLowerCase().replace('_', ' ')
                : itemName(e);
        plugin.msg(p, "bought", "%item%", label, "%price%", plugin.price(shards, cost));
        plugin.sidebar().refresh(p);
    }

    private void sell(Player p, Entry e, boolean all) {
        if (e.sell <= 0 || e.stack.hasItemMeta() && e.stack.getItemMeta().hasEnchants()) {
            plugin.msg(p, "not-sellable");
            return;
        }
        Economy eco = plugin.economy();
        if (eco == null) {
            plugin.msg(p, "no-economy");
            return;
        }
        Material mat = e.stack.getType();
        int per = Math.max(1, e.stack.getAmount());
        int have = 0;
        PlayerInventory inv = p.getInventory();
        for (ItemStack it : inv.getStorageContents()) {
            if (it != null && it.getType() == mat && !it.hasItemMeta()) have += it.getAmount();
        }
        int units = all ? have / per : (have >= per ? 1 : 0);
        if (units <= 0) {
            plugin.msg(p, "no-items");
            return;
        }
        int toRemove = units * per;
        ItemStack[] contents = inv.getStorageContents();
        for (int i = 0; i < contents.length && toRemove > 0; i++) {
            ItemStack it = contents[i];
            if (it == null || it.getType() != mat || it.hasItemMeta()) continue;
            int take = Math.min(toRemove, it.getAmount());
            it.setAmount(it.getAmount() - take);
            if (it.getAmount() <= 0) contents[i] = null;
            toRemove -= take;
        }
        inv.setStorageContents(contents);
        double money = e.sell * units;
        eco.depositPlayer(p, money);
        plugin.msg(p, "sold", "%item%", (units * per) + "x " + mat.name().toLowerCase().replace('_', ' '),
                "%price%", plugin.price(false, money));
        plugin.sidebar().refresh(p);
    }
}
