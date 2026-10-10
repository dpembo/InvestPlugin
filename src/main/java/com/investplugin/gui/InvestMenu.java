package com.investplugin.gui;

import com.investplugin.InvestPlugin;
import com.investplugin.managers.InvestManager;
import com.investplugin.model.Account;
import com.investplugin.model.HistoryRow;
import com.investplugin.model.MarketDay;
import com.investplugin.util.Money;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** The /invest chest GUI. Every name, lore line and material comes from config.yml (gui.*). */
public final class InvestMenu {

    public static final int SIZE = 36;

    public static final int SLOT_STATUS = 4;
    public static final int SLOT_CAPITAL = 10;
    public static final int SLOT_POT = 11;
    public static final int SLOT_TOTAL = 12;
    public static final int SLOT_WITHDRAWABLE = 13;
    public static final int SLOT_MARKET = 15;
    public static final int SLOT_GAINS = 16;
    public static final int SLOT_DEPOSIT = 20;
    public static final int SLOT_AUTO = 22;
    public static final int SLOT_WITHDRAW = 24;

    /** Marks inventories that belong to this menu (safer than comparing titles). */
    public static final class Holder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private InvestMenu() {}

    public static Inventory build(Player player, InvestPlugin plugin) {
        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, SIZE, plugin.text("gui.title", Map.of()));
        holder.inventory = inv;
        refresh(inv, player, plugin);
        return inv;
    }

    public static void refresh(Inventory inv, Player player, InvestPlugin plugin) {
        InvestManager manager = plugin.getInvestManager();
        Account acc = manager.view(player.getUniqueId());
        Map<String, String> ph = plugin.getActions().ph(player.getUniqueId());
        long now = System.currentTimeMillis();

        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fm = filler.getItemMeta();
        if (fm != null) {
            fm.setDisplayName(" ");
            filler.setItemMeta(fm);
        }
        for (int i = 0; i < SIZE; i++) inv.setItem(i, filler);

        if (acc.isFrozen()) {
            inv.setItem(SLOT_STATUS, item(plugin, "status-frozen", Material.PACKED_ICE, ph, null));
        } else {
            inv.setItem(SLOT_STATUS, item(plugin, "status-active", Material.CLOCK, ph, null));
        }
        inv.setItem(SLOT_CAPITAL, item(plugin, "capital", Material.GOLD_INGOT, ph, null));
        inv.setItem(SLOT_POT, item(plugin, "pot", Material.CHEST, ph, null));
        inv.setItem(SLOT_TOTAL, item(plugin, "total", Material.EMERALD, ph, null));
        inv.setItem(SLOT_WITHDRAWABLE, item(plugin, "withdrawable", Material.HOPPER, ph, null));
        inv.setItem(SLOT_MARKET, item(plugin, "market", Material.COMPASS, ph, marketLines(plugin, manager, now)));
        inv.setItem(SLOT_GAINS, item(plugin, "gains", Material.PAPER, ph,
                gainLines(plugin, manager, player, now)));
        inv.setItem(SLOT_DEPOSIT, item(plugin, "deposit", Material.LIME_CONCRETE, ph, null));
        inv.setItem(SLOT_AUTO, acc.isAutoInvest()
                ? item(plugin, "auto-on", Material.LIME_DYE, ph, null)
                : item(plugin, "auto-off", Material.GRAY_DYE, ph, null));
        inv.setItem(SLOT_WITHDRAW, item(plugin, "withdraw", Material.RED_CONCRETE, ph, null));
    }

    // ── Item building ─────────────────────────────────────────────────────────

    private static ItemStack item(InvestPlugin plugin, String key, Material fallback,
                                  Map<String, String> ph, List<String> dayLines) {
        String base = "gui." + key;
        Material material = Material.matchMaterial(plugin.getConfig().getString(base + ".material", fallback.name()));
        if (material == null || !material.isItem()) material = fallback;

        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.text(base + ".name", ph));
            List<String> lore = new ArrayList<>();
            for (String line : plugin.getConfig().getStringList(base + ".lore")) {
                if (dayLines != null && line.contains("{days}")) {
                    lore.addAll(dayLines);
                } else {
                    lore.add(plugin.apply(line, ph));
                }
            }
            meta.setLore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static DateTimeFormatter dateFormat(InvestPlugin plugin) {
        try {
            return DateTimeFormatter.ofPattern(plugin.getConfig().getString("gui.date-format", "dd MMM"), Locale.ENGLISH)
                    .withZone(ZoneId.systemDefault());
        } catch (IllegalArgumentException e) {
            return DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH).withZone(ZoneId.systemDefault());
        }
    }

    private static List<String> marketLines(InvestPlugin plugin, InvestManager manager, long now) {
        List<String> out = new ArrayList<>();
        DateTimeFormatter fmt = dateFormat(plugin);
        String template = plugin.getConfig().getString("gui.market.line", "&7{date}: {color}{percent}");
        for (MarketDay day : manager.recentMarket(now)) {
            Map<String, String> m = new HashMap<>();
            m.put("date", fmt.format(Instant.ofEpochMilli(day.time())));
            m.put("color", plugin.colorFor(day.pct()));
            m.put("percent", Money.percent(day.pct()));
            out.add(plugin.apply(template, m));
        }
        if (out.isEmpty()) out.add(plugin.text("gui.no-data", Map.of()));
        return out;
    }

    private static List<String> gainLines(InvestPlugin plugin, InvestManager manager, Player player, long now) {
        List<String> out = new ArrayList<>();
        DateTimeFormatter fmt = dateFormat(plugin);
        String template = plugin.getConfig().getString("gui.gains.line", "&7{date}: {color}{amount}");
        for (HistoryRow row : manager.recentHistory(player.getUniqueId(), now)) {
            Map<String, String> m = new HashMap<>();
            m.put("date", fmt.format(Instant.ofEpochMilli(row.time())));
            m.put("color", plugin.colorFor(row.delta()));
            m.put("amount", plugin.signedMoney(row.delta()));
            out.add(plugin.apply(template, m));
        }
        if (out.isEmpty()) out.add(plugin.text("gui.no-data", Map.of()));
        return out;
    }
}
