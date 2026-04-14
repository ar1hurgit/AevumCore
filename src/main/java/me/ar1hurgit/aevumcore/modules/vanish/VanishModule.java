package me.ar1hurgit.aevumcore.modules.vanish;

import me.ar1hurgit.aevumcore.AevumCore;
import me.ar1hurgit.aevumcore.core.module.AbstractModule;
import org.bukkit.Bukkit;

public class VanishModule extends AbstractModule {

    private final AevumCore plugin;
    private VanishManager manager;

    public VanishModule(AevumCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "vanish";
    }

    @Override
    protected void onEnable() {
        if (!plugin.getConfig().getBoolean("vanish.enabled", true)) return;

        manager = new VanishManager(plugin);
        manager.enable();

        if (plugin.getCommand("vanish") != null) {
            VanishCommand command = new VanishCommand(plugin, manager);
            plugin.getCommand("vanish").setExecutor(command);
            plugin.getCommand("vanish").setTabCompleter(command);
        }

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
