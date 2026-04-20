package me.ar1hurgit.aevumcore.modules.report;

import me.ar1hurgit.aevumcore.storage.database.DatabaseManager;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

public class ReportRepository {

    private final DatabaseManager databaseManager;
    private final Logger logger;

    public ReportRepository(DatabaseManager databaseManager, Logger logger) {
        this.databaseManager = databaseManager;
        this.logger = logger;
    }

    public BootstrapState loadBootstrapState(long cutoff) {
        initializeSchema();
        purgeExpiredReports(cutoff);

        List<ReportRecord> openReports = new ArrayList<>();
        Map<UUID, Long> cooldowns = new HashMap<>();
        long nextReportId = 1L;

        try (Connection con = databaseManager.getConnection()) {
            openReports.addAll(loadOpenReports(con, cutoff));
            cooldowns.putAll(loadCooldowns(con));
            nextReportId = loadNextReportId(con);
        } catch (SQLException exception) {
            logger.severe("[Report] Impossible de charger l'etat initial des reports : " + exception.getMessage());
        }

        return new BootstrapState(openReports, cooldowns, nextReportId);
    }

    public boolean storeNewReport(ReportRecord record) {
        try (Connection con = databaseManager.getConnection()) {
            insertReport(con, record);
            updateCooldown(con, record.reporterUuid(), record.createdAt());
            return true;
        } catch (SQLException exception) {
            logger.severe("[Report] Impossible d'envoyer le report #" + record.id() + " : " + exception.getMessage());
            return false;
        }
    }

    public int claimReport(long reportId, UUID staffUuid, String staffName, long claimAt, boolean override) {
        try (Connection con = databaseManager.getConnection();
             PreparedStatement stmt = con.prepareStatement(buildClaimSql(override))) {
            stmt.setString(1, staffUuid.toString());
            stmt.setString(2, staffName);
            stmt.setLong(3, claimAt);
            stmt.setLong(4, reportId);
            return stmt.executeUpdate();
        } catch (SQLException exception) {
            logger.severe("[Report] Impossible de prendre en charge le report #" + reportId + " : " + exception.getMessage());
            return 0;
        }
    }

    public int closeReport(long reportId, UUID actorUuid, String actorName, long closedAt, boolean override) {
        try (Connection con = databaseManager.getConnection();
             PreparedStatement stmt = con.prepareStatement(buildCloseSql(override))) {
            stmt.setString(1, actorUuid == null ? null : actorUuid.toString());
            stmt.setString(2, actorName);
            stmt.setLong(3, closedAt);
            stmt.setLong(4, reportId);
            if (!override && actorUuid != null) {
                stmt.setString(5, actorUuid.toString());
            }
            return stmt.executeUpdate();
        } catch (SQLException exception) {
            logger.severe("[Report] Impossible de fermer le report #" + reportId + " : " + exception.getMessage());
            return 0;
        }
    }

    public boolean purgeExpiredReports(long cutoff) {
        try (Connection con = databaseManager.getConnection();
             PreparedStatement stmt = con.prepareStatement("DELETE FROM reports WHERE created_at < ?")) {
            stmt.setLong(1, cutoff);
            stmt.executeUpdate();
            return true;
        } catch (SQLException exception) {
            logger.severe("[Report] Impossible de purger les reports expires : " + exception.getMessage());
            return false;
        }
    }

    private void initializeSchema() {
        try (Connection con = databaseManager.getConnection();
             PreparedStatement reportsStmt = con.prepareStatement(
                     "CREATE TABLE IF NOT EXISTS reports (" +
                             "id BIGINT PRIMARY KEY," +
                             "reporter_uuid VARCHAR(36) NOT NULL," +
                             "reporter_name VARCHAR(64) NOT NULL," +
                             "target_uuid VARCHAR(36)," +
                             "target_name VARCHAR(64) NOT NULL," +
                             "reason TEXT NOT NULL," +
                             "created_at BIGINT NOT NULL," +
                             "claimed_by_uuid VARCHAR(36)," +
                             "claimed_by_name VARCHAR(64)," +
                             "claimed_at BIGINT NOT NULL DEFAULT 0," +
                             "closed_by_uuid VARCHAR(36)," +
                             "closed_by_name VARCHAR(64)," +
                              "closed_at BIGINT NOT NULL DEFAULT 0" +
                              ")"
              );
              PreparedStatement cooldownStmt = con.prepareStatement(
                     "CREATE TABLE IF NOT EXISTS report_cooldowns (" +
                             "reporter_uuid VARCHAR(36) PRIMARY KEY," +
                              "last_report_at BIGINT NOT NULL" +
                              ")"
              )) {
            reportsStmt.executeUpdate();
            cooldownStmt.executeUpdate();
            ensureIndex(con, "reports", "idx_reports_open_created_at", "CREATE INDEX idx_reports_open_created_at ON reports(closed_at, created_at)");
            ensureIndex(con, "report_cooldowns", "idx_report_cooldowns_last_report_at", "CREATE INDEX idx_report_cooldowns_last_report_at ON report_cooldowns(last_report_at)");
        } catch (SQLException exception) {
            logger.severe("[Report] Impossible de creer les tables SQL : " + exception.getMessage());
        }
    }

