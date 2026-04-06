package me.ar1hurgit.aevumcore.modules.salary;

import me.ar1hurgit.aevumcore.AevumCore;
import me.ar1hurgit.aevumcore.storage.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SalaryCommand implements CommandExecutor, TabCompleter {

    private final AevumCore plugin;
    private final SalaryModule module;

    public SalaryCommand(AevumCore plugin, SalaryModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String prefix = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("prefix", "&f[&bAevumCore&f]"));

        if (args.length > 0 && sender.hasPermission("aevumcore.admin.salary")) {
            if (args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("set")) {
                if (args.length < 3) {
                    sender.sendMessage(prefix + ChatColor.RED + " Usage: /salary add <grade> <montant>");
                    return true;
                }
                String grade = args[1].toLowerCase();
                try {
                    int amount = Integer.parseInt(args[2]);
                    module.getSalariesConfig().set("salaries." + grade, amount);
                    module.saveSalariesConfig();
                    sender.sendMessage(prefix + ChatColor.GREEN + " Le grade " + ChatColor.GOLD + grade + ChatColor.GREEN + " a maintenant un salaire de " + ChatColor.GOLD + amount + ChatColor.GREEN + ".");
                } catch (NumberFormatException e) {
                    sender.sendMessage(prefix + ChatColor.RED + " Montant invalide.");
                }
                return true;
            } else if (args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("delete")) {
                if (args.length < 2) {
                    sender.sendMessage(prefix + ChatColor.RED + " Usage: /salary remove <grade>");
                    return true;
                }
                String grade = args[1].toLowerCase();
                module.getSalariesConfig().set("salaries." + grade, null);
                module.saveSalariesConfig();
                sender.sendMessage(prefix + ChatColor.GREEN + " Le grade " + ChatColor.GOLD + grade + ChatColor.GREEN + " a été supprimé.");
                return true;
            } else if (args[0].equalsIgnoreCase("list")) {
                sender.sendMessage(prefix + ChatColor.YELLOW + " Liste des salaires par grade :");
                for (String key : module.getSalariesConfig().getConfigurationSection("salaries").getKeys(false)) {
                    int amount = module.getSalariesConfig().getInt("salaries." + key);
                    sender.sendMessage(ChatColor.GRAY + " - " + ChatColor.GOLD + key + ChatColor.WHITE + " : " + ChatColor.GREEN + amount);
                }
                return true;
            }
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(prefix + ChatColor.RED + " Les sous-commandes admin sont: /salary add|remove|list");
            return true;
        }

        // Show player salary info
        int salary = getSalary(player);
        String rank = getRankName(player);
        
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', prefix + " &e━━━━━━━━━ &6Informations Salaire &e━━━━━━━━━"));
        player.sendMessage(ChatColor.GRAY + " Grade détecté  : " + ChatColor.GOLD + rank);
        player.sendMessage(ChatColor.GRAY + " Montant salaire : " + ChatColor.GREEN + salary);
        
        // Time remaining until next payout
        checkTimeRemaining(player, prefix);

        return true;
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

    private String getRankName(Player player) {
        String highestRank = "default";
        int highestAmount = -1;
        for (String rank : module.getSalariesConfig().getConfigurationSection("salaries").getKeys(false)) {
            if (player.hasPermission("aevumcore.salary.rank." + rank)) {
                int amount = module.getSalariesConfig().getInt("salaries." + rank);
                if (amount > highestAmount) {
                    highestAmount = amount;
                    highestRank = rank;
                }
            }
        }
        return highestRank;
    }

    private void checkTimeRemaining(Player player, String prefix) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection con = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = con.prepareStatement("SELECT last_salary FROM player_data WHERE uuid = ?")) {
                stmt.setString(1, player.getUniqueId().toString());
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    long lastPayout = rs.getLong("last_salary");
                    long intervalMillis = plugin.getConfig().getInt("salary.interval", 60) * 60L * 1000L;
                    long nextPayout = lastPayout + intervalMillis;
                    long remaining = nextPayout - System.currentTimeMillis();
                    
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (remaining <= 0) {
                            player.sendMessage(ChatColor.GRAY + " Prochain versement : " + ChatColor.GREEN + "Immiment...");
                        } else {
                            player.sendMessage(ChatColor.GRAY + " Prochain versement : " + ChatColor.YELLOW + formatDuration(remaining));
                        }
                    });
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    private String formatDuration(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        if (hours > 0) return hours + "h " + (minutes % 60) + "min";
        if (minutes > 0) return minutes + "min " + (seconds % 60) + "s";
        return seconds + "s";
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("aevumcore.admin.salary")) return Collections.emptyList();
        
        if (args.length == 1) {
            List<String> sub = new ArrayList<>();
            sub.add("add");
            sub.add("remove");
            sub.add("set");
            sub.add("list");
            return sub;
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("add"))) {
            return new ArrayList<>(module.getSalariesConfig().getConfigurationSection("salaries").getKeys(false));
        }
        return Collections.emptyList();
    }
}
