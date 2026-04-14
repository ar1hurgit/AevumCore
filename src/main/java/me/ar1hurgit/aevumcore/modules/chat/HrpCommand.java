package me.ar1hurgit.aevumcore.modules.chat;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public class HrpCommand implements CommandExecutor, TabCompleter {

    private final ChatModule module;

    public HrpCommand(ChatModule module) {
        this.module = module;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(module.prefix() + ChatColor.RED + " Cette commande est reservee aux joueurs.");
            return true;
        }

        if (!player.hasPermission("aevumcore.chat.hrp")) {
            player.sendMessage(module.prefix() + ChatColor.RED + " Vous n'avez pas la permission.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(module.prefix() + ChatColor.RED + " Usage: /hrp <mask|unmask|status>");
            return true;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        if (action.equals("mask")) {
            module.setHrpMasked(player.getUniqueId(), true);
            player.sendMessage(module.color(module.getString("message-hrp-mask-enabled", "&eMode HRP mask active. Faites /hrp unmask pour reparler.")));
            return true;
        }

        if (action.equals("unmask")) {
            module.setHrpMasked(player.getUniqueId(), false);
            player.sendMessage(module.color(module.getString("message-hrp-mask-disabled", "&aMode HRP mask desactive.")));
            return true;
        }

        if (action.equals("status")) {
            boolean masked = module.isHrpMasked(player.getUniqueId());
            if (masked) {
                player.sendMessage(module.color(module.getString("message-hrp-mask-status-on", "&eHRP mask: actif.")));
            } else {
                player.sendMessage(module.color(module.getString("message-hrp-mask-status-off", "&aHRP mask: inactif.")));
            }
            return true;
        }

        player.sendMessage(module.prefix() + ChatColor.RED + " Usage: /hrp <mask|unmask|status>");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String token = args[0].toLowerCase(Locale.ROOT);
            return Stream.of("mask", "unmask", "status")
                    .filter(value -> value.startsWith(token))
                    .toList();
        }
        return Collections.emptyList();
    }
}
