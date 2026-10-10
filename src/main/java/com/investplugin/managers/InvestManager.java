package com.investplugin.managers;

import com.investplugin.InvestPlugin;
import com.investplugin.managers.InvestRules.DepositStatus;
import com.investplugin.managers.InvestRules.Rules;
import com.investplugin.managers.InvestRules.Withdrawal;
import com.investplugin.managers.InvestRules.WithdrawStatus;
import com.investplugin.migration.YamlMigrator;
import com.investplugin.model.Account;
import com.investplugin.model.HistoryRow;
import com.investplugin.model.MarketDay;
import com.investplugin.storage.Database;
import com.investplugin.util.Money;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Owns all account state (in memory, main thread only) and persists every change asynchronously
 * through a single DB thread, so the main thread never waits on SQLite/MariaDB.
 */
public final class InvestManager {

    private interface DbTask {
        void run(Database db) throws SQLException;
    }

    private final InvestPlugin plugin;
    private final Map<UUID, Account> accounts = new HashMap<>();
    private final List<MarketDay> market = new ArrayList<>();
    private final Map<UUID, List<HistoryRow>> history = new HashMap<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "InvestPlugin-DB");
        t.setDaemon(true);
        return t;
    });

    private Database db;
    private long lastCycle;

    public InvestManager(InvestPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void load() throws Exception {
        db = new Database(plugin.getLogger(), readDbSettings());
        db.init();
        accounts.putAll(db.loadAccounts());

        long now = System.currentTimeMillis();
        long since = now - windowMillis();
        market.addAll(db.loadMarket(since));
        for (HistoryRow row : db.loadHistory(since)) {
            history.computeIfAbsent(row.uuid(), k -> new ArrayList<>()).add(row);
        }

        String last = db.getMeta("last_cycle");
        try {
            lastCycle = last == null ? now : Long.parseLong(last);
        } catch (NumberFormatException e) {
            lastCycle = now;
        }
        if (last == null) db.setMeta("last_cycle", Long.toString(lastCycle));

        migrateYaml();
        plugin.getLogger().info("Loaded " + accounts.size() + " investment accounts.");
    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(15, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("Timed out waiting for pending database writes.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (db != null) {
            try {
                db.saveAccounts(accounts.values()); // final safety flush
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Final save failed", e);
            }
            db.close();
        }
    }

    private Database.Settings readDbSettings() {
        FileConfiguration c = plugin.getConfig();
        String type = c.getString("storage.type", "sqlite").trim().toLowerCase(Locale.ROOT);
        if (!type.equals("sqlite") && !type.equals("mariadb")) {
            plugin.getLogger().warning("Unknown storage.type '" + type + "', using sqlite.");
            type = "sqlite";
        }
        return new Database.Settings(
                type,
                c.getString("storage.table-prefix", "invest_"),
                new File(plugin.getDataFolder(), c.getString("storage.sqlite.file", "invest.db")),
                c.getString("storage.mariadb.host", "localhost"),
                c.getInt("storage.mariadb.port", 3306),
                c.getString("storage.mariadb.database", "invest"),
                c.getString("storage.mariadb.username", "invest"),
                c.getString("storage.mariadb.password", ""),
                c.getString("storage.mariadb.ssl-mode", "disable"));
    }

    /** One-off import of the old 1.x data.yml. The file is renamed afterwards so it never runs twice. */
    private void migrateYaml() throws SQLException {
        File yaml = new File(plugin.getDataFolder(), "data.yml");
        if (!yaml.isFile() || !plugin.getConfig().getBoolean("migrate-yaml", true)) return;

        Rules rules = rules();
        List<Account> imported = YamlMigrator.read(yaml, accounts.keySet(), defaultAutoInvest(), plugin.getLogger());
        for (Account a : imported) InvestRules.refreshFreeze(a, rules);

        db.saveAccounts(imported); // if this throws, data.yml stays untouched and we retry next start
        for (Account a : imported) accounts.put(a.getUuid(), a);

        Path src = yaml.toPath();
        Path dst = src.resolveSibling("data.yml.migrated");
        if (Files.exists(dst)) dst = src.resolveSibling("data.yml.migrated-" + System.currentTimeMillis());
        try {
            Files.move(src, dst, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            plugin.getLogger().warning("Migrated data.yml but could not rename it: " + e.getMessage()
                    + " (already-migrated players are skipped on the next start)");
        }
        plugin.getLogger().info("Migrated " + imported.size() + " accounts from data.yml (renamed to " + dst.getFileName() + ").");
    }

    // ── Config helpers ────────────────────────────────────────────────────────

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    public Rules rules() {
        FileConfiguration c = cfg();
        double fee = Math.min(100.0, Math.max(0.0, c.getDouble("withdraw-fee-percent", 5.0)));
        return new Rules(c.getDouble("max-capital", 1_000_000.0), c.getDouble("freeze-threshold", 2_000_000.0), fee);
    }

    public boolean defaultAutoInvest() {
        return cfg().getBoolean("default-auto-invest", false);
    }

    public long intervalMillis() {
        return Math.max(60_000L, (long) (cfg().getDouble("daily-interval-hours", 24.0) * 3_600_000L));
    }

    public long windowMillis() {
        return Math.max(1, cfg().getInt("stats-window-days", 7)) * 86_400_000L;
    }

    private long retentionMillis() {
        long days = Math.max(1, cfg().getInt("history-retention-days", 30));
        return Math.max(days * 86_400_000L, windowMillis());
    }

    // ── Accounts ──────────────────────────────────────────────────────────────

    /** The live account, or null if the player never invested. Treat as read-only. */
    public Account getAccount(UUID uuid) {
        return accounts.get(uuid);
    }

    /** The live account, or a fresh unsaved one for display purposes. Treat as read-only. */
    public Account view(UUID uuid) {
        Account a = accounts.get(uuid);
        return a != null ? a : new Account(uuid, 0, 0, defaultAutoInvest(), false);
    }

    private void commit(Account updated) {
        accounts.put(updated.getUuid(), updated);
        Account snapshot = updated.copy();
        submit("save account " + snapshot.getUuid(), d -> d.saveAccount(snapshot));
    }

    private void submit(String what, DbTask task) {
        try {
            executor.execute(() -> {
                try {
                    task.run(db);
                } catch (SQLException | RuntimeException e) {
                    plugin.getLogger().log(Level.SEVERE, "Database error (" + what + ")", e);
                }
            });
        } catch (RejectedExecutionException ignored) {
            // shutting down; the final flush in shutdown() covers it
        }
    }

    // ── Player operations (main thread) ───────────────────────────────────────

    public DepositStatus deposit(Player player, double amount) {
        UUID id = player.getUniqueId();
        Account existing = accounts.get(id);
        Account work = existing != null ? existing.copy() : new Account(id, 0, 0, defaultAutoInvest(), false);

        DepositStatus status = InvestRules.deposit(work, amount, rules());
        if (status != DepositStatus.OK) return status;

        double charged = Money.round2(amount);
        Economy eco = plugin.getEconomy();
        if (!eco.has(player, charged)) return DepositStatus.NO_FUNDS;
        EconomyResponse resp = eco.withdrawPlayer(player, charged);
        if (!resp.transactionSuccess()) return DepositStatus.ECONOMY_ERROR;

        commit(work);
        return DepositStatus.OK;
    }

    public Withdrawal withdraw(Player player, double amount) {
        UUID id = player.getUniqueId();
        Account existing = accounts.get(id);
        if (existing == null) return Withdrawal.fail(WithdrawStatus.NOT_ENOUGH);

        Account work = existing.copy();
        Withdrawal result = InvestRules.withdraw(work, amount, rules());
        if (result.status() != WithdrawStatus.OK) return result;

        EconomyResponse resp = plugin.getEconomy().depositPlayer(player, result.received());
        if (!resp.transactionSuccess()) return Withdrawal.fail(WithdrawStatus.ECONOMY_ERROR);

        commit(work);
        return result;
    }

    /** Flips auto-invest and returns the new state. */
    public boolean toggleAuto(UUID uuid) {
        Account existing = accounts.get(uuid);
        Account work = existing != null ? existing.copy() : new Account(uuid, 0, 0, defaultAutoInvest(), false);
        work.setAutoInvest(!work.isAutoInvest());
        commit(work);
        return work.isAutoInvest();
    }

    // ── Daily cycle ───────────────────────────────────────────────────────────

    /** Called every minute by the plugin; runs the daily cycle once the interval has elapsed. */
    public void checkSchedule() {
        long now = System.currentTimeMillis();
        long next = lastCycle + intervalMillis();
        if (now < next) return;
        // after a long downtime run a single catch-up cycle instead of many
        long newLast = (now - next >= intervalMillis()) ? now : next;
        runCycle(now, newLast);
    }

    /** Admin: run a cycle right now (also restarts the timer). Returns the market % that was applied. */
    public double forceCycle() {
        long now = System.currentTimeMillis();
        return runCycle(now, now);
    }

    private double runCycle(long now, long newLastCycle) {
        FileConfiguration c = cfg();
        double min = c.getDouble("daily-min-percent", -1.0);
        double max = c.getDouble("daily-max-percent", 2.0);
        if (min > max) {
            double t = min;
            min = max;
            max = t;
        }
        double pct = Money.round2(min + ThreadLocalRandom.current().nextDouble() * (max - min));

        Rules rules = rules();
        List<Account> changed = new ArrayList<>();
        List<HistoryRow> rows = new ArrayList<>();
        for (Account a : accounts.values()) {
            if (a.isFrozen() || a.getCapital() <= 0) continue; // frozen / empty accounts get no growth
            double delta = InvestRules.applyDaily(a, pct, rules);
            HistoryRow row = new HistoryRow(a.getUuid(), now, pct, delta);
            rows.add(row);
            history.computeIfAbsent(a.getUuid(), k -> new ArrayList<>()).add(row);
            changed.add(a.copy());
        }
        market.add(new MarketDay(now, pct));
        lastCycle = newLastCycle;
        pruneMemory(now);

        long pruneBefore = now - retentionMillis();
        submit("daily cycle", d -> d.saveCycle(now, pct, changed, rows, pruneBefore, newLastCycle));

        plugin.getLogger().info(String.format(Locale.US, "Daily cycle: %+.2f%% applied to %d accounts.", pct, changed.size()));
        if (c.getBoolean("broadcast-daily-change", true)) {
            Map<String, String> ph = new HashMap<>();
            ph.put("percent", Money.percent(pct));
            ph.put("color", plugin.colorFor(pct));
            Bukkit.broadcastMessage(plugin.msg("daily-broadcast", ph));
        }
        return pct;
    }

    private void pruneMemory(long now) {
        long cutoff = now - windowMillis();
        market.removeIf(m -> m.time() < cutoff);
        history.values().forEach(list -> list.removeIf(h -> h.time() < cutoff));
        history.values().removeIf(List::isEmpty);
    }

    // ── Stats for the GUI ─────────────────────────────────────────────────────

    /** Market days inside the stats window, newest first. */
    public List<MarketDay> recentMarket(long now) {
        long cutoff = now - windowMillis();
        List<MarketDay> out = new ArrayList<>();
        for (MarketDay m : market) if (m.time() >= cutoff) out.add(m);
        Collections.reverse(out);
        return out;
    }

    /** This player's cycle results inside the stats window, newest first. */
    public List<HistoryRow> recentHistory(UUID uuid, long now) {
        long cutoff = now - windowMillis();
        List<HistoryRow> out = new ArrayList<>();
        for (HistoryRow h : history.getOrDefault(uuid, List.of())) if (h.time() >= cutoff) out.add(h);
        Collections.reverse(out);
        return out;
    }

    /** Compounded market change over the stats window, in percent. */
    public double marketChangePercent(long now) {
        double factor = 1.0;
        for (MarketDay m : recentMarket(now)) factor *= 1.0 + m.pct() / 100.0;
        return (factor - 1.0) * 100.0;
    }

    /** Sum of this player's daily gains/losses over the stats window. */
    public double gainsTotal(UUID uuid, long now) {
        double sum = 0;
        for (HistoryRow h : recentHistory(uuid, now)) sum += h.delta();
        return Money.round2(sum);
    }

    public long millisUntilNextCycle(long now) {
        return Math.max(0, lastCycle + intervalMillis() - now);
    }
}
