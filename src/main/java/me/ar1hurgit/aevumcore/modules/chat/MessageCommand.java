package me.ar1hurgit.aevumcore.modules.chat;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MessageCommand implements CommandExecutor, TabCompleter {

    private final ChatModule module;

    public MessageCommand(ChatModule module) {
        this.module = module;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("aevumcore.chat.msg")) {
            sender.sendMessage(module.prefix() + ChatColor.RED + " Vous n'avez pas la permission.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(module.prefix() + ChatColor.RED + " Usage: /msg <joueur> <message>");
            return true;
        }

        Player target = module.resolveOnlineTarget(sender, args[0]);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(module.prefix() + ChatColor.RED + " Joueur introuvable.");
            return true;
        }

        if (sender instanceof Player player && player.getUniqueId().equals(target.getUniqueId())) {
            sender.sendMessage(module.prefix() + ChatColor.RED + " Vous ne pouvez pas vous envoyer un message prive.");
            return true;
        }

        String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
        if (message.isEmpty()) {
            sender.sendMessage(module.prefix() + ChatColor.RED + " Votre message est vide.");
            return true;
        }

        String senderName = module.resolveSenderName(sender);
        String targetName = module.getDisplayName(target);
        String highlighted = module.applyMentions(message);

        String defaultTemplate = "&d[MP] {sender} &8-> &d{target}&8: &f{message}";
        String sendTemplate = module.getString("format-msg-prive-send",
                module.getString("format-msg-prive", defaultTemplate));
        String receiveTemplate = module.getString("format-msg-prive-receive",
                module.getString("format-msg-prive", defaultTemplate));

        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("sender", senderName);
        placeholders.put("target", targetName);
        placeholders.put("message", highlighted);

        sender.sendMessage(module.format(sendTemplate, placeholders));
        target.sendMessage(module.format(receiveTemplate, placeholders));
        module.broadcastSpyPrivateMessage(sender, target, message);

        module.logLine("MSG", senderName + " -> " + targetName, ChatColor.stripColor(message));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("aevumcore.chat.msg")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return module.getOnlineNameSuggestions(sender, args[0]);
        }

        return Collections.emptyList();
    }
}
