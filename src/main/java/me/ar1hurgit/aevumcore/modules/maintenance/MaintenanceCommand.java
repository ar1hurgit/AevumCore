package me.ar1hurgit.aevumcore.modules.maintenance;

import me.ar1hurgit.aevumcore.AevumCore;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class MaintenanceCommand implements CommandExecutor {

    private final AevumCore plugin;
    private final MaintenanceModule module;

    public MaintenanceCommand(AevumCore plugin, MaintenanceModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("aevumcore.admin.maintenance")) {
            sender.sendMessage(ChatColor.RED + "Vous n'avez pas la permission.");
            return true;
        }

        boolean newState = !module.isMaintenanceActive();
        module.setMaintenanceActive(newState);

        String prefix = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("prefix", "&f[&bAevumCore&f]"));
        String status = newState ? "&cActivée" : "&aDésactivée";
        String message = prefix + " &eLa maintenance est maintenant " + status + "&e.";

        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', message));

        // Notifier le staff en ligne
        String notification = prefix + " &c[Alerte Staff] &eLa maintenance a été " + status + " &epar &6" + sender.getName() + "&e.";
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (p.hasPermission("aevumcore.admin.maintenance") && p != sender) {
                p.sendMessage(ChatColor.translateAlternateColorCodes('&', notification));
            }
        }

        return true;
    }
}
