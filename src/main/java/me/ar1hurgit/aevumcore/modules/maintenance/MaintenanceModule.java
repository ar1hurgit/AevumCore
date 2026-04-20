package me.ar1hurgit.aevumcore.modules.maintenance;

import me.ar1hurgit.aevumcore.AevumCore;
import me.ar1hurgit.aevumcore.core.command.CommandBindings;
import me.ar1hurgit.aevumcore.core.module.AbstractModule;
import org.bukkit.Bukkit;

public class MaintenanceModule extends AbstractModule {

    private final AevumCore plugin;
    private boolean maintenanceActive;

    public MaintenanceModule(AevumCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "maintenance";
    }

    @Override
    protected void onEnable() {
        if (!plugin.getConfig().getBoolean("maintenance.enabled", true)) return;

        // Load maintenance state from config so it persists
        this.maintenanceActive = plugin.getConfig().getBoolean("maintenance.active-state", false);

        CommandBindings.bind(plugin, "maintenance", new MaintenanceCommand(plugin, this));
        plugin.getServer().getPluginManager().registerEvents(new MaintenanceListener(plugin, this), plugin);
        
        Bukkit.getLogger().info(plugin.getConfig().getString("prefix", "[AevumCore]") + " Maintenance module enabled");
    }

    @Override
    protected void onDisable() {
        Bukkit.getLogger().info(plugin.getConfig().getString("prefix", "[AevumCore]") + " Maintenance module disabled");
    }

    public boolean isMaintenanceActive() {
        return maintenanceActive;
    }

    public void setMaintenanceActive(boolean maintenanceActive) {
        this.maintenanceActive = maintenanceActive;
        // Save state to persist across restarts
        plugin.getConfig().set("maintenance.active-state", maintenanceActive);
        plugin.saveConfig();
    }
}
