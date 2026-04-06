package me.ar1hurgit.aevumcore.modules.lastconnection;

import me.ar1hurgit.aevumcore.AevumCore;
import me.ar1hurgit.aevumcore.core.module.AbstractModule;
import me.ar1hurgit.aevumcore.storage.database.DatabaseManager;
import org.bukkit.Bukkit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class LastConnectionModule extends AbstractModule {

    private final AevumCore plugin;
    private final DatabaseManager databaseManager;

    private LastConnectionListener listener;

    public LastConnectionModule(AevumCore plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    @Override
    public String getName() {
        return "lastconnection";
    }

    @Override
    protected void onEnable() {
        if (!plugin.getConfig().getBoolean("lastconnection.enabled", true)) return;

        // Create table asynchronously
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection con = databaseManager.getConnection();
                 PreparedStatement stmt = con.prepareStatement(
                     "CREATE TABLE IF NOT EXISTS player_sessions (" +
                     "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                     "uuid VARCHAR(36) NOT NULL," +
                     "login_time BIGINT NOT NULL," +
                     "logout_time BIGINT NOT NULL," +
                     "duration BIGINT NOT NULL" +
                     ")"
                 )) {
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("[LastConnection] Erreur lors de la création de la table player_sessions : " + e.getMessage());
            }
        });

        this.listener = new LastConnectionListener(plugin, databaseManager);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);

        LastConnectionCommand command = new LastConnectionCommand(plugin, databaseManager);
        plugin.getCommand("lastconnexion").setExecutor(command);
        plugin.getCommand("lastconnexion").setTabCompleter(command);

        Bukkit.getLogger().info(plugin.getConfig().getString("prefix", "[AevumCore]") + " LastConnection module enabled");
    }

    public LastConnectionListener getListener() {
        return listener;
    }

    @Override
    protected void onDisable() {
        Bukkit.getLogger().info(plugin.getConfig().getString("prefix", "[AevumCore]") + " LastConnection module disabled");
    }
}
