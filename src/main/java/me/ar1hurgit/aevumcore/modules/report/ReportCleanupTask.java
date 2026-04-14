package me.ar1hurgit.aevumcore.modules.report;

import org.bukkit.scheduler.BukkitRunnable;

public class ReportCleanupTask extends BukkitRunnable {

    private final ReportManager manager;

    public ReportCleanupTask(ReportManager manager) {
        this.manager = manager;
    }

    @Override
    public void run() {
        manager.cleanupExpiredReportsAsync();
    }
}
