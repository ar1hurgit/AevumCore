package me.ar1hurgit.aevumcore.modules.lastconnection;

import me.ar1hurgit.aevumcore.storage.data.SessionData;
import me.ar1hurgit.aevumcore.storage.database.DatabaseManager;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class LastConnectionRepository {

    private final DatabaseManager databaseManager;

    public LastConnectionRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public void createTableIfNeeded() throws SQLException {
        try (Connection con = databaseManager.getConnection();
             PreparedStatement stmt = con.prepareStatement(
                      "CREATE TABLE IF NOT EXISTS player_sessions (" +
                              idColumnDefinition() +
                              "uuid VARCHAR(36) NOT NULL," +
                              "login_time BIGINT NOT NULL," +
                              "logout_time BIGINT NOT NULL," +
                              "duration BIGINT NOT NULL" +
                              ")"
              )) {
            stmt.executeUpdate();
        }

        try (Connection con = databaseManager.getConnection()) {
            ensureIndex(con, "player_sessions", "idx_player_sessions_uuid_logout", "CREATE INDEX idx_player_sessions_uuid_logout ON player_sessions(uuid, logout_time)");
        }
    }

    public void insertSession(LastConnectionManager.SessionSnapshot snapshot) throws SQLException {
        try (Connection con = databaseManager.getConnection();
             PreparedStatement stmt = con.prepareStatement(
                     "INSERT INTO player_sessions (uuid, login_time, logout_time, duration) VALUES (?, ?, ?, ?)"
             )) {
            stmt.setString(1, snapshot.uuid().toString());
            stmt.setLong(2, snapshot.loginAt());
            stmt.setLong(3, snapshot.logoutAt());
            stmt.setLong(4, snapshot.duration());
            stmt.executeUpdate();
        }
    }

    public List<SessionData> fetchRecentSessions(UUID uuid, int limit) throws SQLException {
        try (Connection con = databaseManager.getConnection();
             PreparedStatement stmt = con.prepareStatement(
                      "SELECT login_time, logout_time, duration FROM player_sessions WHERE uuid = ? ORDER BY logout_time DESC LIMIT ?"
             )) {
            stmt.setString(1, uuid.toString());
            stmt.setInt(2, limit);

            ResultSet rs = stmt.executeQuery();
            List<SessionData> sessions = new ArrayList<>();
            while (rs.next()) {
                sessions.add(new SessionData(
                        uuid,
                        rs.getLong("login_time"),
                        rs.getLong("logout_time"),
                        rs.getLong("duration")
                ));
            }
            return sessions;
        }
    }

    public PlayerStats fetchPlayerStats(UUID uuid, int limit) throws SQLException {
        try (Connection con = databaseManager.getConnection()) {
            List<SessionData> sessions = loadRecentSessions(con, uuid, limit);
            long totalPlaytime = loadTotalPlaytime(con, uuid);
            return new PlayerStats(sessions, totalPlaytime);
        }
    }

    public long fetchTotalPlaytime(UUID uuid) throws SQLException {
        try (Connection con = databaseManager.getConnection()) {
            return loadTotalPlaytime(con, uuid);
        }
    }

    private List<SessionData> loadRecentSessions(Connection con, UUID uuid, int limit) throws SQLException {
        try (PreparedStatement stmt = con.prepareStatement(
                "SELECT login_time, logout_time, duration FROM player_sessions WHERE uuid = ? ORDER BY logout_time DESC LIMIT ?"
        )) {
            stmt.setString(1, uuid.toString());
            stmt.setInt(2, limit);

            ResultSet rs = stmt.executeQuery();
            List<SessionData> sessions = new ArrayList<>();
            while (rs.next()) {
                sessions.add(new SessionData(
                        uuid,
                        rs.getLong("login_time"),
                        rs.getLong("logout_time"),
                        rs.getLong("duration")
                ));
            }
            return sessions;
        }
    }

    private long loadTotalPlaytime(Connection con, UUID uuid) throws SQLException {
        try (PreparedStatement stmt = con.prepareStatement("SELECT SUM(duration) AS total FROM player_sessions WHERE uuid = ?")) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (!rs.next()) {
                return 0L;
            }

            return Math.max(0L, rs.getLong("total"));
        }
    }

    private void ensureIndex(Connection con, String tableName, String indexName, String createSql) throws SQLException {
        if (hasIndex(con, tableName, indexName)) {
            return;
        }

        try (PreparedStatement stmt = con.prepareStatement(createSql)) {
            stmt.executeUpdate();
        }
    }

    private boolean hasIndex(Connection con, String tableName, String indexName) throws SQLException {
        DatabaseMetaData metaData = con.getMetaData();
        try (ResultSet indexes = metaData.getIndexInfo(null, null, tableName, false, false)) {
            while (indexes.next()) {
                String currentName = indexes.getString("INDEX_NAME");
                if (currentName != null && currentName.equalsIgnoreCase(indexName)) {
                    return true;
                }
            }
            return false;
        }
    }

    private String idColumnDefinition() {
        if (databaseManager.isMySql()) {
            return "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,";
        }
        return "id INTEGER PRIMARY KEY AUTOINCREMENT,";
    }

    public record PlayerStats(List<SessionData> recentSessions, long totalPlaytime) {
    }
}
