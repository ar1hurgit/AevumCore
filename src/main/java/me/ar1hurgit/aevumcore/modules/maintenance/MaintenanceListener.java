package me.ar1hurgit.aevumcore.modules.maintenance;

import me.ar1hurgit.aevumcore.AevumCore;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;

import java.util.List;

public class MaintenanceListener implements Listener {

    private final AevumCore plugin;
    private final MaintenanceModule module;

    public MaintenanceListener(AevumCore plugin, MaintenanceModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerLogin(PlayerLoginEvent event) {
        if (!module.isMaintenanceActive()) {
            return;
        }

        Player player = event.getPlayer();
        
        // Always allow OPs
        if (player.isOp()) {
            return;
        }

        // Check for general bypass permission
        if (player.hasPermission("aevumcore.maintenance.bypass")) {
            return;
        }

        // Check for allowed groups from config
        List<String> allowedGroups = plugin.getConfig().getStringList("maintenance.allowed-groups");
        for (String group : allowedGroups) {
            // Check via Bukkit permissions (common standard when Vault isn't present)
            if (player.hasPermission("group." + group)) {
                return;
            }
        }

        // Player is not allowed, kick them
        String rawMessage = plugin.getConfig().getString("maintenance.message", "&cLe serveur est actuellement en maintenance.");
        String message = ChatColor.translateAlternateColorCodes('&', rawMessage);
        
        event.disallow(PlayerLoginEvent.Result.KICK_OTHER, message);
    }
}