    private List<ReportRecord> loadOpenReports(Connection con, long cutoff) throws SQLException {
        List<ReportRecord> reports = new ArrayList<>();

        try (PreparedStatement stmt = con.prepareStatement(
                "SELECT id, reporter_uuid, reporter_name, target_uuid, target_name, reason, created_at, claimed_by_uuid, claimed_by_name, claimed_at " +
                        "FROM reports WHERE closed_at = 0 AND created_at >= ?"
        )) {
            stmt.setLong(1, cutoff);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                UUID reporterUuid = parseUuid(rs.getString("reporter_uuid"));
                if (reporterUuid == null) {
                    continue;
                }

                reports.add(new ReportRecord(
                        rs.getLong("id"),
                        reporterUuid,
                        rs.getString("reporter_name"),
                        parseUuid(rs.getString("target_uuid")),
                        rs.getString("target_name"),
                        rs.getString("reason"),
                        rs.getLong("created_at"),
                        parseUuid(rs.getString("claimed_by_uuid")),
                        rs.getString("claimed_by_name"),
                        rs.getLong("claimed_at")
                ));
            }
        }

        return reports;
    }

    private Map<UUID, Long> loadCooldowns(Connection con) throws SQLException {
        Map<UUID, Long> cooldowns = new HashMap<>();

        try (PreparedStatement stmt = con.prepareStatement("SELECT reporter_uuid, last_report_at FROM report_cooldowns")) {
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                UUID uuid = parseUuid(rs.getString("reporter_uuid"));
                if (uuid == null) {
                    continue;
                }
                cooldowns.put(uuid, rs.getLong("last_report_at"));
            }
        }

        return cooldowns;
    }

    private long loadNextReportId(Connection con) throws SQLException {
        try (PreparedStatement stmt = con.prepareStatement("SELECT MAX(id) AS max_id FROM reports")) {
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getLong("max_id") + 1L;
            }
            return 1L;
        }
    }

    private void insertReport(Connection con, ReportRecord record) throws SQLException {
        try (PreparedStatement stmt = con.prepareStatement(
                "INSERT INTO reports (" +
                        "id, reporter_uuid, reporter_name, target_uuid, target_name, reason, created_at, " +
                        "claimed_by_uuid, claimed_by_name, claimed_at, closed_by_uuid, closed_by_name, closed_at" +
                        ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        )) {
            stmt.setLong(1, record.id());
            stmt.setString(2, record.reporterUuid().toString());
            stmt.setString(3, record.reporterName());
            stmt.setString(4, record.targetUuid() == null ? null : record.targetUuid().toString());
            stmt.setString(5, record.targetName());
            stmt.setString(6, record.reason());
            stmt.setLong(7, record.createdAt());
            stmt.setString(8, null);
            stmt.setString(9, null);
            stmt.setLong(10, 0L);
            stmt.setString(11, null);
            stmt.setString(12, null);
            stmt.setLong(13, 0L);
            stmt.executeUpdate();
        }
    }

    private void updateCooldown(Connection con, UUID reporterUuid, long lastReportAt) throws SQLException {
        if (databaseManager.isMySql()) {
            try (PreparedStatement stmt = con.prepareStatement(
                    "INSERT INTO report_cooldowns (reporter_uuid, last_report_at) VALUES (?, ?) " +
                            "ON DUPLICATE KEY UPDATE last_report_at = VALUES(last_report_at)"
            )) {
                stmt.setString(1, reporterUuid.toString());
                stmt.setLong(2, lastReportAt);
                stmt.executeUpdate();
            }
            return;
        }

        try (PreparedStatement stmt = con.prepareStatement(
                "INSERT INTO report_cooldowns (reporter_uuid, last_report_at) VALUES (?, ?) " +
                        "ON CONFLICT(reporter_uuid) DO UPDATE SET last_report_at = excluded.last_report_at"
        )) {
            stmt.setString(1, reporterUuid.toString());
            stmt.setLong(2, lastReportAt);
            stmt.executeUpdate();
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
                String current = indexes.getString("INDEX_NAME");
                if (current != null && current.equalsIgnoreCase(indexName)) {
                    return true;
                }
            }
            return false;
        }
    }

    private String buildClaimSql(boolean override) {
        if (override) {
            return "UPDATE reports SET claimed_by_uuid = ?, claimed_by_name = ?, claimed_at = ? WHERE id = ? AND closed_at = 0";
        }
        return "UPDATE reports SET claimed_by_uuid = ?, claimed_by_name = ?, claimed_at = ? WHERE id = ? AND closed_at = 0 AND claimed_by_uuid IS NULL";
    }

    private String buildCloseSql(boolean override) {
        if (override) {
            return "UPDATE reports SET closed_by_uuid = ?, closed_by_name = ?, closed_at = ? WHERE id = ? AND closed_at = 0";
        }
        return "UPDATE reports SET closed_by_uuid = ?, closed_by_name = ?, closed_at = ? WHERE id = ? AND closed_at = 0 AND claimed_by_uuid = ?";
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

    public record BootstrapState(List<ReportRecord> openReports, Map<UUID, Long> cooldowns, long nextReportId) {
    }
}
