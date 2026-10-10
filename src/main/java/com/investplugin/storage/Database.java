package com.investplugin.storage;

import com.investplugin.model.Account;
import com.investplugin.model.HistoryRow;
import com.investplugin.model.MarketDay;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * JDBC storage for SQLite and MariaDB. The SQL is deliberately the common subset of both
 * (REPLACE INTO, plain column types) so one code path serves both backends.
 * All methods are blocking and synchronized: call them from the DB executor thread.
 */
public final class Database {

    public record Settings(String type, String prefix, File sqliteFile, String host, int port,
                           String database, String user, String password, String sslMode) {}

    private final Logger log;
    private final Settings settings;
    private final boolean sqlite;
    private final String tAccounts;
    private final String tMeta;
    private final String tMarket;
    private final String tHistory;
    private Connection conn;

    public Database(Logger log, Settings settings) {
        this.log = log;
        this.settings = settings;
        this.sqlite = !"mariadb".equalsIgnoreCase(settings.type());
        String p = settings.prefix() == null ? "" : settings.prefix().replaceAll("[^A-Za-z0-9_]", "");
        this.tAccounts = p + "accounts";
        this.tMeta = p + "meta";
        this.tMarket = p + "market";
        this.tHistory = p + "history";
    }

    public boolean isSqlite() {
        return sqlite;
    }

    // ── Connection handling ───────────────────────────────────────────────────

    private Connection open() throws SQLException {
        try {
            if (sqlite) {
                Class.forName("org.sqlite.JDBC");
                File dir = settings.sqliteFile().getParentFile();
                if (dir != null) dir.mkdirs();
                Connection c = DriverManager.getConnection("jdbc:sqlite:" + settings.sqliteFile().getAbsolutePath());
                try (Statement st = c.createStatement()) {
                    st.execute("PRAGMA journal_mode=WAL");
                    st.execute("PRAGMA busy_timeout=5000");
                    st.execute("PRAGMA synchronous=NORMAL");
                }
                return c;
            }
            Class.forName("org.mariadb.jdbc.Driver");
            String url = "jdbc:mariadb://" + settings.host() + ":" + settings.port() + "/" + settings.database()
                    + "?sslMode=" + settings.sslMode() + "&connectTimeout=5000";
            return DriverManager.getConnection(url, settings.user(), settings.password());
        } catch (ClassNotFoundException e) {
            throw new SQLException("JDBC driver not found: " + e.getMessage(), e);
        }
    }

    private Connection conn() throws SQLException {
        if (conn == null || conn.isClosed() || !conn.isValid(2)) {
            closeQuietly();
            conn = open();
        }
        return conn;
    }

