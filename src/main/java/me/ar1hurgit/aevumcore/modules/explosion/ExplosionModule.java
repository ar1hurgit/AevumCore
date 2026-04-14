package me.ar1hurgit.aevumcore.modules.explosion;

import me.ar1hurgit.aevumcore.AevumCore;
import me.ar1hurgit.aevumcore.core.module.AbstractModule;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.List;

public class ExplosionModule extends AbstractModule {

    private final AevumCore plugin;

    public ExplosionModule(AevumCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "explosion";
    }

    @Override
    protected void onEnable() {
        if (!plugin.getConfig().getBoolean("explosion.enabled", true)) return;

        plugin.getServer().getPluginManager().registerEvents(new ExplosionListener(plugin, this), plugin);
        Bukkit.getLogger().info(plugin.getConfig().getString("prefix", "[AevumCore]") + " Explosion module enabled");
    }

    @Override
    protected void onDisable() {
        Bukkit.getLogger().info(plugin.getConfig().getString("prefix", "[AevumCore]") + " Explosion module disabled");
    }

    public boolean cancelBlockDamage() {
        return plugin.getConfig().getBoolean("explosion.cancel-block-damage", true);
    }

    public boolean cancelPlayerDamage() {
        return plugin.getConfig().getBoolean("explosion.cancel-player-damage", false);
    }

    public boolean isWorldAllowed(World world) {
        List<String> worlds = plugin.getConfig().getStringList("explosion.allowed-worlds");
        if (worlds == null || worlds.isEmpty()) {
            return true;
        }

        String worldName = world.getName();
        for (String allowed : worlds) {
            if (allowed != null && allowed.equalsIgnoreCase(worldName)) {
                return true;
            }
        }
        return false;
    }
}
