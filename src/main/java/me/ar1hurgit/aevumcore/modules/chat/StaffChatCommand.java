package me.ar1hurgit.aevumcore.modules.chat;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Map;

public class StaffChatCommand implements CommandExecutor {

    private final ChatModule module;

    public StaffChatCommand(ChatModule module) {
        this.module = module;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("aevumcore.chat.staffchat")) {
            sender.sendMessage(module.prefix() + ChatColor.RED + " Vous n'avez pas acces au staff chat.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(module.prefix() + ChatColor.RED + " Usage: /staffchat <message>");
            return true;
        }

        String message = String.join(" ", args).trim();
        if (message.isEmpty()) {
            sender.sendMessage(module.prefix() + ChatColor.RED + " Votre message est vide.");
            return true;
        }

        String senderName = module.resolveSenderName(sender);
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("sender", senderName);
        placeholders.put("player", senderName);
        placeholders.put("message", module.applyMentions(message));

        String template = module.getString("format-staffchat", "&c[STAFF] &f{player}&8: &7{message}");
        String formatted = module.format(template, placeholders);

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission("aevumcore.chat.staffchat")) {
                online.sendMessage(formatted);
            }
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(formatted);
        }

        module.logLine("STAFF", senderName, ChatColor.stripColor(message));
        return true;
    }
}
