package me.ar1hurgit.aevumcore.modules.salary;

import me.ar1hurgit.aevumcore.AevumCore;
import me.ar1hurgit.aevumcore.storage.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class PlaytimeCommand implements CommandExecutor {

    private final AevumCore plugin;

    public PlaytimeCommand(AevumCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String prefix = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("prefix", "&f[&bAevumCore&f]"));

        OfflinePlayer target;
        if (args.length > 0) {
            target = Bukkit.getOfflinePlayer(args[0]);
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage(prefix + ChatColor.RED + " Usage: /playtime <joueur>");
            return true;
        }

        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage(prefix + ChatColor.RED + " Ce joueur n'existe pas.");
            return true;
        }

        UUID uuid = target.getUniqueId();
        
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection con = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = con.prepareStatement("SELECT SUM(duration) as total FROM player_sessions WHERE uuid = ?")) {
                
                stmt.setString(1, uuid.toString());
                ResultSet rs = stmt.executeQuery();
                long totalRecorded = 0;
                if (rs.next()) {
                    totalRecorded = rs.getLong("total");
                }

                long finalTotal = totalRecorded;
                
                // If online, add current session duration
                if (target.isOnline()) {
                    me.ar1hurgit.aevumcore.modules.lastconnection.LastConnectionModule module = 
                        (me.ar1hurgit.aevumcore.modules.lastconnection.LastConnectionModule) plugin.getModuleManager().get("lastconnection");
                    
                    if (module != null && module.getListener() != null) {
                        Long loginTime = module.getListener().getLoginTimes().get(uuid);
                        if (loginTime != null) {
                            finalTotal += (System.currentTimeMillis() - loginTime);
                        }
                    }
                }

                String timeStr = formatDuration(finalTotal);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(prefix + ChatColor.GRAY + " Temps de jeu total de " + ChatColor.GOLD + target.getName() + " " + ChatColor.GRAY + ": " + ChatColor.GREEN + timeStr);
                });

            } catch (SQLException e) {
                e.printStackTrace();
                sender.sendMessage(prefix + ChatColor.RED + " Erreur base de données.");
            }
        });

        return true;
    }

    private String formatDuration(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) return days + "j " + (hours % 24) + "h " + (minutes % 60) + "min";
        if (hours > 0) return hours + "h " + (minutes % 60) + "min";
        if (minutes > 0) return minutes + "min " + (seconds % 60) + "s";
        return seconds + "s";
    }
}
