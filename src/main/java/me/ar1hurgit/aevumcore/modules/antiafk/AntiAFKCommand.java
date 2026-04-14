package me.ar1hurgit.aevumcore.modules.antiafk;

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

public class AntiAFKCommand implements CommandExecutor, TabCompleter {

    private final AevumCore plugin;
    private final AntiAFKModule module;

    public AntiAFKCommand(AevumCore plugin, AntiAFKModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String prefix = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("prefix", "&f[&bAevumCore&f]"));

        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(prefix + ChatColor.RED + " Usage: /antiafk <joueur>");
                return true;
            }

            boolean newState = !module.isAfk(player.getUniqueId());
            module.setAfk(player, newState, false);
            if (newState) {
                player.sendMessage(prefix + ChatColor.YELLOW + " Vous etes maintenant AFK.");
            } else {
                player.sendMessage(prefix + ChatColor.GREEN + " Vous n'etes plus AFK.");
            }
            return true;
        }

        if (!sender.hasPermission("aevumcore.admin.antiafk")) {
            sender.sendMessage(prefix + ChatColor.RED + " Vous n'avez pas la permission.");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            target = Bukkit.getPlayer(args[0]);
        }

        if (target == null) {
            sender.sendMessage(prefix + ChatColor.RED + " Joueur introuvable ou hors-ligne.");
            return true;
        }

        if (module.isAfk(target.getUniqueId())) {
            sender.sendMessage(prefix + ChatColor.YELLOW + target.getName() + " est deja AFK.");
            return true;
        }

        module.setAfk(target, true, false);
        sender.sendMessage(prefix + ChatColor.GREEN + " " + target.getName() + " est maintenant AFK.");
        target.sendMessage(prefix + ChatColor.YELLOW + " Un membre du staff vous a passe en AFK.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("aevumcore.admin.antiafk")) return Collections.emptyList();
        if (args.length != 1) return Collections.emptyList();

        List<String> players = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            players.add(player.getName());
        }
        return players;
    }
}
