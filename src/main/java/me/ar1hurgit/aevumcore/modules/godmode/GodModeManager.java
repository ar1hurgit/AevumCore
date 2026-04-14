package me.ar1hurgit.aevumcore.modules.godmode;

import me.ar1hurgit.aevumcore.AevumCore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GodModeManager {

    private final AevumCore plugin;
    private final Set<UUID> godPlayers = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<UUID, Integer> disableTasks = new ConcurrentHashMap<>();

    public GodModeManager(AevumCore plugin) {
        this.plugin = plugin;
    }

    public boolean isGod(UUID uuid) {
        return godPlayers.contains(uuid);
    }

    public void toggle(Player target, CommandSender actor) {
        setGodMode(target, !isGod(target.getUniqueId()), actor, false);
    }

    public void setGodMode(Player target, boolean enabled, CommandSender actor, boolean automatic) {
        UUID uuid = target.getUniqueId();

        if (enabled) {
            godPlayers.add(uuid);
            target.setFireTicks(0);
            scheduleAutoDisable(target);
        } else {
            godPlayers.remove(uuid);
            cancelAutoDisable(uuid);
        }

        String prefix = prefix();
        if (automatic) {
            target.sendMessage(prefix + ChatColor.YELLOW + " Votre god mode a ete desactive automatiquement.");
            notifyStaff(prefix + ChatColor.GOLD + target.getName() + ChatColor.YELLOW + " a eu son god mode desactive automatiquement.", target);
            return;
        }

        if (actor != null && actor != target) {
            actor.sendMessage(prefix + ChatColor.GRAY + target.getName() + (enabled ? ChatColor.GREEN + " a maintenant le god mode." : ChatColor.YELLOW + " n'a plus le god mode."));
        }
        target.sendMessage(prefix + (enabled ? ChatColor.GREEN + " God mode active." : ChatColor.YELLOW + " God mode desactive."));

        if (plugin.getConfig().getBoolean("godmode.notify-others", true)) {
            String state = enabled ? ChatColor.GREEN + "active" : ChatColor.YELLOW + "desactive";
            notifyStaff(prefix + ChatColor.AQUA + target.getName() + ChatColor.GRAY + " a " + state + ChatColor.GRAY + " son god mode.", target);
        }
    }

    public void handleQuit(Player player) {
        cancelAutoDisable(player.getUniqueId());
    }

    public void disableAll() {
        for (UUID uuid : disableTasks.keySet()) {
            cancelAutoDisable(uuid);
        }
        godPlayers.clear();
    }

    private void scheduleAutoDisable(Player player) {
        cancelAutoDisable(player.getUniqueId());

        int delaySeconds = plugin.getConfig().getInt("godmode.auto-disable-delay", 0);
        if (delaySeconds <= 0) return;

        int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || !isGod(player.getUniqueId())) return;
            setGodMode(player, false, null, true);
        }, delaySeconds * 20L).getTaskId();

        disableTasks.put(player.getUniqueId(), taskId);
    }

    private void cancelAutoDisable(UUID uuid) {
        Integer taskId = disableTasks.remove(uuid);
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
    }

    private void notifyStaff(String message, Player source) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (source != null && online.getUniqueId().equals(source.getUniqueId())) continue;
            if (online.hasPermission("aevumcore.godmode.notify") || online.hasPermission("aevumcore.admin.*") || online.isOp()) {
                online.sendMessage(message);
            }
        }
    }

    private String prefix() {
        return ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("prefix", "&f[&bAevumCore&f]"));
    }
}
