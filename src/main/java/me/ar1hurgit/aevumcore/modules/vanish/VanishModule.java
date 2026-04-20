package me.ar1hurgit.aevumcore.modules.vanish;

import me.ar1hurgit.aevumcore.AevumCore;
import me.ar1hurgit.aevumcore.core.command.CommandBindings;
import me.ar1hurgit.aevumcore.core.module.AbstractModule;
import me.ar1hurgit.aevumcore.storage.database.DatabaseManager;
import org.bukkit.Bukkit;

public class VanishModule extends AbstractModule {

    private final AevumCore plugin;
    private final DatabaseManager databaseManager;
    private VanishManager manager;

    public VanishModule(AevumCore plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    @Override
    public String getName() {
        return "vanish";
    }

    @Override
    protected void onEnable() {
        if (!plugin.getConfig().getBoolean("vanish.enabled", true)) return;

        manager = new VanishManager(plugin, databaseManager);
        manager.enable();

        VanishCommand command = new VanishCommand(plugin, manager);
        CommandBindings.bind(plugin, "vanish", command, command);

        plugin.getServer().getPluginManager().registerEvents(new VanishListener(plugin, manager), plugin);

        Bukkit.getLogger().info(plugin.getConfig().getString("prefix", "[AevumCore]") + " Vanish module enabled");
    }

    @Override
    protected void onDisable() {
        if (manager != null) {
            manager.disable();
        }
        Bukkit.getLogger().info(plugin.getConfig().getString("prefix", "[AevumCore]") + " Vanish module disabled");
    }

    public VanishManager getManager() {
        return manager;
    }
}
