package me.ar1hurgit.aevumcore.modules.vanish;

import me.ar1hurgit.aevumcore.storage.database.DatabaseManager;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class VanishDataStore {

    private final JavaPlugin plugin;
    private final DatabaseManager databaseManager;
    private final File dataFile;

    public VanishDataStore(JavaPlugin plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.dataFile = new File(plugin.getDataFolder(), "vanish.yml");
    }

    public StoredState load() {
        initializeSchema();

        StoredState storedState = loadFromDatabase();
        if (!storedState.vanishedPlayers().isEmpty() || !storedState.knownNames().isEmpty()) {
            return storedState;
        }

        StoredState legacyState = loadFromLegacyYaml();
        if (!legacyState.vanishedPlayers().isEmpty() || !legacyState.knownNames().isEmpty()) {
            save(legacyState.vanishedPlayers(), legacyState.knownNames());
        }
        return legacyState;
    }

    public void save(Set<UUID> vanishedPlayers, Map<UUID, String> knownNames) {
        initializeSchema();
        try (Connection con = databaseManager.getConnection()) {
            con.setAutoCommit(false);
            try {
                try (PreparedStatement deleteStmt = con.prepareStatement("DELETE FROM vanish_state")) {
                    deleteStmt.executeUpdate();
                }

                try (PreparedStatement insertStmt = con.prepareStatement(
                        "INSERT INTO vanish_state (uuid, known_name, vanished) VALUES (?, ?, ?)"
                )) {
                    Set<UUID> allPlayers = new HashSet<>(knownNames.keySet());
                    allPlayers.addAll(vanishedPlayers);

                    for (UUID uuid : allPlayers) {
                        insertStmt.setString(1, uuid.toString());
                        insertStmt.setString(2, knownNames.get(uuid));
                        insertStmt.setInt(3, vanishedPlayers.contains(uuid) ? 1 : 0);
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
            plugin.getLogger().severe("[Vanish] Impossible de sauvegarder l'etat en base : " + exception.getMessage());
        }
    }

    private void initializeSchema() {
        try (Connection con = databaseManager.getConnection();
             PreparedStatement stmt = con.prepareStatement(
                     "CREATE TABLE IF NOT EXISTS vanish_state (" +
                             "uuid VARCHAR(36) PRIMARY KEY," +
                             "known_name VARCHAR(64)," +
                             "vanished INTEGER NOT NULL DEFAULT 0" +
                             ")"
             )) {
            stmt.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().severe("[Vanish] Impossible de creer la table vanish_state : " + exception.getMessage());
        }
    }

    private StoredState loadFromDatabase() {
        Set<UUID> vanishedPlayers = new HashSet<>();
        Map<UUID, String> knownNames = new HashMap<>();

        try (Connection con = databaseManager.getConnection();
             PreparedStatement stmt = con.prepareStatement(
                     "SELECT uuid, known_name, vanished FROM vanish_state"
             )) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                UUID uuid = parseUuid(rs.getString("uuid"));
                if (uuid == null) {
                    continue;
                }

                String knownName = rs.getString("known_name");
                if (knownName != null && !knownName.isBlank()) {
                    knownNames.put(uuid, knownName);
                }

                if (rs.getInt("vanished") != 0) {
                    vanishedPlayers.add(uuid);
                }
            }
        } catch (SQLException exception) {
            plugin.getLogger().severe("[Vanish] Impossible de charger l'etat depuis la base : " + exception.getMessage());
        }

        return new StoredState(vanishedPlayers, knownNames);
    }

    private StoredState loadFromLegacyYaml() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        if (!dataFile.exists()) {
            return new StoredState(new HashSet<>(), new HashMap<>());
        }

        FileConfiguration configuration = YamlConfiguration.loadConfiguration(dataFile);
        Set<UUID> vanishedPlayers = new HashSet<>();
        for (String raw : configuration.getStringList("vanished")) {
            try {
                vanishedPlayers.add(UUID.fromString(raw));
            } catch (IllegalArgumentException ignored) {
            }
        }

        Map<UUID, String> knownNames = new HashMap<>();
        ConfigurationSection names = configuration.getConfigurationSection("names");
        if (names != null) {
            for (String key : names.getKeys(false)) {
                try {
                    knownNames.put(UUID.fromString(key), names.getString(key, key));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        return new StoredState(vanishedPlayers, knownNames);
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

    public record StoredState(Set<UUID> vanishedPlayers, Map<UUID, String> knownNames) {
    }
}
