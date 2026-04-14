package me.ar1hurgit.aevumcore.modules.antiafk;

import me.ar1hurgit.aevumcore.AevumCore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class AntiAFKTask extends BukkitRunnable {

    private final AevumCore plugin;
    private final AntiAFKModule module;

    public AntiAFKTask(AevumCore plugin, AntiAFKModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    @Override
    public void run() {
        if (!plugin.getConfig().getBoolean("antiafk.enabled", true)) return;

        int timeoutSeconds = Math.max(1, plugin.getConfig().getInt("antiafk.timeout-seconds", 300));
        long timeoutMillis = timeoutSeconds * 1000L;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("aevumcore.antiafk.bypass")) continue;
            if (module.isAfk(player.getUniqueId())) continue;

            if (module.getInactiveMillis(player.getUniqueId()) >= timeoutMillis) {
                module.handleTimeout(player);
            }
        }
    }
}
