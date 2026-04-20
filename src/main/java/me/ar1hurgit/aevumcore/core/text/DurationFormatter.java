package me.ar1hurgit.aevumcore.core.text;

public final class DurationFormatter {

    private DurationFormatter() {
    }

    public static String formatCompact(long millis) {
        long safeMillis = Math.max(0L, millis);
        long seconds = safeMillis / 1000L;
        long minutes = seconds / 60L;
        long hours = minutes / 60L;
        long days = hours / 24L;

        if (days > 0L) {
            return days + "j " + (hours % 24L) + "h " + (minutes % 60L) + "min";
        }
        if (hours > 0L) {
            return hours + "h " + (minutes % 60L) + "min";
        }
        if (minutes > 0L) {
            return minutes + "min " + (seconds % 60L) + "s";
        }
        return seconds + "s";
    }
}
