package me.ar1hurgit.aevumcore.modules.lastconnection;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public class LastConnectionListener implements Listener {

    private final LastConnectionModule module;
    private final LastConnectionManager manager;

    public LastConnectionListener(LastConnectionModule module, LastConnectionManager manager) {
        this.module = module;
        this.manager = manager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        manager.recordLogin(uuid, System.currentTimeMillis());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        long logoutAt = System.currentTimeMillis();
        LastConnectionManager.SessionSnapshot snapshot = manager.recordLogout(uuid, logoutAt);
        if (snapshot == null) {
            return;
        }

        module.persistSessionAsync(snapshot);
    }
}
