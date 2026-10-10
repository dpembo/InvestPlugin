package com.investplugin;

import com.investplugin.commands.InvestCommand;
import com.investplugin.listeners.ChatListener;
import com.investplugin.listeners.JoinListener;
import com.investplugin.listeners.MenuListener;
import com.investplugin.managers.InvestActions;
import com.investplugin.managers.InvestManager;
import com.investplugin.util.Money;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public class InvestPlugin extends JavaPlugin {

    private static InvestPlugin instance;
    private Economy economy;
    private InvestManager investManager;
    private InvestActions actions;
    private BukkitTask cycleTask;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        if (!setupEconomy()) {
            getLogger().severe("Vault/Economy not found! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        investManager = new InvestManager(this);
        try {
            investManager.load();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Could not start storage - check the 'storage' section of config.yml. Disabling plugin.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        actions = new InvestActions(this, investManager);

        ChatListener chat = new ChatListener(this);
        getServer().getPluginManager().registerEvents(chat, this);
        getServer().getPluginManager().registerEvents(new MenuListener(this, chat), this);
        getServer().getPluginManager().registerEvents(new JoinListener(this), this);

        InvestCommand command = new InvestCommand(this);
        getCommand("invest").setExecutor(command);
        getCommand("invest").setTabCompleter(command);

        // Checks once a minute whether the daily cycle is due (the schedule itself survives restarts).
        cycleTask = getServer().getScheduler().runTaskTimer(this, investManager::checkSchedule, 600L, 1200L);

        getLogger().info("InvestPlugin enabled.");
    }

    @Override
    public void onDisable() {
        if (cycleTask != null) cycleTask.cancel();
        if (investManager != null) investManager.shutdown();
        getLogger().info("InvestPlugin disabled. Data saved.");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        economy = rsp.getProvider();
        return economy != null;
    }

    /** Reloads config.yml. Storage settings only take effect after a restart. */
    public void reloadPlugin() {
        reloadConfig();
    }

    public static InvestPlugin getInstance() {
        return instance;
    }

    public Economy getEconomy() {
        return economy;
    }

    public InvestManager getInvestManager() {
        return investManager;
    }

    public InvestActions getActions() {
        return actions;
    }

    // ── Text helpers (all text comes from config.yml) ─────────────────────────

    public static String colorize(String text) {
        return text.replace("&", "\u00A7");
    }

    /** Replaces {placeholders} then applies colour codes. */
    public String apply(String raw, Map<String, String> ph) {
        String s = raw;
        for (Map.Entry<String, String> e : ph.entrySet()) {
            s = s.replace("{" + e.getKey() + "}", e.getValue());
        }
        return colorize(s);
    }

    /** Any config string by full path, e.g. "gui.title". */
    public String text(String path, Map<String, String> ph) {
        return apply(getConfig().getString(path, "&cMissing config: " + path), ph);
    }

    public List<String> textList(String path, Map<String, String> ph) {
        List<String> out = new ArrayList<>();
        for (String line : getConfig().getStringList(path)) out.add(apply(line, ph));
        return out;
    }

    /** A message from messages.* with the prefix. */
    public String msg(String key, Map<String, String> ph) {
        return colorize(getConfig().getString("messages.prefix", "")) + text("messages." + key, ph);
    }

    public String money(double v) {
        return Money.format(v, getConfig().getString("currency-symbol", "$"));
    }

    public String signedMoney(double v) {
        return Money.signed(v, getConfig().getString("currency-symbol", "$"));
    }

    /** Colour code for a gain (positive), loss (negative) or neutral (zero) value. */
    public String colorFor(double v) {
        if (v > 0) return getConfig().getString("colors.gain", "&a");
        if (v < 0) return getConfig().getString("colors.loss", "&c");
        return getConfig().getString("colors.neutral", "&7");
    }
}
