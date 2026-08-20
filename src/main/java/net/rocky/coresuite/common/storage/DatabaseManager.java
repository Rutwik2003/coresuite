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
                return thread;
            }
        });
    }

    /**
     * Initializes the database connection pool.
     */
    public void initialize() {
        String storageType = plugin.getConfig().getString("storage.type", "SQLITE").toUpperCase();

        if ("MYSQL".equals(storageType)) {
            setupMySQL();
            checkAndMigrate();
        } else {
            setupSQLite();
        }
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

    private void checkAndMigrate() {
        File sqliteFile = new File(plugin.getDataFolder(), "data/coresuite.db");
        if (!sqliteFile.exists()) {
            return;
        }

        plugin.getLogger().info("Found existing SQLite database. Starting automatic migration to MySQL (async)...");
        
        executeAsync(() -> {
            String[] tables = {
                "coresuite_playtime", 
                "coresuite_fishing", 
                "coresuite_fish_logbook", 
                "coresuite_kills", 
                "coresuite_referrals", 
                "coresuite_reward_codes"
            };
            
            boolean migrationSuccess = false;
            try (Connection sqliteConn = java.sql.DriverManager.getConnection("jdbc:sqlite:" + sqliteFile.getAbsolutePath());
                 Connection mysqlConn = getConnection()) {
                 
                 for (String table : tables) {
                     migrateTable(sqliteConn, mysqlConn, table);
                 }
                 migrationSuccess = true;
            } catch (SQLException e) {
                 plugin.getLogger().log(Level.SEVERE, "Failed to migrate SQLite to MySQL", e);
            }
            
            if (migrationSuccess) {
                File migratedFile = new File(plugin.getDataFolder(), "data/coresuite_migrated.db");
                if (sqliteFile.renameTo(migratedFile)) {
                    plugin.getLogger().info("Migration complete. Renamed to coresuite_migrated.db");
                } else {
                    plugin.getLogger().warning("Migration complete, but failed to rename coresuite.db");
                }
            }
        });
    }

    private void migrateTable(Connection sqliteConn, Connection mysqlConn, String tableName) {
        try (java.sql.Statement sqliteStmt = sqliteConn.createStatement();
             java.sql.ResultSet rs = sqliteStmt.executeQuery("SELECT * FROM " + tableName)) {
             
             java.sql.ResultSetMetaData metaData = rs.getMetaData();
             int columnCount = metaData.getColumnCount();
             
             if (columnCount == 0) return;
             
             StringBuilder sql = new StringBuilder("REPLACE INTO " + tableName + " VALUES (");
             for (int i = 0; i < columnCount; i++) {
                 sql.append("?");
                 if (i < columnCount - 1) sql.append(", ");
             }
             sql.append(")");
             
             try (PreparedStatement mysqlStmt = mysqlConn.prepareStatement(sql.toString())) {
                 int count = 0;
                 while (rs.next()) {
                     for (int i = 1; i <= columnCount; i++) {
                         mysqlStmt.setObject(i, rs.getObject(i));
                     }
                     mysqlStmt.addBatch();
                     count++;
                     if (count % 100 == 0) {
                         mysqlStmt.executeBatch();
                     }
                 }
                 mysqlStmt.executeBatch();
                 plugin.getLogger().info("Migrated " + count + " rows into " + tableName);
             }
             
        } catch (SQLException e) {
             plugin.getLogger().warning("Could not migrate table " + tableName + " (it might not exist or failed): " + e.getMessage());
        }
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
        asyncExecutor.execute(() -> {
            try {
                task.run();
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Async database task failed", e);
            }
        });
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
        asyncExecutor.shutdown();
        try {
            if (!asyncExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                asyncExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            asyncExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("Database connection closed.");
        }
    }
}
