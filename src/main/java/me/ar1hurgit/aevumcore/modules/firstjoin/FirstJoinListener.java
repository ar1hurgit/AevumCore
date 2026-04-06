package me.ar1hurgit.aevumcore.modules.firstjoin;

import me.ar1hurgit.aevumcore.AevumCore;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

public class FirstJoinListener implements Listener {

    private final AevumCore plugin;
    private final Economy economy;

    public FirstJoinListener(AevumCore plugin, Economy economy) {
        this.plugin = plugin;
        this.economy = economy;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {

        Player player = event.getPlayer();

        if (player.hasPlayedBefore()) return;

        if (!plugin.getConfig().getBoolean("firstjoin.enabled", true)) return;

        String mode = plugin.getConfig().getString("firstjoin.reward-type", "ITEM");
        int amount = plugin.getConfig().getInt("firstjoin.starting-money", 100);

        boolean useVault = mode.equalsIgnoreCase("VAULT");

        // ===== VAULT CHECK SAFE =====
        if (useVault && economy == null) {
            plugin.getLogger().warning("FirstJoin désactivé: Vault absent mais reward-type=VAULT");
            return;
        }

        // ===== REWARD =====
        if (useVault) {
            economy.depositPlayer(player, amount);
        } else {
            Material material = Material.matchMaterial(
                    plugin.getConfig().getString("firstjoin.starting-money-item", "EMERALD")
            );

            if (material == null) material = Material.EMERALD;

            giveItems(player, material, amount);
        }

        // ===== GLOBAL MESSAGE =====
        boolean global = plugin.getConfig().getBoolean("firstjoin.global-message", true);

        if (global) {
            String msg = color(
                    plugin.getConfig().getString(
                            "firstjoin.welcome-message",
                            "&6✦ Bienvenue &e%player% &6sur le serveur !"
                    ).replace("%player%", player.getName())
            );

            Bukkit.broadcastMessage(msg);
        }
    }

    private void giveItems(Player player, Material material, int amount) {
        int given = 0;

        while (given < amount) {
            int toGive = Math.min(material.getMaxStackSize(), amount - given);
            player.getInventory().addItem(new ItemStack(material, toGive));
            given += toGive;
        }
    }

    private String color(String msg) {
        return ChatColor.translateAlternateColorCodes('&', msg);
    }
}