package me.ar1hurgit.aevumcore.modules.nickname;

public final class NicknameCooldownPolicy {

    private NicknameCooldownPolicy() {
    }

    public static long getRemainingCooldownMillis(long lastChangeAt, long now, int cooldownSeconds) {
        long cooldownMillis = Math.max(0, cooldownSeconds) * 1000L;
        if (cooldownMillis <= 0L) {
            return 0L;
        }

        long elapsed = Math.max(0L, now - lastChangeAt);
        long remaining = cooldownMillis - elapsed;
        return Math.max(0L, remaining);
    }
}
