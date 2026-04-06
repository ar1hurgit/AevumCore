package me.ar1hurgit.aevumcore.modules.lastconnection;

import me.ar1hurgit.aevumcore.AevumCore;
import me.ar1hurgit.aevumcore.storage.database.DatabaseManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LastConnectionListener implements Listener {

    private final AevumCore plugin;
    private final DatabaseManager databaseManager;
    // Stores login timestamps for online players
    private final Map<UUID, Long> loginTimes = new HashMap<>();

    public LastConnectionListener(AevumCore plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        loginTimes.put(uuid, System.currentTimeMillis());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        Long loginTime = loginTimes.remove(uuid);
        if (loginTime == null) return;

        long logoutTime = System.currentTimeMillis();
        long duration = logoutTime - loginTime;

        // Insert session asynchronously
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection con = databaseManager.getConnection();
                 PreparedStatement stmt = con.prepareStatement(
                     "INSERT INTO player_sessions (uuid, login_time, logout_time, duration) VALUES (?, ?, ?, ?)"
                 )) {
                stmt.setString(1, uuid.toString());
                stmt.setLong(2, loginTime);
                stmt.setLong(3, logoutTime);
                stmt.setLong(4, duration);
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("[LastConnection] Erreur lors de l'enregistrement de la session : " + e.getMessage());
            }
        });
    }

    public Map<UUID, Long> getLoginTimes() {
        return loginTimes;
    }
}
