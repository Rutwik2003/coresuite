/**
 * CoreSuite Plugin
 * Package: net.rocky.coresuite.common.storage
 * 
 * Manages the SQLite database connection using HikariCP.
 */
package net.rocky.coresuite.common.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.rocky.coresuite.CoreSuitePlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * Manages the SQLite database connection pool and asynchronous operations.
 * Relies on HikariCP for connection pooling.
 */
public final class DatabaseManager {
    private final CoreSuitePlugin plugin;
    private HikariDataSource dataSource;
    private final ExecutorService asyncExecutor;
    private String storageType = "SQLITE";
    private final Object taskLock = new Object();
    private int pendingTasks;
    private boolean acceptingTasks = true;

    /**
     * Constructs a new DatabaseManager.
     *
     * @param plugin The CoreSuite plugin instance.
     */
    public DatabaseManager(CoreSuitePlugin plugin) {
        this.plugin = plugin;
        this.asyncExecutor = Executors.newSingleThreadExecutor(new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(1);
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, "CoreSuite-DB-Thread-" + counter.getAndIncrement());
                thread.setDaemon(true);
                thread.setContextClassLoader(plugin.getClass().getClassLoader());
                return thread;
            }
        });
    }

    /**
     * Initializes the database connection pool.
     */
    public void initialize() {
        this.storageType = plugin.getConfig().getString("storage.type", "SQLITE").toUpperCase();

        if ("MYSQL".equals(storageType)) {
            setupMySQL();
        } else {
            setupSQLite();
        }
    }

    public boolean isMySQL() {
        return "MYSQL".equalsIgnoreCase(storageType);
    }

    private void setupMySQL() {
        String host = plugin.getConfig().getString("storage.mysql.host", "localhost");
        int port = plugin.getConfig().getInt("storage.mysql.port", 3306);
        String database = plugin.getConfig().getString("storage.mysql.database", "coresuite");
        String username = plugin.getConfig().getString("storage.mysql.username", "root");
        String password = plugin.getConfig().getString("storage.mysql.password", "");

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?autoReconnect=true&useSSL=false");
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(10000);
        config.setPoolName("CoreSuite-DB-MySQL");
        
        dataSource = new HikariDataSource(config);
        plugin.getLogger().info("MySQL Database connected: " + host + ":" + port);
    }

    private void setupSQLite() {
        // Get db path from config (default: data/coresuite.db)
        String dbPath = plugin.getConfig().getString("storage.database", "data/coresuite.db");
        File dbFile = new File(plugin.getDataFolder(), dbPath);
        if (dbFile.getParentFile() != null) {
            dbFile.getParentFile().mkdirs();
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
        config.setMaximumPoolSize(1); // SQLite single-writer
        config.setMinimumIdle(1);
        config.setConnectionTimeout(10000);
        config.setPoolName("CoreSuite-DB");
        
        // SQLite performance pragmas
        config.addDataSourceProperty("journal_mode", "WAL");
        config.addDataSourceProperty("synchronous", "NORMAL");
        config.addDataSourceProperty("foreign_keys", "ON");

        dataSource = new HikariDataSource(config);
        plugin.getLogger().info("Database connected: " + dbFile.getPath());
    }

    public void checkAndMigrate() {
        if (!isMySQL()) return;
        File sqliteFile = new File(plugin.getDataFolder(), plugin.getConfig().getString("storage.database", "data/coresuite.db"));
        if (!sqliteFile.exists()) return;

        executeAsync(() -> {
            // Never label a partial copy as complete, or overwrite an active MySQL database.
            File migratedFile = new File(sqliteFile.getParentFile(), "coresuite_migrated.db");
            if (migratedFile.exists()) {
                plugin.getLogger().warning("Migration skipped: a migrated database backup already exists.");
                return;
            }
            try (Connection sqliteConn = java.sql.DriverManager.getConnection("jdbc:sqlite:" + sqliteFile.getAbsolutePath());
                 Connection mysqlConn = getConnection()) {
                LegacyDatabaseMigration.copy(sqliteConn, mysqlConn);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Migration aborted. SQLite source retained; inspect schemas and use a complete migration before switching storage.", e);
                return;
            }
            if (sqliteFile.renameTo(migratedFile)) {
                plugin.getLogger().info("Legacy migration complete. SQLite source preserved as coresuite_migrated.db");
            } else {
                plugin.getLogger().warning("Legacy copy completed, but source could not be renamed. It has been retained.");
            }
        });
    }

    /**
     * Retrieves a connection from the pool.
     *
     * @return A database connection.
     * @throws SQLException If a database access error occurs or the pool is closed.
     */
    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("Database is not initialized or has been closed.");
        }
        return dataSource.getConnection();
    }

    /**
     * Executes a task asynchronously on the database thread.
     *
     * @param task The task to execute.
     */
    public void executeAsync(Runnable task) {
        synchronized (taskLock) {
            if (!acceptingTasks) {
                throw new java.util.concurrent.RejectedExecutionException("CoreSuite database is closed");
            }
            pendingTasks++;
            try {
                asyncExecutor.execute(() -> {
                    try {
                        task.run();
                    } catch (Throwable t) {
                        plugin.getLogger().log(Level.SEVERE, "Async database task failed", t);
                    } finally {
                        synchronized (taskLock) {
                            pendingTasks--;
                            taskLock.notifyAll();
                        }
                    }
                });
            } catch (RuntimeException e) {
                pendingTasks--;
                taskLock.notifyAll();
                throw e;
            }
        }
    }

    /**
     * Executes a simple SQL update statement asynchronously with parameters.
     *
     * @param sql    The SQL statement.
     * @param params The parameters for the statement.
     */
    public void execute(String sql, Object... params) {
        executeAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                for (int i = 0; i < params.length; i++) {
                    stmt.setObject(i + 1, params[i]);
                }
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "SQL execution failed: " + sql, e);
            }
        });
    }

    /**
     * Closes the connection pool and shuts down the executor.
     */
    public void close() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        try {
            // Keep accepting child writes from already queued jobs until the queue drains.
            // They must remain queued: their parent may still hold SQLite's only connection.
            synchronized (taskLock) {
                while (pendingTasks > 0) {
                    long remaining = deadline - System.nanoTime();
                    if (remaining <= 0) break;
                    TimeUnit.NANOSECONDS.timedWait(taskLock, remaining);
                }
                acceptingTasks = false;
                if (pendingTasks > 0) {
                    plugin.getLogger().warning("Database shutdown timed out with " + pendingTasks + " unfinished tasks.");
                }
            }
            asyncExecutor.shutdown();
            if (!asyncExecutor.awaitTermination(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS)) {
                asyncExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            synchronized (taskLock) {
                acceptingTasks = false;
            }
            asyncExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("Database connection closed.");
        }
    }
}
