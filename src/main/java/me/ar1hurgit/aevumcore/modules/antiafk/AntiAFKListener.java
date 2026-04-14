package me.ar1hurgit.aevumcore.modules.antiafk;

import me.ar1hurgit.aevumcore.AevumCore;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class AntiAFKListener implements Listener {

    private final AevumCore plugin;
    private final AntiAFKModule module;

    public AntiAFKListener(AevumCore plugin, AntiAFKModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        module.trackJoin(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        module.trackQuit(event.getPlayer());
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        module.markActivity(event.getPlayer());
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        module.markActivity(event.getPlayer());
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        plugin.getServer().getScheduler().runTask(plugin, () -> module.markActivity(event.getPlayer()));
    }
}
