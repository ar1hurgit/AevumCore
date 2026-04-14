package me.ar1hurgit.aevumcore.modules.chat;

import me.ar1hurgit.aevumcore.AevumCore;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class ChatListener implements Listener {

    private final AevumCore plugin;
    private final ChatModule module;

    public ChatListener(AevumCore plugin, ChatModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAsyncChat(AsyncPlayerChatEvent event) {
        event.setCancelled(true);

        Bukkit.getScheduler().runTask(plugin, () ->
                module.handlePublicChat(event.getPlayer(), event.getMessage()));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        if (event.getJoinMessage() != null) {
            event.setJoinMessage(module.buildJoinMessage(event.getPlayer()));
        }
        module.deliverPendingRoyalDecrees(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        if (event.getQuitMessage() != null) {
            event.setQuitMessage(module.buildQuitMessage(event.getPlayer()));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        String deathMessage = event.getDeathMessage();
        if (deathMessage == null || deathMessage.isBlank()) {
            return;
        }

        event.setDeathMessage(module.rewriteMessageWithDisplayNames(deathMessage));
    }
}
