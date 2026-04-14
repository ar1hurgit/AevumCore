package me.ar1hurgit.aevumcore.modules.vanish;

import me.ar1hurgit.aevumcore.AevumCore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerChatTabCompleteEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.TabCompleteEvent;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public class VanishListener implements Listener {

    private final AevumCore plugin;
    private final VanishManager manager;

    public VanishListener(AevumCore plugin, VanishManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        manager.handleJoin(player);

        if (manager.shouldHideJoinMessage(player)) {
            event.setJoinMessage(null);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        manager.handleQuit(player);

        if (manager.shouldHideJoinMessage(player)) {
            event.setQuitMessage(null);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && manager.isVanished(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (!manager.isVanished(player.getUniqueId())) return;

        if (event.getRightClicked() instanceof InventoryHolder holder) {
            manager.enterGuiSpectator(player);
            manager.markVanishSoundActivity(player, event.getRightClicked().getLocation());
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    player.openInventory(holder.getInventory());
                }
            });
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (manager.isVanished(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteractBlock(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!manager.isVanished(player.getUniqueId())) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        BlockState state = clicked.getState();
        if (!(state instanceof InventoryHolder holder)) {
            manager.markVanishSoundActivity(player, centered(clicked));
            return;
        }

        manager.enterGuiSpectator(player);
        manager.markVanishSoundActivity(player, centered(clicked));
        event.setCancelled(true);

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                player.openInventory(holder.getInventory());
            }
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityTarget(EntityTargetLivingEntityEvent event) {
        if (event.getTarget() instanceof Player player && manager.isVanished(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityTargetGeneric(EntityTargetEvent event) {
        if (event.getTarget() instanceof Player player && manager.isVanished(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player target && manager.isVanished(target.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        Entity damager = event.getDamager();
        if (damager instanceof Player attacker && manager.isVanished(attacker.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter
                && manager.isVanished(shooter.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (event.getHitEntity() instanceof Player player && manager.isVanished(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!manager.isVanished(player.getUniqueId())) return;
        if (manager.consumeSkipSpectatorNextOpen(player)) return;

        if (manager.shouldUseSpectatorFor(event.getInventory().getType())) {
            manager.enterGuiSpectator(player);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        manager.restoreGameModeAfterGui(player);
        manager.clearMenu(player.getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!manager.isMenu(event.getView().getTitle())) return;

        event.setCancelled(true);

        if (!player.hasPermission("aevumcore.vanish.teleport")) {
            player.sendMessage(prefix() + ChatColor.RED + " Vous n'avez pas la permission de teleporter via ce menu.");
            return;
        }

        if (event.getClickedInventory() == null) return;
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;

        UUID targetUuid = manager.getMenuTarget(player.getUniqueId(), event.getRawSlot());
        if (targetUuid == null) return;

        Player target = Bukkit.getPlayer(targetUuid);
        if (target == null) {
            player.sendMessage(prefix() + ChatColor.RED + " Ce joueur n'est plus en ligne.");
            return;
        }

        player.teleport(target.getLocation());
        player.sendMessage(prefix() + ChatColor.GREEN + " Teleportation vers " + ChatColor.GOLD + target.getName() + ChatColor.GREEN + ".");
    }

    @EventHandler(ignoreCancelled = true)
    public void onTabComplete(PlayerChatTabCompleteEvent event) {
        if (manager.canSeeVanished(event.getPlayer())) return;
        event.getTabCompletions().removeIf(manager::isVanishedName);
    }

    @EventHandler(ignoreCancelled = true)
    public void onServerTabComplete(TabCompleteEvent event) {
        if (!(event.getSender() instanceof Player player)) return;
        if (manager.canSeeVanished(player)) return;
        java.util.List<String> filtered = new java.util.ArrayList<>(event.getCompletions());
        filtered.removeIf(manager::isVanishedName);
        event.setCompletions(filtered);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!manager.isVanished(event.getPlayer().getUniqueId())) return;
        manager.markVanishSoundActivity(event.getPlayer(), centered(event.getBlock()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!manager.isVanished(event.getPlayer().getUniqueId())) return;
        manager.markVanishSoundActivity(event.getPlayer(), centered(event.getBlock()));
    }

    private Location centered(Block block) {
        return block.getLocation().add(0.5D, 0.5D, 0.5D);
    }

    private String prefix() {
        return ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("prefix", "&f[&bAevumCore&f]"));
    }
}
