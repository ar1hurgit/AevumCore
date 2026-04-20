package me.ar1hurgit.aevumcore.modules.report;

public final class ReportCooldownPolicy {

    private ReportCooldownPolicy() {
    }

    public static long getRemainingCooldownMillis(long lastReportAt, long now, long cooldownSeconds) {
        long cooldownMillis = Math.max(0L, cooldownSeconds) * 1000L;
        if (cooldownMillis <= 0L) {
            return 0L;
        }

        long elapsed = Math.max(0L, now - lastReportAt);
        long remaining = cooldownMillis - elapsed;
        return Math.max(0L, remaining);
    }
}
