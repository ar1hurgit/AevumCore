package me.ar1hurgit.aevumcore.storage.database;

public enum DatabaseType {
    MYSQL,
    SQLITE;

    public static DatabaseType fromConfig(String rawValue) {
        if (rawValue == null) {
            return SQLITE;
        }

        try {
            return DatabaseType.valueOf(rawValue.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return SQLITE;
        }
    }
}
