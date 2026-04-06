package me.ar1hurgit.aevumcore.modules.dice;

import me.ar1hurgit.aevumcore.AevumCore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;

public class DiceCommand implements CommandExecutor {

    private final AevumCore plugin;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Random random = new Random();

    public DiceCommand(AevumCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player player)) return true;

        if (!player.hasPermission("aevumcore.player.dice")) return true;

        int cooldown = plugin.getConfig().getInt("dice.cooldown");
        long now = System.currentTimeMillis();

        if (cooldowns.containsKey(player.getUniqueId())) {
            long last = cooldowns.get(player.getUniqueId());
            if ((now - last) < cooldown * 1000L) return true;
        }

        cooldowns.put(player.getUniqueId(), now);

        int result = random.nextInt(101);

        String selfMsg = ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("dice.message-self")
                        .replace("{result}", String.valueOf(result))
        );

        String othersMsg = ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("dice.message-others")
                        .replace("{player}", player.getName())
                        .replace("{result}", String.valueOf(result))
        );

        player.sendMessage(selfMsg);

        int radius = plugin.getConfig().getInt("dice.radius");

        Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.getWorld().equals(player.getWorld()))
                .filter(p -> p.getLocation().distance(player.getLocation()) <= radius)
                .filter(p -> p != player)
                .forEach(p -> p.sendMessage(othersMsg));

        return true;
    }
}