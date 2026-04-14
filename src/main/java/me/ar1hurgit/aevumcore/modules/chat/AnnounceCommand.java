package me.ar1hurgit.aevumcore.modules.chat;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AnnounceCommand implements CommandExecutor, TabCompleter {

    private final ChatModule module;

    public AnnounceCommand(ChatModule module) {
        this.module = module;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String loweredLabel = label.toLowerCase(Locale.ROOT);

        if (loweredLabel.equals("anrp")) {
            return handleRpAnnouncement(sender, joinArgs(args, 0));
        }

        if (loweredLabel.equals("anhrp")) {
            return handleHrpAnnouncement(sender, joinArgs(args, 0));
        }

        if (args.length == 0) {
            sendAnnounceUsage(sender);
            return true;
        }

        String mode = args[0].toLowerCase(Locale.ROOT);
        switch (mode) {
            case "rp":
                return handleRpAnnouncement(sender, joinArgs(args, 1));
            case "hrp":
                return handleHrpAnnouncement(sender, joinArgs(args, 1));
            case "royaume":
                return handleRoyalAnnouncement(sender, args);
            default:
                sendAnnounceUsage(sender);
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String loweredCommand = command.getName().toLowerCase(Locale.ROOT);
        if (!loweredCommand.equals("annonce")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return filterByToken(args[0], List.of("rp", "hrp", "royaume"));
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("royaume")) {
            return filterByToken(args[1], List.of("decret", "notif"));
        }

        return Collections.emptyList();
    }

    private boolean handleRpAnnouncement(CommandSender sender, String message) {
        if (!sender.hasPermission("aevumcore.chat.announce.rp")) {
            sender.sendMessage(module.prefix() + ChatColor.RED + " Vous n'avez pas la permission d'envoyer une annonce RP.");
            return true;
        }
        if (message == null || message.isBlank()) {
            sender.sendMessage(module.prefix() + ChatColor.RED + " Usage: /annonce rp <message>");
            return true;
        }

        String senderName = module.resolveSenderName(sender);
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("sender", senderName);
        placeholders.put("player", senderName);
        placeholders.put("message", message);

        List<String> lines = module.getFormatLines("format-annonce-rp", "&6[Annonce RP] &f{message}");
        module.broadcastAnnouncementLines(lines, placeholders);

        String title = module.format(module.getString("annonce-rp-titre", "&6Annonce RP"), placeholders);
        String subtitle = module.format(module.getString("annonce-rp-sous-titre", "&f{message}"), placeholders);
        int fadeIn = Math.max(0, module.getInt("annonce-rp-title-fade-in", 10));
        int stay = Math.max(0, module.getInt("annonce-rp-title-stay", 70));
        int fadeOut = Math.max(0, module.getInt("annonce-rp-title-fade-out", 20));

        Sound sound = module.getRpAnnouncementSound();
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
            online.playSound(online.getLocation(), sound, 1.0F, 1.0F);
        }

        module.logLine("ANNONCE_RP", senderName, ChatColor.stripColor(message));
        return true;
    }

    private boolean handleHrpAnnouncement(CommandSender sender, String message) {
        if (!sender.hasPermission("aevumcore.chat.announce.hrp")) {
            sender.sendMessage(module.prefix() + ChatColor.RED + " Vous n'avez pas la permission d'envoyer une annonce HRP.");
            return true;
        }
        if (message == null || message.isBlank()) {
            sender.sendMessage(module.prefix() + ChatColor.RED + " Usage: /annonce hrp <message>");
            return true;
        }

        String senderName = module.resolveSenderName(sender);
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("sender", senderName);
        placeholders.put("player", senderName);
        placeholders.put("message", message);

        List<String> lines = module.getFormatLines("format-annonce-hrp", "&7[Annonce HRP] &f{message}");
        module.broadcastAnnouncementLines(lines, placeholders);
        module.logLine("ANNONCE_HRP", senderName, ChatColor.stripColor(message));
        return true;
    }

    private boolean handleRoyalAnnouncement(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(module.prefix() + ChatColor.RED + " Usage: /annonce royaume <decret|notif> <message>");
            return true;
        }

        String subMode = args[1].toLowerCase(Locale.ROOT);
        String message = joinArgs(args, 2);

        if (message == null || message.isBlank()) {
            sender.sendMessage(module.prefix() + ChatColor.RED + " Le message est requis.");
            return true;
        }

        if (subMode.equals("decret")) {
            if (!sender.hasPermission("aevumcore.chat.announce.royaume.decret")) {
                sender.sendMessage(module.prefix() + ChatColor.RED + " Vous n'avez pas la permission d'envoyer un decret.");
                return true;
            }

            int recipients = module.sendRoyalDecree(sender, message);
            sender.sendMessage(module.prefix() + ChatColor.GREEN + " Decret envoye a " + ChatColor.GOLD + recipients + ChatColor.GREEN + " joueurs.");
            return true;
        }

        if (subMode.equals("notif")) {
            if (!sender.hasPermission("aevumcore.chat.announce.royaume.notif")) {
                sender.sendMessage(module.prefix() + ChatColor.RED + " Vous n'avez pas la permission d'envoyer une notification royale.");
                return true;
            }

            long remaining = module.consumeRoyalNotifCooldown(sender);
            if (remaining > 0L) {
                long seconds = (remaining + 999L) / 1000L;
                sender.sendMessage(module.prefix() + ChatColor.RED + " Cooldown actif: " + ChatColor.GOLD + seconds + "s");
                return true;
            }

            String senderName = module.resolveSenderName(sender);
            Map<String, String> placeholders = new LinkedHashMap<>();
            placeholders.put("sender", senderName);
            placeholders.put("player", senderName);
            placeholders.put("message", message);

            List<String> lines = module.getFormatLines("format-annonce-royaume-notif", "&e[Notification Royale] &f{message}");
            module.broadcastAnnouncementLines(lines, placeholders);
            module.logLine("ROYAL_NOTIF", senderName, ChatColor.stripColor(message));
            return true;
        }

        sender.sendMessage(module.prefix() + ChatColor.RED + " Sous-commande royaume inconnue. Utilisez decret ou notif.");
        return true;
    }

    private List<String> filterByToken(String token, List<String> values) {
        String loweredToken = token == null ? "" : token.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(loweredToken))
                .toList();
    }

    private String joinArgs(String[] args, int startIndex) {
        if (args.length <= startIndex) {
            return "";
        }
        return String.join(" ", Arrays.copyOfRange(args, startIndex, args.length)).trim();
    }

    private void sendAnnounceUsage(CommandSender sender) {
        sender.sendMessage(module.prefix() + ChatColor.RED + " Usage: /annonce <rp|hrp|royaume> ...");
    }
}
