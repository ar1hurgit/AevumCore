package me.ar1hurgit.aevumcore.modules.vision;

import me.ar1hurgit.aevumcore.storage.database.DatabaseManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class VisionDataStore {

    private final JavaPlugin plugin;
    private final DatabaseManager databaseManager;
    private final File legacyFile;

    public VisionDataStore(JavaPlugin plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.legacyFile = new File(plugin.getDataFolder(), "vision.yml");
    }

    public Set<UUID> loadEnabledPlayers() {
        initializeSchema();

        Set<UUID> enabledPlayers = loadFromDatabase();
        if (!enabledPlayers.isEmpty()) {
            return enabledPlayers;
        }

        Set<UUID> legacyPlayers = loadFromLegacyYaml();
        if (!legacyPlayers.isEmpty()) {
            saveEnabledPlayers(legacyPlayers);
        }
        return legacyPlayers;
    }

    public void saveEnabledPlayers(Set<UUID> enabledPlayers) {
        initializeSchema();
        try (Connection con = databaseManager.getConnection()) {
            con.setAutoCommit(false);
            try {
                try (PreparedStatement deleteStmt = con.prepareStatement("DELETE FROM vision_state")) {
                    deleteStmt.executeUpdate();
                }

                try (PreparedStatement insertStmt = con.prepareStatement(
                        "INSERT INTO vision_state (uuid, enabled) VALUES (?, ?)"
                )) {
                    for (UUID uuid : enabledPlayers) {
                        insertStmt.setString(1, uuid.toString());
                        insertStmt.setInt(2, 1);
                        insertStmt.addBatch();
                    }
                    insertStmt.executeBatch();
                }

                con.commit();
            } catch (SQLException exception) {
                con.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            plugin.getLogger().severe("[Vision] Impossible de sauvegarder l'etat en base : " + exception.getMessage());
        }
    }

    private void initializeSchema() {
        try (Connection con = databaseManager.getConnection();
             PreparedStatement stmt = con.prepareStatement(
                     "CREATE TABLE IF NOT EXISTS vision_state (" +
                             "uuid VARCHAR(36) PRIMARY KEY," +
                             "enabled INTEGER NOT NULL DEFAULT 1" +
                             ")"
             )) {
            stmt.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().severe("[Vision] Impossible de creer la table vision_state : " + exception.getMessage());
        }
    }

    private Set<UUID> loadFromDatabase() {
        Set<UUID> enabledPlayers = new HashSet<>();

        try (Connection con = databaseManager.getConnection();
             PreparedStatement stmt = con.prepareStatement(
                     "SELECT uuid FROM vision_state WHERE enabled <> 0"
             )) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                UUID uuid = parseUuid(rs.getString("uuid"));
                if (uuid != null) {
                    enabledPlayers.add(uuid);
                }
            }
        } catch (SQLException exception) {
            plugin.getLogger().severe("[Vision] Impossible de charger l'etat depuis la base : " + exception.getMessage());
        }

        return enabledPlayers;
    }

    private Set<UUID> loadFromLegacyYaml() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        if (!legacyFile.exists()) {
            return new HashSet<>();
        }

        FileConfiguration configuration = YamlConfiguration.loadConfiguration(legacyFile);
        List<String> rawList = configuration.getStringList("enabled");
        Set<UUID> enabledPlayers = new HashSet<>();
        for (String raw : rawList) {
            UUID uuid = parseUuid(raw);
            if (uuid != null) {
                enabledPlayers.add(uuid);
            }
        }
        return enabledPlayers;
    }

    private UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
