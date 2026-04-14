package me.ar1hurgit.aevumcore.modules.nickname;

import me.ar1hurgit.aevumcore.AevumCore;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

public class NicknameCommand implements CommandExecutor, TabCompleter {

    private final AevumCore plugin;
    private final NicknameManager manager;

    public NicknameCommand(AevumCore plugin, NicknameManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String commandName = command.getName().toLowerCase();

        if (commandName.equals("nom")) {
            if (!(sender instanceof Player player)) {
                return true;
            }
            if (!sender.hasPermission("aevumcore.nickname.use")) {
                sender.sendMessage(prefix() + ChatColor.RED + " Vous n'avez pas la permission.");
                return true;
            }
            if (args.length != 1) {
                sender.sendMessage(prefix() + ChatColor.RED + " Usage: /nom <pseudo>");
                return true;
            }

            manager.setNickname(player, args[0]);
            return true;
        }

        if (commandName.equals("realname")) {
            if (!sender.hasPermission("aevumcore.nickname.realname")) {
                sender.sendMessage(prefix() + ChatColor.RED + " Vous n'avez pas la permission.");
                return true;
            }
            if (args.length != 1) {
                sender.sendMessage(prefix() + ChatColor.RED + " Usage: /realname <pseudo>");
                return true;
            }

            String pseudo = args[0];
            manager.findRealNameByDisplayed(pseudo, realName -> {
                if (realName == null) {
                    sender.sendMessage(prefix() + ChatColor.RED + " Aucun joueur trouve avec ce pseudo affiche.");
                    return;
                }
                sender.sendMessage(prefix() + ChatColor.GOLD + pseudo + ChatColor.GRAY + " -> " + ChatColor.AQUA + realName);
            });
            return true;
        }

        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String commandName = command.getName().toLowerCase();
        if (!commandName.equals("realname")) return Collections.emptyList();
        if (!sender.hasPermission("aevumcore.nickname.realname")) return Collections.emptyList();
        if (args.length != 1) return Collections.emptyList();

        return manager.getKnownDisplayedNames();
    }

    private String prefix() {
        return ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("prefix", "&f[&bAevumCore&f]"));
    }
}
