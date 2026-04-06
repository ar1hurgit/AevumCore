package me.ar1hurgit.aevumcore.modules.dice;

import me.ar1hurgit.aevumcore.AevumCore;
import me.ar1hurgit.aevumcore.core.module.AbstractModule;
import org.bukkit.Bukkit;

public class DiceModule extends AbstractModule {

    private final AevumCore plugin;

    public DiceModule(AevumCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "dice";
    }

    @Override
    protected void onEnable() {
        if (!plugin.getConfig().getBoolean("dice.enabled")) return;

        plugin.getCommand("des").setExecutor(new DiceCommand(plugin));
        plugin.getCommand("des").setTabCompleter(new DiceTab());

        Bukkit.getLogger().info(plugin.getConfig().getString("prefix") + " Dice module enabled");
    }

    @Override
    protected void onDisable() {
        Bukkit.getLogger().info(plugin.getConfig().getString("prefix") + " Dice module disabled");
    }
}