package me.ar1hurgit.aevumcore.modules.firstjoin;

import me.ar1hurgit.aevumcore.AevumCore;
import me.ar1hurgit.aevumcore.core.module.AbstractModule;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

public class FirstJoinModule extends AbstractModule {

    private final AevumCore plugin;
    private Economy economy;

    public FirstJoinModule(AevumCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "firstjoin";
    }

    @Override
    protected void onEnable() {

        if (!plugin.getConfig().getBoolean("firstjoin.enabled", true)) return;

        // ===== Vault optional =====
        if (plugin.getServer().getPluginManager().getPlugin("Vault") != null) {
            RegisteredServiceProvider<Economy> rsp =
                    plugin.getServer().getServicesManager().getRegistration(Economy.class);

            if (rsp != null) {
                economy = rsp.getProvider();
            }
        }

        plugin.getServer().getPluginManager().registerEvents(
                new FirstJoinListener(plugin, economy),
                plugin
        );

        Bukkit.getLogger().info(
                plugin.getConfig().getString("prefix", "[AevumCore]") + " FirstJoin module enabled"
        );
    }

    @Override
    protected void onDisable() {
        Bukkit.getLogger().info(
                plugin.getConfig().getString("prefix", "[AevumCore]") + " FirstJoin module disabled"
        );
    }
}
