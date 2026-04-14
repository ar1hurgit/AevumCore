package me.ar1hurgit.aevumcore.modules.godmode;

import me.ar1hurgit.aevumcore.AevumCore;
import me.ar1hurgit.aevumcore.core.module.AbstractModule;
import org.bukkit.Bukkit;

public class GodModeModule extends AbstractModule {

    private final AevumCore plugin;
    private GodModeManager manager;

    public GodModeModule(AevumCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "godmode";
    }

    @Override
    protected void onEnable() {
        if (!plugin.getConfig().getBoolean("godmode.enabled", true)) return;

        manager = new GodModeManager(plugin);
        plugin.getServer().getPluginManager().registerEvents(new GodModeListener(manager), plugin);

        if (plugin.getCommand("godmode") != null) {
            GodModeCommand command = new GodModeCommand(plugin, manager);
            plugin.getCommand("godmode").setExecutor(command);
            plugin.getCommand("godmode").setTabCompleter(command);
        }

        Bukkit.getLogger().info(plugin.getConfig().getString("prefix", "[AevumCore]") + " GodMode module enabled");
    }

    @Override
    protected void onDisable() {
        if (manager != null) {
            manager.disableAll();
        }

        Bukkit.getLogger().info(plugin.getConfig().getString("prefix", "[AevumCore]") + " GodMode module disabled");
    }
}
