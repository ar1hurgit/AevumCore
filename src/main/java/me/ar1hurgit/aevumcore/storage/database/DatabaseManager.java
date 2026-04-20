package me.ar1hurgit.aevumcore.storage.database;

import me.ar1hurgit.aevumcore.AevumCore;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DatabaseManager {

    private final AevumCore plugin;

    private MySQL mySQL;
    private SQLite sqLite;
    private ExecutorService databaseExecutor;

    private DatabaseType type = DatabaseType.SQLITE;

    public DatabaseManager(AevumCore plugin) {
        this.plugin = plugin;
    }

    public void connect() {
        FileConfiguration config = plugin.getConfig();

        type = DatabaseType.fromConfig(config.getString("database.type", "SQLITE"));
        closeExecutor();

        if (type == DatabaseType.MYSQL) {
            mySQL = new MySQL();
            int poolSize = config.getInt("database.mysql.pool-size", 5);

            mySQL.connect(
                    config.getString("database.mysql.host"),
                    config.getInt("database.mysql.port"),
                    config.getString("database.mysql.database"),
                    config.getString("database.mysql.username"),
                    config.getString("database.mysql.password"),
                    poolSize
            );
            databaseExecutor = Executors.newVirtualThreadPerTaskExecutor();

        } else {
            sqLite = new SQLite();
            File file = new File(plugin.getDataFolder(), config.getString("database.sqlite.file", "data.db"));
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            sqLite.connect(file);
            databaseExecutor = Executors.newSingleThreadExecutor(Thread.ofPlatform()
                    .name("AevumCore-SQLite-", 0)
                    .factory());
        }
    }

    public Connection getConnection() throws SQLException {
        if (type == DatabaseType.MYSQL) {
            return mySQL.getConnection();
        } else {
            return sqLite.getConnection();
        }
    }

    public DatabaseType getType() {
        return type;
    }

    public boolean isMySql() {
        return type == DatabaseType.MYSQL;
    }

    public boolean isSqlite() {
        return type == DatabaseType.SQLITE;
    }

    public boolean isUniqueConstraintViolation(SQLException exception) {
        String sqlState = exception.getSQLState();
        if ("23505".equals(sqlState) || "23000".equals(sqlState)) {
            return true;
        }

        String message = exception.getMessage();
        if (message == null) {
            return false;
        }

        String normalized = message.toLowerCase();
        return normalized.contains("unique constraint")
                || normalized.contains("duplicate entry")
                || normalized.contains("unique index")
                || normalized.contains("constraint failed");
    }

    public <T> CompletableFuture<T> supplyAsync(SqlSupplier<T> supplier) {
        if (databaseExecutor == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Database executor not initialized."));
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                return supplier.get();
            } catch (Exception exception) {
                throw new CompletionException(exception);
            }
        }, databaseExecutor);
    }

    public CompletableFuture<Void> runAsync(SqlRunnable runnable) {
        return supplyAsync(() -> {
            runnable.run();
            return null;
        });
    }

    /**
     * Executes a DDL statement (CREATE TABLE, etc.) synchronously.
     */
    public void execute(String sql) {
        try (Connection con = getConnection();
             PreparedStatement stmt = con.prepareStatement(sql)) {
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("[DatabaseManager] Erreur lors de l'exécution SQL : " + e.getMessage());
        }
    }

    public void disconnect() {
        closeExecutor();
        if (mySQL != null) mySQL.disconnect();
        if (sqLite != null) sqLite.disconnect();
    }

    private void closeExecutor() {
        if (databaseExecutor != null) {
            databaseExecutor.shutdownNow();
            databaseExecutor = null;
        }
    }

    @FunctionalInterface
    public interface SqlSupplier<T> {
        T get() throws Exception;
    }

    @FunctionalInterface
    public interface SqlRunnable {
        void run() throws Exception;
    }
}
