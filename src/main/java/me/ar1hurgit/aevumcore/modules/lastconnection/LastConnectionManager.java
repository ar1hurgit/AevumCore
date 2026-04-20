package me.ar1hurgit.aevumcore.modules.lastconnection;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LastConnectionManager {

    private final Map<UUID, Long> loginTimes = new ConcurrentHashMap<>();

    public void recordLogin(UUID uuid, long loginAt) {
        loginTimes.put(uuid, loginAt);
    }

    public SessionSnapshot recordLogout(UUID uuid, long logoutAt) {
        Long loginAt = loginTimes.remove(uuid);
        if (loginAt == null) {
            return null;
        }

        return new SessionSnapshot(uuid, loginAt, logoutAt, Math.max(0L, logoutAt - loginAt));
    }

    public long getCurrentSessionDuration(UUID uuid, long now) {
        Long loginAt = loginTimes.get(uuid);
        if (loginAt == null) {
            return 0L;
        }

        return Math.max(0L, now - loginAt);
    }

    public record SessionSnapshot(UUID uuid, long loginAt, long logoutAt, long duration) {
    }
}
