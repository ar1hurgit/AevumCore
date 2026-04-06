package me.ar1hurgit.aevumcore.storage.data;

import java.util.UUID;

public class PlayerData {

    private final UUID uuid;
    private String lastName;
    private long playtime;

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public long getPlaytime() {
        return playtime;
    }

    public void setPlaytime(long playtime) {
        this.playtime = playtime;
    }
}