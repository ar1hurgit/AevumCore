package me.ar1hurgit.aevumcore.modules.salary;

import me.ar1hurgit.aevumcore.AevumCore;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public class SalaryListener implements Listener {

    private final AevumCore plugin;
    private final SalaryModule module;

    public SalaryListener(AevumCore plugin, SalaryModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!plugin.getConfig().getBoolean("salary.enabled", true)) return;
        if (!module.isReady()) return;
        module.loadPlayerProgress(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!plugin.getConfig().getBoolean("salary.enabled", true)) return;
        if (!module.isReady()) return;

        UUID uuid = event.getPlayer().getUniqueId();
        module.savePlayerProgressAsync(uuid);
        module.unloadPlayer(uuid);
    }
}
