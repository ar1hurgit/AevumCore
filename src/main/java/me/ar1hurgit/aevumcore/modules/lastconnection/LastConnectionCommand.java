package me.ar1hurgit.aevumcore.modules.lastconnection;

import me.ar1hurgit.aevumcore.AevumCore;
import me.ar1hurgit.aevumcore.storage.data.SessionData;
import me.ar1hurgit.aevumcore.storage.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class LastConnectionCommand implements CommandExecutor, TabCompleter {

    private final AevumCore plugin;
    private final DatabaseManager databaseManager;

    private final Map<UUID, Long> cacheTimestamps = new ConcurrentHashMap<>();
    private final Map<UUID, List<SessionData>> sessionCache = new ConcurrentHashMap<>();

    public LastConnectionCommand(AevumCore plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    private String color(String msg) {
        return ChatColor.translateAlternateColorCodes('&', msg);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (args.length < 1) {
            sender.sendMessage(color("&cUsage: /lastconnexion <joueur> [historique]"));
            return true;
        }

        if (sender instanceof Player player) {
            if (!player.getName().equalsIgnoreCase(args[0])
                    && !player.hasPermission("aevumcore.admin.lastconnexion")) {
                player.sendMessage(color("&cVous n'avez pas la permission."));
                return true;
            }
        }

        String targetName = args[0];
        boolean showHistory = args.length >= 2 &&
                (args[1].equalsIgnoreCase("historique") || args[1].equalsIgnoreCase("h"));

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);

        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage(color("&cCe joueur n'existe pas."));
            return true;
        }

        UUID uuid = target.getUniqueId();

        String dateFormatStr = plugin.getConfig().getString("lastconnection.date-format", "dd/MM/yyyy HH:mm");
        int cacheDuration = plugin.getConfig().getInt("lastconnection.cache-duration", 60);
        int historySize = plugin.getConfig().getInt("lastconnection.history-size", 10);
        boolean showPlaytime = plugin.getConfig().getBoolean("lastconnection.show-playtime", true);

        long now = System.currentTimeMillis();

        if (cacheTimestamps.containsKey(uuid)) {
            long cachedAt = cacheTimestamps.get(uuid);
            if ((now - cachedAt) < cacheDuration * 1000L) {
                List<SessionData> cached = sessionCache.get(uuid);
                if (cached != null) {
                    sendResult(sender, target.getName(), cached, showHistory, historySize, dateFormatStr, showPlaytime);
                    return true;
                }
            }
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection con = databaseManager.getConnection();
                 PreparedStatement stmt = con.prepareStatement(
                         "SELECT login_time, logout_time, duration FROM player_sessions WHERE uuid = ? ORDER BY logout_time DESC LIMIT ?"
                 )) {

                stmt.setString(1, uuid.toString());
                stmt.setInt(2, historySize);

                ResultSet rs = stmt.executeQuery();

                List<SessionData> sessions = new ArrayList<>();

                while (rs.next()) {
                    sessions.add(new SessionData(
                            uuid,
                            rs.getLong("login_time"),
                            rs.getLong("logout_time"),
                            rs.getLong("duration")
                    ));
                }

                cacheTimestamps.put(uuid, now);
                sessionCache.put(uuid, sessions);

                Bukkit.getScheduler().runTask(plugin, () ->
                        sendResult(sender, target.getName(), sessions, showHistory, historySize, dateFormatStr, showPlaytime)
                );

            } catch (SQLException e) {
                plugin.getLogger().severe("LastConnection error: " + e.getMessage());
                sender.sendMessage(color("&cErreur base de données."));
            }
        });

        return true;
    }

    private void sendResult(CommandSender sender, String name, List<SessionData> sessions,
                            boolean showHistory, int historySize, String dateFormatStr, boolean showPlaytime) {

        SimpleDateFormat sdf = new SimpleDateFormat(dateFormatStr);

        String prefix = color(plugin.getConfig().getString("prefix", "&f[&bAevumCore&f]"));

        if (sessions.isEmpty()) {
            sender.sendMessage(color(prefix + " &eAucune session trouvée pour &6" + name + "&e."));
            return;
        }

        if (!showHistory) {

            SessionData latest = sessions.get(0);

            String lastLogin = sdf.format(new Date(latest.getLoginTime()));
            String lastLogout = sdf.format(new Date(latest.getLogoutTime()));
            long elapsed = System.currentTimeMillis() - latest.getLogoutTime();

            sender.sendMessage(color(prefix + " &e━━━━━━━━━ &6" + name + " &e━━━━━━━━━"));
            sender.sendMessage(color("&7Dernière connexion &f: &a" + lastLogin));
            sender.sendMessage(color("&7Dernière déconnexion &f: &c" + lastLogout));
            sender.sendMessage(color("&7Temps écoulé &f: &e" + formatDuration(elapsed)));

            if (showPlaytime) {
                long total = sessions.stream().mapToLong(SessionData::getDuration).sum();
                sender.sendMessage(color("&7Temps total &f: &b" + formatDuration(total)));
            }

        } else {

            sender.sendMessage(color(prefix +
                    " &eHistorique de &6" + name +
                    " &e(&f" + sessions.size() + "&e)"));

            sender.sendMessage(color("&e━━━━━━━━━━━━━━━━━━━━━━━━"));

            for (int i = 0; i < sessions.size(); i++) {
                SessionData s = sessions.get(i);

                sender.sendMessage(color(
                        "&7#" + (i + 1) +
                                " &a" + sdf.format(new Date(s.getLoginTime())) +
                                " &f→ &c" + sdf.format(new Date(s.getLogoutTime())) +
                                " &7(" + formatDuration(s.getDuration()) + ")"
                ));
            }
        }
    }

    private String formatDuration(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) return days + "j " + (hours % 24) + "h " + (minutes % 60) + "min";
        if (hours > 0) return hours + "h " + (minutes % 60) + "min";
        if (minutes > 0) return minutes + "min " + (seconds % 60) + "s";
        return seconds + "s";
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> list = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                    list.add(p.getName());
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