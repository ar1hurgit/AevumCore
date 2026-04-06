package me.ar1hurgit.aevumcore.storage.data;

import java.util.UUID;

public class SessionData {

    private final UUID uuid;
    private final long loginTime;
    private long logoutTime;
    private long duration;

    public SessionData(UUID uuid, long loginTime, long logoutTime, long duration) {
        this.uuid = uuid;
        this.loginTime = loginTime;
        this.logoutTime = logoutTime;
        this.duration = duration;
    }

    public UUID getUuid() { return uuid; }
    public long getLoginTime() { return loginTime; }
    public long getLogoutTime() { return logoutTime; }
    public long getDuration() { return duration; }
}
