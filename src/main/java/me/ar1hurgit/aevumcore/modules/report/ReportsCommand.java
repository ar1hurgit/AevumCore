package me.ar1hurgit.aevumcore.modules.report;

import me.ar1hurgit.aevumcore.AevumCore;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

public class ReportsCommand implements CommandExecutor, TabCompleter {

    private final AevumCore plugin;
    private final ReportManager manager;

    public ReportsCommand(AevumCore plugin, ReportManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(prefix() + ChatColor.RED + " Cette commande est uniquement joueur.");
            return true;
        }

        if (!manager.canManage(player)) {
            player.sendMessage(prefix() + ChatColor.RED + " Vous n'avez pas la permission.");
            return true;
        }

        manager.openMenu(player, 0);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }

    private String prefix() {
        return ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("prefix", "&f[&bAevumCore&f]"));
    }
}
