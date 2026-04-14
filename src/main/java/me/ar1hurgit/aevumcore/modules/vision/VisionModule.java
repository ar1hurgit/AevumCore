package me.ar1hurgit.aevumcore.modules.vision;

import me.ar1hurgit.aevumcore.AevumCore;
import me.ar1hurgit.aevumcore.core.module.AbstractModule;
import org.bukkit.Bukkit;

public class VisionModule extends AbstractModule {

    private final AevumCore plugin;
    private VisionManager manager;

    public VisionModule(AevumCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "vision";
    }

    @Override
    protected void onEnable() {
        if (!plugin.getConfig().getBoolean("vision.enabled", true)) return;

        manager = new VisionManager(plugin);
        manager.enable();

        plugin.getServer().getPluginManager().registerEvents(new VisionListener(manager), plugin);

        if (plugin.getCommand("vision") != null) {
            VisionCommand command = new VisionCommand(plugin, manager);
            plugin.getCommand("vision").setExecutor(command);
            plugin.getCommand("vision").setTabCompleter(command);
        }

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
