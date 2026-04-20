package me.ar1hurgit.aevumcore.modules.lastconnection;

import me.ar1hurgit.aevumcore.AevumCore;
import me.ar1hurgit.aevumcore.core.text.DurationFormatter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.Locale;

public class LastConnectionCommand implements CommandExecutor, TabCompleter {

    private final AevumCore plugin;
    private final LastConnectionModule module;

    public LastConnectionCommand(AevumCore plugin, LastConnectionModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    private String color(String msg) {
        return ChatColor.translateAlternateColorCodes('&', msg);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!module.isReady()) {
            sender.sendMessage(color("&cLe module lastconnection se charge encore."));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(color("&cUsage: /lastconnexion <joueur> [historique]"));
            return true;
        }

        if (sender instanceof Player player
                && !player.getName().equalsIgnoreCase(args[0])
                && !player.hasPermission("aevumcore.admin.lastconnexion")) {
            player.sendMessage(color("&cVous n'avez pas la permission."));
            return true;
        }

        String targetName = args[0];
        boolean showHistory = args.length >= 2
                && (args[1].equalsIgnoreCase("historique") || args[1].equalsIgnoreCase("h"));

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage(color("&cCe joueur n'existe pas."));
            return true;
        }

        UUID uuid = target.getUniqueId();
        String dateFormatStr = plugin.getConfig().getString("lastconnection.date-format", "dd/MM/yyyy HH:mm");
        int historySize = plugin.getConfig().getInt("lastconnection.history-size", 10);
        boolean showPlaytime = plugin.getConfig().getBoolean("lastconnection.show-playtime", true);

        module.fetchPlayerStatsAsync(uuid, historySize, stats -> {
            if (stats == null) {
                sender.sendMessage(color("&cErreur base de donnees."));
                return;
            }

            sendResult(sender, target.getName(), uuid, stats, showHistory, dateFormatStr, showPlaytime);
        });

        return true;
    }

    private void sendResult(CommandSender sender, String name, UUID uuid, LastConnectionRepository.PlayerStats stats,
                            boolean showHistory, String dateFormatStr, boolean showPlaytime) {
        List<me.ar1hurgit.aevumcore.storage.data.SessionData> sessions = stats.recentSessions();
        DateTimeFormatter formatter = createFormatter(dateFormatStr);
        String prefix = color(plugin.getConfig().getString("prefix", "&f[&bAevumCore&f]"));

        if (sessions.isEmpty()) {
            sender.sendMessage(color(prefix + " &eAucune session trouvee pour &6" + name + "&e."));
            return;
        }

        if (!showHistory) {
            me.ar1hurgit.aevumcore.storage.data.SessionData latest = sessions.get(0);
            String lastLogin = formatter.format(Instant.ofEpochMilli(latest.getLoginTime()));
            String lastLogout = formatter.format(Instant.ofEpochMilli(latest.getLogoutTime()));
            long elapsed = System.currentTimeMillis() - latest.getLogoutTime();

            sender.sendMessage(color(prefix + " &e--------- &6" + name + " &e---------"));
            sender.sendMessage(color("&7Derniere connexion &f: &a" + lastLogin));
            sender.sendMessage(color("&7Derniere deconnexion &f: &c" + lastLogout));
            sender.sendMessage(color("&7Temps ecoule &f: &e" + formatDuration(elapsed)));

            if (showPlaytime) {
                long total = stats.totalPlaytime() + module.getCurrentSessionDuration(uuid, System.currentTimeMillis());
                sender.sendMessage(color("&7Temps total &f: &b" + formatDuration(total)));
            }
            return;
        }

        sender.sendMessage(color(prefix + " &eHistorique de &6" + name + " &e(&f" + sessions.size() + "&e)"));
        sender.sendMessage(color("&e------------------------"));

        for (int index = 0; index < sessions.size(); index++) {
            me.ar1hurgit.aevumcore.storage.data.SessionData session = sessions.get(index);
            sender.sendMessage(color(
                    "&7#" + (index + 1)
                            + " &a" + formatter.format(Instant.ofEpochMilli(session.getLoginTime()))
                            + " &f-> &c" + formatter.format(Instant.ofEpochMilli(session.getLogoutTime()))
                            + " &7(" + formatDuration(session.getDuration()) + ")"
            ));
        }
    }

    private DateTimeFormatter createFormatter(String pattern) {
        try {
            return DateTimeFormatter.ofPattern(pattern, Locale.ROOT).withZone(ZoneId.systemDefault());
        } catch (IllegalArgumentException exception) {
            return DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.ROOT).withZone(ZoneId.systemDefault());
        }
    }

    private String formatDuration(long millis) {
        return DurationFormatter.formatCompact(millis);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> list = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                    list.add(player.getName());
                }
            }
            return list;
        }

        if (args.length == 2) {
            return Collections.singletonList("historique");
        }

        return Collections.emptyList();
    }
}
