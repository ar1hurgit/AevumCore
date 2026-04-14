package me.ar1hurgit.aevumcore.modules.vanish;

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
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class VanishCommand implements CommandExecutor, TabCompleter {

    private final AevumCore plugin;
    private final VanishManager manager;

    public VanishCommand(AevumCore plugin, VanishManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(prefix() + ChatColor.RED + " Usage: /v <joueur|list|menu>");
                return true;
            }
            if (!sender.hasPermission("aevumcore.vanish.use")) {
                sender.sendMessage(prefix() + ChatColor.RED + " Vous n'avez pas la permission.");
                return true;
            }
            manager.toggleVanish(player, player);
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("list")) {
            if (!sender.hasPermission("aevumcore.vanish.list")) {
                sender.sendMessage(prefix() + ChatColor.RED + " Vous n'avez pas la permission.");
                return true;
            }
            sendList(sender);
            return true;
        }

        if (sub.equals("menu")) {
            if (!(sender instanceof Player player)) {
                return true;
            }
            if (!sender.hasPermission("aevumcore.vanish.menu")) {
                sender.sendMessage(prefix() + ChatColor.RED + " Vous n'avez pas la permission.");
                return true;
            }
            manager.openMenu(player);
            return true;
        }

        if (!sender.hasPermission("aevumcore.vanish.others")) {
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

        manager.toggleVanish(target, sender);
        return true;
    }

    private void sendList(CommandSender sender) {
        Set<UUID> vanished = manager.getVanishedSnapshot();
        if (vanished.isEmpty()) {
            sender.sendMessage(prefix() + ChatColor.YELLOW + " Aucun joueur en vanish.");
            return;
        }

        List<UUID> ordered = new ArrayList<>(vanished);
        ordered.sort(Comparator.comparing(manager::getKnownName, String.CASE_INSENSITIVE_ORDER));

        sender.sendMessage(prefix() + ChatColor.GOLD + " Liste des joueurs vanish:");
        for (UUID uuid : ordered) {
            Player online = Bukkit.getPlayer(uuid);
            String status = online != null ? (ChatColor.GREEN + "online") : (ChatColor.GRAY + "offline");
            sender.sendMessage(ChatColor.DARK_GRAY + " - " + ChatColor.YELLOW + manager.getKnownName(uuid) + ChatColor.DARK_GRAY + " | " + status);
        }
    }

    private String prefix() {
        return ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("prefix", "&f[&bAevumCore&f]"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return Collections.emptyList();

        List<String> suggestions = new ArrayList<>();

        if (sender.hasPermission("aevumcore.vanish.list")) suggestions.add("list");
        if (sender.hasPermission("aevumcore.vanish.menu")) suggestions.add("menu");
        if (sender.hasPermission("aevumcore.vanish.others")) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                suggestions.add(player.getName());
            }
        }

        return suggestions;
    }
}
