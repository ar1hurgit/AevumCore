package me.ar1hurgit.aevumcore.modules.vision;

import me.ar1hurgit.aevumcore.AevumCore;
import me.ar1hurgit.aevumcore.core.command.CommandBindings;
import me.ar1hurgit.aevumcore.core.module.AbstractModule;
import me.ar1hurgit.aevumcore.storage.database.DatabaseManager;
import org.bukkit.Bukkit;

public class VisionModule extends AbstractModule {

    private final AevumCore plugin;
    private final DatabaseManager databaseManager;
    private VisionManager manager;

    public VisionModule(AevumCore plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    @Override
    public String getName() {
        return "vision";
    }

    @Override
    protected void onEnable() {
        if (!plugin.getConfig().getBoolean("vision.enabled", true)) return;

        manager = new VisionManager(plugin, databaseManager);
        manager.enable();

        plugin.getServer().getPluginManager().registerEvents(new VisionListener(manager), plugin);

        VisionCommand command = new VisionCommand(plugin, manager);
        CommandBindings.bind(plugin, "vision", command, command);

        Bukkit.getLogger().info(plugin.getConfig().getString("prefix", "[AevumCore]") + " Vision module enabled");
    }

    @Override
    protected void onDisable() {
        if (manager != null) {
            manager.disable();
        }

        Bukkit.getLogger().info(plugin.getConfig().getString("prefix", "[AevumCore]") + " Vision module disabled");
    }
}
