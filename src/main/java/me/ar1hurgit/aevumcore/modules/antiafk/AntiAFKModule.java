package me.ar1hurgit.aevumcore.modules.antiafk;

import me.ar1hurgit.aevumcore.AevumCore;
import me.ar1hurgit.aevumcore.core.command.CommandBindings;
import me.ar1hurgit.aevumcore.core.module.AbstractModule;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AntiAFKModule extends AbstractModule {

    private final AevumCore plugin;
    private final Map<UUID, Long> lastActivity = new ConcurrentHashMap<>();
    private final Set<UUID> afkPlayers = ConcurrentHashMap.newKeySet();
    private BukkitTask task;

    public AntiAFKModule(AevumCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "antiafk";
    }

    @Override
    protected void onEnable() {
        if (!plugin.getConfig().getBoolean("antiafk.enabled", true)) return;

        AntiAFKCommand command = new AntiAFKCommand(plugin, this);
        CommandBindings.bind(plugin, "antiafk", command, command);

        plugin.getServer().getPluginManager().registerEvents(new AntiAFKListener(plugin, this), plugin);
        task = new AntiAFKTask(plugin, this).runTaskTimer(plugin, 20L, 20L);

        for (Player player : Bukkit.getOnlinePlayers()) {
            markActivity(player);
        }

        Bukkit.getLogger().info(plugin.getConfig().getString("prefix", "[AevumCore]") + " AntiAFK module enabled");
    }

    @Override
    protected void onDisable() {
        if (task != null) {
            task.cancel();
        }
        lastActivity.clear();
        afkPlayers.clear();
        Bukkit.getLogger().info(plugin.getConfig().getString("prefix", "[AevumCore]") + " AntiAFK module disabled");
    }

    public void markActivity(Player player) {
        UUID uuid = player.getUniqueId();
        lastActivity.put(uuid, System.currentTimeMillis());

        if (afkPlayers.remove(uuid)) {
            String message = plugin.getConfig().getString("antiafk.return-message", "&aVous n'etes plus AFK.");
            player.sendMessage(colorize(message));
        }
    }

    public void trackJoin(Player player) {
        markActivity(player);
        afkPlayers.remove(player.getUniqueId());
    }

    public void trackQuit(Player player) {
        UUID uuid = player.getUniqueId();
        lastActivity.remove(uuid);
        afkPlayers.remove(uuid);
    }

    public boolean isAfk(UUID uuid) {
        return afkPlayers.contains(uuid);
    }

    public void setAfk(Player player, boolean afk, boolean sendStatusMessage) {
        UUID uuid = player.getUniqueId();
        if (afk) {
            afkPlayers.add(uuid);
            if (sendStatusMessage) {
                player.sendMessage(colorize("&eVous etes maintenant AFK."));
            }
            return;
        }

        boolean changed = afkPlayers.remove(uuid);
        lastActivity.put(uuid, System.currentTimeMillis());
        if (changed && sendStatusMessage) {
            String message = plugin.getConfig().getString("antiafk.return-message", "&aVous n'etes plus AFK.");
            player.sendMessage(colorize(message));
        }
    }

    public long getInactiveMillis(UUID uuid) {
        Long last = lastActivity.get(uuid);
        if (last == null) return 0L;
        return Math.max(0L, System.currentTimeMillis() - last);
    }

    public void handleTimeout(Player player) {
        if (isAfk(player.getUniqueId())) return;

        setAfk(player, true, false);
        String message = colorize(plugin.getConfig().getString("antiafk.afk-message", "&eVous etes maintenant AFK."));
        String action = plugin.getConfig().getString("antiafk.action", "message");

        if ("kick".equalsIgnoreCase(action)) {
            player.kickPlayer(message);
        } else {
            player.sendMessage(message);
        }
    }

    private String colorize(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
