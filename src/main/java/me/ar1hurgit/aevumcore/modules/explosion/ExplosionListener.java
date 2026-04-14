package me.ar1hurgit.aevumcore.modules.explosion;

import me.ar1hurgit.aevumcore.AevumCore;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.block.BlockExplodeEvent;

public class ExplosionListener implements Listener {

    private final AevumCore plugin;
    private final ExplosionModule module;

    public ExplosionListener(AevumCore plugin, ExplosionModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!module.isWorldAllowed(event.getLocation().getWorld())) return;
        if (!module.cancelBlockDamage()) return;

        event.blockList().clear();
        event.setYield(0F);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (!module.isWorldAllowed(event.getBlock().getWorld())) return;
        if (!module.cancelBlockDamage()) return;

        event.blockList().clear();
        event.setYield(0F);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplosionDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!module.cancelPlayerDamage()) return;
        if (!module.isWorldAllowed(player.getWorld())) return;

        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
                || cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
            event.setCancelled(true);
        }
    }
}
