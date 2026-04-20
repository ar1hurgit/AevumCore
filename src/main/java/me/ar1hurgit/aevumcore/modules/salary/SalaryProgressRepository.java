package me.ar1hurgit.aevumcore.modules.salary;

import me.ar1hurgit.aevumcore.storage.database.DatabaseManager;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class SalaryProgressRepository {

    private final DatabaseManager databaseManager;

    public SalaryProgressRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public void initializeSchema() throws SQLException {
        try (Connection con = databaseManager.getConnection();
             PreparedStatement stmt = con.prepareStatement(
                     "CREATE TABLE IF NOT EXISTS player_data (" +
                             "uuid VARCHAR(36) PRIMARY KEY," +
                             "last_salary BIGINT DEFAULT 0," +
                              "salary_progress BIGINT DEFAULT 0" +
                              ")"
              )) {
            stmt.executeUpdate();
            if (!hasColumn(con, "player_data", "salary_progress")) {
                try (PreparedStatement alter = con.prepareStatement("ALTER TABLE player_data ADD COLUMN salary_progress BIGINT DEFAULT 0")) {
                    alter.executeUpdate();
                }
            }
        }
    }

    public long loadProgress(UUID uuid) throws SQLException {
        try (Connection con = databaseManager.getConnection();
             PreparedStatement stmt = con.prepareStatement("SELECT salary_progress FROM player_data WHERE uuid = ?")) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (!rs.next()) {
                return 0L;
            }

            return Math.max(0L, rs.getLong("salary_progress"));
        }
    }

    public void ensurePlayerRow(UUID uuid, long now) throws SQLException {
        try (Connection con = databaseManager.getConnection();
             PreparedStatement insert = con.prepareStatement("INSERT INTO player_data (uuid, last_salary, salary_progress) VALUES (?, ?, ?)")) {
            insert.setString(1, uuid.toString());
            insert.setLong(2, now);
            insert.setLong(3, 0L);
            insert.executeUpdate();
        }
    }

    public void saveProgress(UUID uuid, long progress) throws SQLException {
        long safeProgress = Math.max(0L, progress);
        try (Connection con = databaseManager.getConnection()) {
            boolean updated;
            try (PreparedStatement update = con.prepareStatement("UPDATE player_data SET salary_progress = ? WHERE uuid = ?")) {
                update.setLong(1, safeProgress);
                update.setString(2, uuid.toString());
                updated = update.executeUpdate() > 0;
            }

            if (!updated) {
                try (PreparedStatement insert = con.prepareStatement("INSERT INTO player_data (uuid, last_salary, salary_progress) VALUES (?, ?, ?)")) {
                    insert.setString(1, uuid.toString());
                    insert.setLong(2, System.currentTimeMillis());
                    insert.setLong(3, safeProgress);
                    insert.executeUpdate();
                }
            }
        }
    }

    private boolean hasColumn(Connection con, String tableName, String columnName) throws SQLException {
        DatabaseMetaData metaData = con.getMetaData();
        try (ResultSet columns = metaData.getColumns(null, null, tableName, columnName)) {
            return columns.next();
        }
    }
}
