package me.ar1hurgit.aevumcore.modules.salary;

import me.ar1hurgit.aevumcore.AevumCore;
import me.ar1hurgit.aevumcore.storage.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
        if (!plugin.getConfig().getBoolean("salary.pay-offline", true)) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        long intervalMillis = plugin.getConfig().getInt("salary.interval", 60) * 60L * 1000L;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection con = plugin.getDatabaseManager().getConnection()) {
                long lastPayout = 0;

                try (PreparedStatement stmt = con.prepareStatement("SELECT last_salary FROM player_data WHERE uuid = ?")) {
                    stmt.setString(1, uuid.toString());
                    ResultSet rs = stmt.executeQuery();
                    if (rs.next()) {
                        lastPayout = rs.getLong("last_salary");
                    } else {
                        // First join or missing data
                        try (PreparedStatement insert = con.prepareStatement("INSERT INTO player_data (uuid, last_salary) VALUES (?, ?)")) {
                            insert.setString(1, uuid.toString());
                            insert.setLong(2, now);
                            insert.executeUpdate();
                        }
                        return;
                    }
                }

                if ((now - lastPayout) >= intervalMillis) {
                    final long lastPayoutFinal = lastPayout;
                    long cycles = (now - lastPayout) / intervalMillis;
                    int salaryPerCycle = getSalary(player);
                    int totalToPay = (int) (cycles * salaryPerCycle);

                    if (totalToPay > 0) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            deliverSalary(player, totalToPay, (int) cycles);
                            
                            // Update last_salary
                            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                                try (Connection conUpdate = plugin.getDatabaseManager().getConnection();
                                     PreparedStatement update = conUpdate.prepareStatement("UPDATE player_data SET last_salary = ? WHERE uuid = ?")) {
                                    // Set it to now (or exact interval marks?)
                                    // Better exact interval: lastPayout + (cycles * intervalMillis)
                                    update.setLong(1, lastPayoutFinal + (cycles * intervalMillis));
                                    update.setString(2, uuid.toString());
                                    update.executeUpdate();
                                } catch (SQLException e) {
                                    e.printStackTrace();
                                }
                            });
                        });
                    }
                }

            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    private int getSalary(Player player) {
        int highest = 0;
        for (String rank : module.getSalariesConfig().getConfigurationSection("salaries").getKeys(false)) {
            if (player.hasPermission("aevumcore.salary.rank." + rank)) {
                int amount = module.getSalariesConfig().getInt("salaries." + rank);
                if (amount > highest) highest = amount;
            }
        }
        if (highest == 0) highest = module.getSalariesConfig().getInt("salaries.default", 0);
        return highest;
    }

    private void deliverSalary(Player player, int amount, int cycles) {
        if (amount <= 0) return;
        String mode = plugin.getConfig().getString("salary.reward-type", "ITEM");
        
        if (mode.equalsIgnoreCase("VAULT") && module.getEconomy() != null) {
            module.getEconomy().depositPlayer(player, amount);
        } else {
            Material material = Material.matchMaterial(plugin.getConfig().getString("salary.starting-money-item", "EMERALD"));
            if (material == null) material = Material.EMERALD;

            int given = 0;
            while (given < amount) {
                int toGive = Math.min(material.getMaxStackSize(), amount - given);
                if (toGive <= 0) break;
                ItemStack item = new ItemStack(material, toGive);
                player.getInventory().addItem(item).values().forEach(remaining -> {
                    player.getWorld().dropItemNaturally(player.getLocation(), remaining);
                });
                given += toGive;
            }
        }

        String prefix = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("prefix", "&f[&bAevumCore&f]"));
        player.sendMessage(prefix + ChatColor.YELLOW + " Durant votre absence, vous avez accumulé " + 
            ChatColor.GOLD + cycles + " salaires " + ChatColor.YELLOW + "soit un total de " + 
            ChatColor.GOLD + amount + ChatColor.YELLOW + " !");
    }
}
