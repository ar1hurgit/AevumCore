package me.ar1hurgit.aevumcore.modules.chat;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

public class SpyCommand implements CommandExecutor, TabCompleter {

    private final ChatModule module;

    public SpyCommand(ChatModule module) {
        this.module = module;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(module.prefix() + ChatColor.RED + " Cette commande est reservee aux joueurs.");
            return true;
        }

        if (!player.hasPermission("aevumcore.chat.spy")) {
            player.sendMessage(module.prefix() + ChatColor.RED + " Vous n'avez pas la permission.");
            return true;
        }

        if (args.length == 0) {
            boolean enabled = module.toggleSpy(player.getUniqueId());
            sendStateMessage(player, enabled);
            return true;
        }

        String mode = args[0].toLowerCase(Locale.ROOT);
        if (mode.equals("on")) {
            module.setSpyEnabled(player.getUniqueId(), true);
            sendStateMessage(player, true);
            return true;
        }

        if (mode.equals("off")) {
            module.setSpyEnabled(player.getUniqueId(), false);
            sendStateMessage(player, false);
            return true;
        }

        player.sendMessage(module.prefix() + ChatColor.RED + " Usage: /spy <on|off>");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String token = args[0].toLowerCase(Locale.ROOT);
            return java.util.stream.Stream.of("on", "off")
                    .filter(value -> value.startsWith(token))
                    .toList();
        }
        return java.util.Collections.emptyList();
    }

    private void sendStateMessage(Player player, boolean enabled) {
        if (enabled) {
            player.sendMessage(module.color(module.getString("message-spy-enabled", "&aSpy active: vous espionnez maintenant les MP.")));
            return;
        }
        player.sendMessage(module.color(module.getString("message-spy-disabled", "&cSpy desactive.")));
    }
}
