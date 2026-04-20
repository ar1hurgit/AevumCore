package me.ar1hurgit.aevumcore.modules.report;

import me.ar1hurgit.aevumcore.AevumCore;
import me.ar1hurgit.aevumcore.core.command.CommandBindings;
import me.ar1hurgit.aevumcore.core.module.AbstractModule;
import me.ar1hurgit.aevumcore.storage.database.DatabaseManager;
import org.bukkit.Bukkit;

public class ReportModule extends AbstractModule {

    private final AevumCore plugin;
    private final DatabaseManager databaseManager;
    private ReportManager manager;

    public ReportModule(AevumCore plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    @Override
    public String getName() {
        return "report";
    }

    @Override
    protected void onEnable() {
        if (!plugin.getConfig().getBoolean("report.enabled", true)) return;

        manager = new ReportManager(plugin, databaseManager);
        manager.enable();

        ReportCommand reportCommand = new ReportCommand(plugin, manager);
        CommandBindings.bind(plugin, "report", reportCommand, reportCommand);

        ReportsCommand reportsCommand = new ReportsCommand(plugin, manager);
        CommandBindings.bind(plugin, "reports", reportsCommand, reportsCommand);

        plugin.getServer().getPluginManager().registerEvents(new ReportListener(manager), plugin);

        Bukkit.getLogger().info(plugin.getConfig().getString("prefix", "[AevumCore]") + " Report module enabled");
    }

    @Override
    protected void onDisable() {
        if (manager != null) {
            manager.disable();
        }

        Bukkit.getLogger().info(plugin.getConfig().getString("prefix", "[AevumCore]") + " Report module disabled");
    }

    public ReportManager getManager() {
        return manager;
    }
}
