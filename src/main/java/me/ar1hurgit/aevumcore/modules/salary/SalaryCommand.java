package me.ar1hurgit.aevumcore.modules.salary;

import me.ar1hurgit.aevumcore.AevumCore;
import me.ar1hurgit.aevumcore.core.text.DurationFormatter;
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
        if (!module.isReady()) {
            sender.sendMessage(prefix + ChatColor.RED + " Le module salary se charge encore.");
            return true;
        }

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
                sender.sendMessage(prefix + ChatColor.GREEN + " Le grade " + ChatColor.GOLD + grade + ChatColor.GREEN + " a ete supprime.");
                return true;
            } else if (args[0].equalsIgnoreCase("list")) {
                sender.sendMessage(prefix + ChatColor.YELLOW + " Liste des salaires par grade :");
                if (module.getSalariesConfig().getConfigurationSection("salaries") != null) {
                    for (String key : module.getSalariesConfig().getConfigurationSection("salaries").getKeys(false)) {
                        int amount = module.getSalariesConfig().getInt("salaries." + key);
                        sender.sendMessage(ChatColor.GRAY + " - " + ChatColor.GOLD + key + ChatColor.WHITE + " : " + ChatColor.GREEN + amount);
                    }
                }
                return true;
            }
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(prefix + ChatColor.RED + " Les sous-commandes admin sont: /salary add|remove|list");
            return true;
        }

        int salary = getSalary(player);
        String rank = getRankName(player);

        player.sendMessage(ChatColor.translateAlternateColorCodes('&', prefix + " &e--------- &6Informations Salaire &e---------"));
        player.sendMessage(ChatColor.GRAY + " Grade detecte   : " + ChatColor.GOLD + rank);
        player.sendMessage(ChatColor.GRAY + " Montant salaire : " + ChatColor.GREEN + salary);

        checkTimeRemaining(player);
        return true;
    }

    private int getSalary(Player player) {
        if (module.getSalariesConfig().getConfigurationSection("salaries") == null) return 0;

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
        if (module.getSalariesConfig().getConfigurationSection("salaries") == null) return "default";

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

    private void checkTimeRemaining(Player player) {
        String prefix = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("prefix", "&f[&bAevumCore&f]"));
        long intervalMillis = module.getIntervalMillis();

        module.fetchProgressAsync(player.getUniqueId(), progress -> {
            if (progress == null) {
                player.sendMessage(prefix + ChatColor.RED + " Impossible de lire votre progression salaire.");
                return;
            }

            long remaining = Math.max(0L, intervalMillis - progress);
            boolean afk = module.isPlayerAfk(player);

            if (remaining <= 0) {
                player.sendMessage(ChatColor.GRAY + " Prochain versement : " + ChatColor.GREEN + "Imminent...");
            } else {
                String suffix = afk ? ChatColor.RED + " (en pause - AFK)" : "";
                player.sendMessage(ChatColor.GRAY + " Prochain versement : " + ChatColor.YELLOW + formatDuration(remaining) + suffix);
            }

            if (afk) {
                player.sendMessage(prefix + ChatColor.YELLOW + " Votre cooldown salaire est en pause tant que vous etes AFK.");
            }
        });
    }

    private String formatDuration(long millis) {
        return DurationFormatter.formatCompact(millis);
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
            if (module.getSalariesConfig().getConfigurationSection("salaries") == null) return Collections.emptyList();
            return new ArrayList<>(module.getSalariesConfig().getConfigurationSection("salaries").getKeys(false));
        }
        return Collections.emptyList();
    }
}
