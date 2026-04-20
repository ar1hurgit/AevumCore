package me.ar1hurgit.aevumcore.modules.nickname;

import me.ar1hurgit.aevumcore.storage.database.DatabaseManager;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

public class NicknameRepository {

    private final DatabaseManager databaseManager;
    private final Logger logger;

    public NicknameRepository(DatabaseManager databaseManager, Logger logger) {
        this.databaseManager = databaseManager;
        this.logger = logger;
    }

    public void initializeSchema() {
        try (Connection con = databaseManager.getConnection();
             PreparedStatement stmt = con.prepareStatement(
                      "CREATE TABLE IF NOT EXISTS player_nicknames (" +
                              "uuid VARCHAR(36) PRIMARY KEY," +
                              "nickname VARCHAR(64)," +
                              "nickname_normalized VARCHAR(64)," +
                              "last_change BIGINT NOT NULL DEFAULT 0" +
                              ")")) {
            stmt.executeUpdate();
            ensureNormalizedColumn(con);
            backfillNormalizedNicknames(con);
            ensureNormalizedIndex(con);
        } catch (SQLException exception) {
            logger.severe("[Nickname] Erreur creation table player_nicknames : " + exception.getMessage());
        }
    }

    public LoadedNicknames loadAllNicknames() {
        Map<UUID, String> nicknames = new HashMap<>();
        Map<UUID, Long> lastChanges = new HashMap<>();

        try (Connection con = databaseManager.getConnection();
             PreparedStatement stmt = con.prepareStatement("SELECT uuid, nickname, last_change FROM player_nicknames")) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                UUID uuid = parseUuid(rs.getString("uuid"));
                if (uuid == null) {
                    continue;
                }

                lastChanges.put(uuid, rs.getLong("last_change"));
                nicknames.put(uuid, rs.getString("nickname"));
            }
        } catch (SQLException exception) {
            logger.severe("[Nickname] Erreur chargement global des pseudos : " + exception.getMessage());
        }

        return new LoadedNicknames(nicknames, lastChanges);
    }

    public PlayerNickname loadPlayer(UUID uuid) {
        try (Connection con = databaseManager.getConnection();
             PreparedStatement stmt = con.prepareStatement("SELECT nickname, last_change FROM player_nicknames WHERE uuid = ?")) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return new PlayerNickname(rs.getString("nickname"), rs.getLong("last_change"));
            }
        } catch (SQLException exception) {
            logger.severe("[Nickname] Erreur chargement joueur " + uuid + " : " + exception.getMessage());
        }

        return new PlayerNickname(null, 0L);
    }

    public UUID findUuidByNickname(String nickname) {
        String normalized = normalizeNickname(nickname);
        if (normalized == null) {
            return null;
        }

        try (Connection con = databaseManager.getConnection();
             PreparedStatement stmt = con.prepareStatement("SELECT uuid FROM player_nicknames WHERE nickname_normalized = ?")) {
            stmt.setString(1, normalized);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return parseUuid(rs.getString("uuid"));
            }
        } catch (SQLException exception) {
            logger.severe("[Nickname] Erreur recherche realname : " + exception.getMessage());
        }

        return null;
    }

    public SaveResult saveNickname(UUID uuid, String nickname, long lastChange) {
        String normalized = normalizeNickname(nickname);
        if (normalized == null) {
            return SaveResult.ERROR;
        }

        try (Connection con = databaseManager.getConnection()) {
            con.setAutoCommit(false);
            try {
                boolean updated;
                try (PreparedStatement stmt = con.prepareStatement(
                        "UPDATE player_nicknames SET nickname = ?, nickname_normalized = ?, last_change = ? WHERE uuid = ?"
                )) {
                    stmt.setString(1, nickname);
                    stmt.setString(2, normalized);
                    stmt.setLong(3, lastChange);
                    stmt.setString(4, uuid.toString());
                    updated = stmt.executeUpdate() > 0;
                }

                if (!updated) {
                    try (PreparedStatement stmt = con.prepareStatement(
                            "INSERT INTO player_nicknames (uuid, nickname, nickname_normalized, last_change) VALUES (?, ?, ?, ?)"
                    )) {
                        stmt.setString(1, uuid.toString());
                        stmt.setString(2, nickname);
                        stmt.setString(3, normalized);
                        stmt.setLong(4, lastChange);
                        stmt.executeUpdate();
                    }
                }

                con.commit();
                return SaveResult.SUCCESS;
            } catch (SQLException exception) {
                con.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            if (databaseManager.isUniqueConstraintViolation(exception)) {
                return SaveResult.ALREADY_TAKEN;
            }
            logger.severe("[Nickname] Erreur changement pseudo " + uuid + " : " + exception.getMessage());
            return SaveResult.ERROR;
        }
    }

    public boolean clearNickname(UUID uuid) {
        try (Connection con = databaseManager.getConnection();
             PreparedStatement stmt = con.prepareStatement("DELETE FROM player_nicknames WHERE uuid = ?")) {
            stmt.setString(1, uuid.toString());
            stmt.executeUpdate();
            return true;
        } catch (SQLException exception) {
            logger.severe("[Nickname] Erreur suppression pseudo " + uuid + " : " + exception.getMessage());
            return false;
        }
    }

    private void ensureNormalizedColumn(Connection con) throws SQLException {
        if (hasColumn(con, "player_nicknames", "nickname_normalized")) {
            return;
        }

        try (PreparedStatement stmt = con.prepareStatement(
                "ALTER TABLE player_nicknames ADD COLUMN nickname_normalized VARCHAR(64)"
        )) {
            stmt.executeUpdate();
        }
    }

    private void backfillNormalizedNicknames(Connection con) throws SQLException {
        try (PreparedStatement stmt = con.prepareStatement(
                "UPDATE player_nicknames SET nickname_normalized = LOWER(nickname) " +
                        "WHERE nickname IS NOT NULL AND nickname_normalized IS NULL"
        )) {
            stmt.executeUpdate();
        }
    }

    private void ensureNormalizedIndex(Connection con) throws SQLException {
        if (hasIndex(con, "player_nicknames", "ux_player_nicknames_normalized")) {
            return;
        }

        try (PreparedStatement stmt = con.prepareStatement(
                "CREATE UNIQUE INDEX ux_player_nicknames_normalized ON player_nicknames(nickname_normalized)"
        )) {
            stmt.executeUpdate();
        }
    }

    private boolean hasColumn(Connection con, String tableName, String columnName) throws SQLException {
        DatabaseMetaData metaData = con.getMetaData();
        try (ResultSet columns = metaData.getColumns(null, null, tableName, columnName)) {
            return columns.next();
        }
    }

    private boolean hasIndex(Connection con, String tableName, String indexName) throws SQLException {
        DatabaseMetaData metaData = con.getMetaData();
        try (ResultSet indexes = metaData.getIndexInfo(null, null, tableName, false, false)) {
            while (indexes.next()) {
                String current = indexes.getString("INDEX_NAME");
                if (current != null && current.equalsIgnoreCase(indexName)) {
                    return true;
                }
            }
            return false;
        }
    }

    private String normalizeNickname(String nickname) {
        if (nickname == null) {
            return null;
        }

        String normalized = nickname.trim().toLowerCase();
        return normalized.isEmpty() ? null : normalized;
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

    public record LoadedNicknames(Map<UUID, String> nicknames, Map<UUID, Long> lastChanges) {
    }

    public record PlayerNickname(String nickname, long lastChange) {
    }

    public enum SaveResult {
        SUCCESS,
        ALREADY_TAKEN,
        ERROR
    }
}
