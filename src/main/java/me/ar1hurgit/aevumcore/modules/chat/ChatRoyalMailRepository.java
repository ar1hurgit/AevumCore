package me.ar1hurgit.aevumcore.modules.chat;

import me.ar1hurgit.aevumcore.storage.database.DatabaseManager;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

public class ChatRoyalMailRepository {

    private final DatabaseManager databaseManager;
    private final Logger logger;

    public ChatRoyalMailRepository(DatabaseManager databaseManager, Logger logger) {
        this.databaseManager = databaseManager;
        this.logger = logger;
    }

    public void initializeSchema() {
        try (Connection con = databaseManager.getConnection()) {
            createMailboxTable(con);
        } catch (SQLException exception) {
            logger.severe("[Chat] Impossible de creer la table chat_royal_mailbox : " + exception.getMessage());
        }
    }

    public void storeRoyalDecree(String senderName, String content, long createdAt, Set<UUID> recipients, Set<UUID> deliveredRecipients) {
        try (Connection con = databaseManager.getConnection()) {
            createMailboxTable(con);
            try (PreparedStatement stmt = con.prepareStatement(
                    "INSERT INTO chat_royal_mailbox (mail_id, player_uuid, sender_name, mail_type, content, created_at, delivered) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?)"
            )) {
                for (UUID recipient : recipients) {
                    stmt.setString(1, UUID.randomUUID().toString());
                    stmt.setString(2, recipient.toString());
                    stmt.setString(3, senderName);
                    stmt.setString(4, "decret");
                    stmt.setString(5, content);
                    stmt.setLong(6, createdAt);
                    stmt.setInt(7, deliveredRecipients.contains(recipient) ? 1 : 0);
                    stmt.addBatch();
                }
                stmt.executeBatch();
            }
        } catch (SQLException exception) {
            logger.severe("[Chat] Impossible d'enregistrer le decret royal : " + exception.getMessage());
        }
    }

    public List<RoyalMailEntry> loadPendingRoyalDecrees(UUID playerUuid) {
        List<RoyalMailEntry> pending = new ArrayList<>();

        try (Connection con = databaseManager.getConnection()) {
            createMailboxTable(con);
            try (PreparedStatement stmt = con.prepareStatement(
                    "SELECT mail_id, sender_name, content, created_at " +
                            "FROM chat_royal_mailbox " +
                            "WHERE player_uuid = ? AND mail_type = 'decret' AND delivered = 0 " +
                            "ORDER BY created_at ASC"
            )) {
                stmt.setString(1, playerUuid.toString());
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    pending.add(new RoyalMailEntry(
                            rs.getString("mail_id"),
                            rs.getString("sender_name"),
                            rs.getString("content"),
                            rs.getLong("created_at")
                    ));
                }
            }
        } catch (SQLException exception) {
            logger.severe("[Chat] Impossible de lire la boite aux lettres royale : " + exception.getMessage());
        }

        return pending;
    }

    public void markDelivered(List<String> mailIds) {
        if (mailIds == null || mailIds.isEmpty()) {
            return;
        }

        try (Connection con = databaseManager.getConnection()) {
            createMailboxTable(con);
            try (PreparedStatement stmt = con.prepareStatement(
                    "UPDATE chat_royal_mailbox SET delivered = 1 WHERE mail_id = ?"
            )) {
                for (String id : mailIds) {
                    stmt.setString(1, id);
                    stmt.addBatch();
                }
                stmt.executeBatch();
            }
        } catch (SQLException exception) {
            logger.severe("[Chat] Impossible de marquer les lettres royales comme lues : " + exception.getMessage());
        }
    }

    private void createMailboxTable(Connection con) throws SQLException {
        try (PreparedStatement stmt = con.prepareStatement(
                "CREATE TABLE IF NOT EXISTS chat_royal_mailbox (" +
                        "mail_id VARCHAR(36) PRIMARY KEY," +
                        "player_uuid VARCHAR(36) NOT NULL," +
                        "sender_name VARCHAR(64) NOT NULL," +
                        "mail_type VARCHAR(16) NOT NULL," +
                        "content TEXT NOT NULL," +
                        "created_at BIGINT NOT NULL," +
                        "delivered INTEGER NOT NULL DEFAULT 0" +
                        ")"
        )) {
            stmt.executeUpdate();
        }

        ensureIndex(con, "chat_royal_mailbox", "idx_chat_royal_mailbox_pending", "CREATE INDEX idx_chat_royal_mailbox_pending ON chat_royal_mailbox(player_uuid, delivered, created_at)");
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

    public record RoyalMailEntry(String mailId, String senderName, String content, long createdAt) {
    }
}
