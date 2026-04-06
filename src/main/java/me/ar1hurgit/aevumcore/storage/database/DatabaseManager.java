package me.ar1hurgit.aevumcore.storage.database;

import me.ar1hurgit.aevumcore.AevumCore;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class DatabaseManager {

    private final AevumCore plugin;

    private MySQL mySQL;
    private SQLite sqLite;

    private String type;

    public DatabaseManager(AevumCore plugin) {
        this.plugin = plugin;
    }

    public void connect() {
        FileConfiguration config = plugin.getConfig();

        type = config.getString("database.type", "SQLITE");

        if (type.equalsIgnoreCase("MYSQL")) {
            mySQL = new MySQL();

            mySQL.connect(
                    config.getString("database.mysql.host"),
                    config.getInt("database.mysql.port"),
                    config.getString("database.mysql.database"),
                    config.getString("database.mysql.username"),
                    config.getString("database.mysql.password"),
                    config.getInt("database.mysql.pool-size", 5)
            );

        } else {
            sqLite = new SQLite();
            File file = new File(plugin.getDataFolder(), config.getString("database.sqlite.file", "data.db"));
            sqLite.connect(file);
        }
    }

    public Connection getConnection() throws SQLException {
        if (type.equalsIgnoreCase("MYSQL")) {
            return mySQL.getConnection();
        } else {
            return sqLite.getConnection();
        }
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
        if (mySQL != null) mySQL.disconnect();
        if (sqLite != null) sqLite.disconnect();
    }
}