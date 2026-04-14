package me.ar1hurgit.aevumcore.modules.report;

import me.ar1hurgit.aevumcore.AevumCore;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ReportCommand implements CommandExecutor, TabCompleter {

    private final AevumCore plugin;
    private final ReportManager manager;

    public ReportCommand(AevumCore plugin, ReportManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (shouldHandleCloseSubcommand(sender, args)) {
            long reportId;
            try {
                reportId = Long.parseLong(args[1]);
            } catch (NumberFormatException exception) {
                sender.sendMessage(prefix() + ChatColor.RED + " L'identifiant du report est invalide.");
                return true;
            }

            manager.closeReport(sender, reportId);
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(prefix() + ChatColor.RED + " Cette commande est uniquement joueur.");
            return true;
        }

        if (!sender.hasPermission(ReportManager.PLAYER_PERMISSION)) {
            sender.sendMessage(prefix() + ChatColor.RED + " Vous n'avez pas la permission.");
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(prefix() + ChatColor.RED + " Usage: /report <joueur> <raison>");
            return true;
        }

        ReportManager.ResolvedTarget target = manager.resolveTarget(player, args[0]);
        if (target == null) {
            player.sendMessage(prefix() + ChatColor.RED + " Joueur introuvable.");
            return true;
        }

        if (target.uuid() != null && target.uuid().equals(player.getUniqueId())) {
            player.sendMessage(prefix() + ChatColor.RED + " Vous ne pouvez pas vous report vous-meme.");
            return true;
        }

        String reason = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        manager.submitReport(player, target, reason);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();

            if (manager.canManage(sender) && "close".startsWith(args[0].toLowerCase())) {
                suggestions.add("close");
            }

            if (sender instanceof Player player && sender.hasPermission(ReportManager.PLAYER_PERMISSION)) {
                suggestions.addAll(manager.getTargetSuggestions(player, args[0]));
            }

            suggestions.sort(String.CASE_INSENSITIVE_ORDER);
            return suggestions;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("close") && manager.canManage(sender)) {
            return manager.getOpenReportIdSuggestions(args[1]);
        }

        return Collections.emptyList();
    }

    private boolean shouldHandleCloseSubcommand(CommandSender sender, String[] args) {
        return args.length >= 2
                && args[0].equalsIgnoreCase("close")
                && isNumeric(args[1])
                && manager.canManage(sender);
    }

    private boolean isNumeric(String input) {
        if (input == null || input.isBlank()) return false;

        for (int index = 0; index < input.length(); index++) {
            if (!Character.isDigit(input.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    private String prefix() {
        return ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("prefix", "&f[&bAevumCore&f]"));
    }
}
