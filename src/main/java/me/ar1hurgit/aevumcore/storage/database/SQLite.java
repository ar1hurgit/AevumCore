package me.ar1hurgit.aevumcore.storage.database;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class SQLite {

    private String url;

    public void connect(File file) {
        // Store the JDBC URL; we'll create a fresh connection per call
        this.url = "jdbc:sqlite:" + file.getAbsolutePath();

        // Eagerly verify that the driver+file work
        try (Connection test = DriverManager.getConnection(url)) {
            // connection test OK
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Always returns a brand-new connection.
     * Callers are responsible for closing it (try-with-resources).
     */
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url);
    }

    public void disconnect() {
        // Nothing to close; connections are closed by callers
    }
}