    private void closeQuietly() {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException ignored) {
                // nothing useful to do
            }
            conn = null;
        }
    }

    public synchronized void close() {
        closeQuietly();
    }

    private interface Work {
        void run(Connection c) throws SQLException;
    }

    private void tx(Work work) throws SQLException {
        Connection c = conn();
        boolean previous = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            work.run(c);
            c.commit();
        } catch (SQLException | RuntimeException e) {
            try {
                c.rollback();
            } catch (SQLException ignored) {
                // original exception is the interesting one
            }
            throw e;
        } finally {
            try {
                c.setAutoCommit(previous);
            } catch (SQLException ignored) {
                // connection will be re-validated on next use
            }
        }
    }

    // ── Schema ────────────────────────────────────────────────────────────────

    public synchronized void init() throws SQLException {
        try (Statement st = conn().createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + tAccounts + " ("
                    + "player_uuid VARCHAR(36) NOT NULL PRIMARY KEY, "
                    + "capital DOUBLE NOT NULL DEFAULT 0, "
                    + "pot DOUBLE NOT NULL DEFAULT 0, "
                    + "auto_invest TINYINT NOT NULL DEFAULT 0, "
                    + "frozen TINYINT NOT NULL DEFAULT 0)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + tMeta + " ("
                    + "meta_key VARCHAR(64) NOT NULL PRIMARY KEY, "
                    + "meta_value VARCHAR(255) NOT NULL)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + tMarket + " ("
                    + "cycle_time BIGINT NOT NULL PRIMARY KEY, "
                    + "pct DOUBLE NOT NULL)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + tHistory + " ("
                    + "player_uuid VARCHAR(36) NOT NULL, "
                    + "cycle_time BIGINT NOT NULL, "
                    + "pct DOUBLE NOT NULL, "
                    + "delta DOUBLE NOT NULL, "
                    + "PRIMARY KEY (player_uuid, cycle_time))");
        }
        log.info("Storage ready (" + (sqlite ? "SQLite: " + settings.sqliteFile().getName()
                : "MariaDB: " + settings.host() + ":" + settings.port() + "/" + settings.database()) + ")");
    }

    // ── Accounts ──────────────────────────────────────────────────────────────

    public synchronized Map<UUID, Account> loadAccounts() throws SQLException {
        Map<UUID, Account> out = new HashMap<>();
        try (Statement st = conn().createStatement();
             ResultSet rs = st.executeQuery("SELECT player_uuid, capital, pot, auto_invest, frozen FROM " + tAccounts)) {
            while (rs.next()) {
                UUID id;
                try {
                    id = UUID.fromString(rs.getString(1));
                } catch (IllegalArgumentException e) {
                    log.warning("Skipping account with invalid UUID: " + rs.getString(1));
                    continue;
                }
                out.put(id, new Account(id, rs.getDouble(2), rs.getDouble(3), rs.getInt(4) != 0, rs.getInt(5) != 0));
            }
        }
        return out;
    }

    private String replaceAccountSql() {
        return "REPLACE INTO " + tAccounts + " (player_uuid, capital, pot, auto_invest, frozen) VALUES (?, ?, ?, ?, ?)";
    }

    private void bindAccount(PreparedStatement ps, Account a) throws SQLException {
        ps.setString(1, a.getUuid().toString());
        ps.setDouble(2, a.getCapital());
        ps.setDouble(3, a.getPot());
        ps.setInt(4, a.isAutoInvest() ? 1 : 0);
        ps.setInt(5, a.isFrozen() ? 1 : 0);
    }

    public synchronized void saveAccount(Account a) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement(replaceAccountSql())) {
            bindAccount(ps, a);
            ps.executeUpdate();
        }
    }

    public synchronized void saveAccounts(Collection<Account> accounts) throws SQLException {
        if (accounts.isEmpty()) return;
        tx(c -> {
            try (PreparedStatement ps = c.prepareStatement(replaceAccountSql())) {
                for (Account a : accounts) {
                    bindAccount(ps, a);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        });
    }

    // ── Daily cycle ───────────────────────────────────────────────────────────

    /** Writes one finished cycle atomically: market row, changed accounts, history rows, last-cycle marker, pruning. */
    public synchronized void saveCycle(long time, double pct, Collection<Account> changed, Collection<HistoryRow> rows,
                                       long pruneBefore, long newLastCycle) throws SQLException {
        tx(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "REPLACE INTO " + tMarket + " (cycle_time, pct) VALUES (?, ?)")) {
                ps.setLong(1, time);
                ps.setDouble(2, pct);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(replaceAccountSql())) {
                for (Account a : changed) {
                    bindAccount(ps, a);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "REPLACE INTO " + tHistory + " (player_uuid, cycle_time, pct, delta) VALUES (?, ?, ?, ?)")) {
                for (HistoryRow r : rows) {
                    ps.setString(1, r.uuid().toString());
                    ps.setLong(2, r.time());
                    ps.setDouble(3, r.pct());
                    ps.setDouble(4, r.delta());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "REPLACE INTO " + tMeta + " (meta_key, meta_value) VALUES (?, ?)")) {
                ps.setString(1, "last_cycle");
                ps.setString(2, Long.toString(newLastCycle));
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM " + tMarket + " WHERE cycle_time < ?")) {
                ps.setLong(1, pruneBefore);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM " + tHistory + " WHERE cycle_time < ?")) {
                ps.setLong(1, pruneBefore);
                ps.executeUpdate();
            }
        });
    }

    public synchronized List<MarketDay> loadMarket(long since) throws SQLException {
        List<MarketDay> out = new ArrayList<>();
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT cycle_time, pct FROM " + tMarket + " WHERE cycle_time >= ? ORDER BY cycle_time ASC")) {
            ps.setLong(1, since);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(new MarketDay(rs.getLong(1), rs.getDouble(2)));
            }
        }
        return out;
    }

    public synchronized List<HistoryRow> loadHistory(long since) throws SQLException {
        List<HistoryRow> out = new ArrayList<>();
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT player_uuid, cycle_time, pct, delta FROM " + tHistory
                        + " WHERE cycle_time >= ? ORDER BY cycle_time ASC")) {
            ps.setLong(1, since);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        out.add(new HistoryRow(UUID.fromString(rs.getString(1)), rs.getLong(2),
                                rs.getDouble(3), rs.getDouble(4)));
                    } catch (IllegalArgumentException ignored) {
                        // skip malformed row
                    }
                }
            }
        }
        return out;
    }

    // ── Meta ──────────────────────────────────────────────────────────────────

    public synchronized String getMeta(String key) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT meta_value FROM " + tMeta + " WHERE meta_key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    public synchronized void setMeta(String key, String value) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement(
                "REPLACE INTO " + tMeta + " (meta_key, meta_value) VALUES (?, ?)")) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        }
    }
}
