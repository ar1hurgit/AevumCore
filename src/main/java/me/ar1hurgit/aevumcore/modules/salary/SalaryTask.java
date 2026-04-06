package me.ar1hurgit.aevumcore.modules.salary;

import me.ar1hurgit.aevumcore.AevumCore;
import me.ar1hurgit.aevumcore.storage.database.DatabaseManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class SalaryTask extends BukkitRunnable {

    private final AevumCore plugin;
    private final SalaryModule module;

    public SalaryTask(AevumCore plugin, SalaryModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    @Override
    public void run() {
        if (!plugin.getConfig().getBoolean("salary.enabled", true)) return;

        long now = System.currentTimeMillis();
        long intervalMillis = plugin.getConfig().getInt("salary.interval", 60) * 60L * 1000L;

        for (Player player : Bukkit.getOnlinePlayers()) {
            checkAndPay(player, now, intervalMillis);
        }
    }

    private void checkAndPay(Player player, long now, long intervalMillis) {
        UUID uuid = player.getUniqueId();

        // Get last payout from DB asynchrously? No, Task is already on a scheduler.
        // But runTaskTimer is on main thread. Better do DB async then payout on main thread.
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection con = plugin.getDatabaseManager().getConnection()) {
                long lastPayout = 0;
                
                try (PreparedStatement stmt = con.prepareStatement("SELECT last_salary FROM player_data WHERE uuid = ?")) {
                    stmt.setString(1, uuid.toString());
                    ResultSet rs = stmt.executeQuery();
                    if (rs.next()) {
                        lastPayout = rs.getLong("last_salary");
                    } else {
                        // First time, initialize it to now (so they don't get 1000 salaries at once if pay-offline is true)
                        // Actually, if they just joined, FirstJoin handled them. 
                        // So we set it to now to start the countdown.
                        try (PreparedStatement insert = con.prepareStatement("INSERT INTO player_data (uuid, last_salary) VALUES (?, ?)")) {
                            insert.setString(1, uuid.toString());
                            insert.setLong(2, now);
                            insert.executeUpdate();
                        }
                        return;
                    }
                }

                if ((now - lastPayout) >= intervalMillis) {
                    // It's time to pay!
                    int salary = getSalary(player);
                    if (salary <= 0) return;

                    // Sync back to main thread for reward delivery
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        deliverSalary(player, salary);
                        
                        // Update last_salary in DB
                        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                            try (Connection conUpdate = plugin.getDatabaseManager().getConnection();
                                 PreparedStatement update = conUpdate.prepareStatement("UPDATE player_data SET last_salary = ? WHERE uuid = ?")) {
                                update.setLong(1, now);
                                update.setString(2, uuid.toString());
                                update.executeUpdate();
                            } catch (SQLException e) {
                                e.printStackTrace();
                            }
                        });
                    });
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
        // Fallback to default if no other perm found
        if (highest == 0) {
            highest = module.getSalariesConfig().getInt("salaries.default", 0);
        }
        return highest;
    }

    private void deliverSalary(Player player, int amount) {
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
                
                // Add to inventory, drop if full
                player.getInventory().addItem(item).values().forEach(remaining -> {
                    player.getWorld().dropItemNaturally(player.getLocation(), remaining);
                });
                given += toGive;
            }
        }

        String prefix = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("prefix", "&f[&bAevumCore&f]"));
        player.sendMessage(prefix + ChatColor.GREEN + " Vous avez reçu votre salaire de " + ChatColor.GOLD + amount + ChatColor.GREEN + " !");
    }
}
