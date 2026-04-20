package me.ar1hurgit.aevumcore.modules.salary;

import me.ar1hurgit.aevumcore.AevumCore;
import me.ar1hurgit.aevumcore.core.text.DurationFormatter;
import me.ar1hurgit.aevumcore.modules.lastconnection.LastConnectionModule;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class PlaytimeCommand implements CommandExecutor {

    private final AevumCore plugin;
    private final SalaryModule salaryModule;

    public PlaytimeCommand(AevumCore plugin, SalaryModule salaryModule) {
        this.plugin = plugin;
        this.salaryModule = salaryModule;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String prefix = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("prefix", "&f[&bAevumCore&f]"));
        if (!salaryModule.isReady()) {
            sender.sendMessage(prefix + ChatColor.RED + " Le module salary se charge encore.");
            return true;
        }

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

        LastConnectionModule lastConnectionModule = (LastConnectionModule) plugin.getModuleManager().get("lastconnection");
        if (lastConnectionModule == null || !lastConnectionModule.isEnabled() || !lastConnectionModule.isReady()) {
            sender.sendMessage(prefix + ChatColor.RED + " Le module lastconnection se charge encore.");
            return true;
        }

        UUID uuid = target.getUniqueId();
        lastConnectionModule.fetchTotalPlaytimeAsync(uuid, totalPlaytime -> {
            if (totalPlaytime == null) {
                sender.sendMessage(prefix + ChatColor.RED + " Erreur base de donnees.");
                return;
            }

            sender.sendMessage(prefix + ChatColor.GRAY + " Temps de jeu total de " + ChatColor.GOLD + target.getName() + " " + ChatColor.GRAY + ": " + ChatColor.GREEN + formatDuration(totalPlaytime));
        });

        return true;
    }

    private String formatDuration(long millis) {
        return DurationFormatter.formatCompact(millis);
    }
}
