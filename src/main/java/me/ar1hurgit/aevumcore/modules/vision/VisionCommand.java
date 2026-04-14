package me.ar1hurgit.aevumcore.modules.vision;

import me.ar1hurgit.aevumcore.AevumCore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class VisionCommand implements CommandExecutor, TabCompleter {

    private final AevumCore plugin;
    private final VisionManager manager;

    public VisionCommand(AevumCore plugin, VisionManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(prefix() + ChatColor.RED + " Usage: /vision <joueur>");
                return true;
            }
            if (!sender.hasPermission("aevumcore.vision.use")) {
                sender.sendMessage(prefix() + ChatColor.RED + " Vous n'avez pas la permission.");
                return true;
            }

            manager.toggle(player, player);
            return true;
        }

        if (!sender.hasPermission("aevumcore.vision.others")) {
            sender.sendMessage(prefix() + ChatColor.RED + " Vous n'avez pas la permission.");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            target = Bukkit.getPlayer(args[0]);
        }

        if (target == null) {
            sender.sendMessage(prefix() + ChatColor.RED + " Joueur introuvable ou hors-ligne.");
            return true;
        }

        manager.toggle(target, sender);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return Collections.emptyList();
        if (!sender.hasPermission("aevumcore.vision.others")) return Collections.emptyList();

        List<String> players = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            players.add(online.getName());
        }
        return players;
    }

    private String prefix() {
        return ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("prefix", "&f[&bAevumCore&f]"));
    }
}
