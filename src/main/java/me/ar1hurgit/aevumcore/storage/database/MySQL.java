package me.ar1hurgit.aevumcore.storage.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

public class MySQL {

    private HikariDataSource dataSource;

    public void connect(String host, int port, String database, String username, String password, int poolSize) {
        HikariConfig config = new HikariConfig();

        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&autoReconnect=true");
        config.setUsername(username);
        config.setPassword(password);

        config.setMaximumPoolSize(poolSize);
        config.setPoolName("AevumCore-MySQL");

        dataSource = new HikariDataSource(config);
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void disconnect() {
        if (dataSource != null) dataSource.close();
    }
}