package me.ar1hurgit.aevumcore.modules.vision;

import me.ar1hurgit.aevumcore.AevumCore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VisionManager {

    private final AevumCore plugin;
    private final Set<UUID> enabledPlayers = ConcurrentHashMap.newKeySet();

    private File dataFile;
    private FileConfiguration dataConfig;

    private PotionEffectType effectType;
    private int effectAmplifier;

    private BukkitTask syncTask;

    public VisionManager(AevumCore plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        loadConfiguredEffect();
        loadData();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isEnabled(player.getUniqueId())) {
                applyEffect(player);
            }
        }

        syncTask = Bukkit.getScheduler().runTaskTimer(plugin, this::syncEffects, 40L, 40L);
    }

    public void disable() {
        if (syncTask != null) {
            syncTask.cancel();
            syncTask = null;
        }

        for (UUID uuid : new ArrayList<>(enabledPlayers)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                removeEffect(player);
            }
        }

        saveData();
        enabledPlayers.clear();
    }

    public boolean isEnabled(UUID uuid) {
        return enabledPlayers.contains(uuid);
    }

    public void toggle(Player target, CommandSender actor) {
        setVision(target, !isEnabled(target.getUniqueId()), actor);
    }

    public void setVision(Player target, boolean enabled, CommandSender actor) {
        UUID uuid = target.getUniqueId();

        if (enabled) {
            enabledPlayers.add(uuid);
            applyEffect(target);
        } else {
            enabledPlayers.remove(uuid);
            removeEffect(target);
        }

        saveData();

        if (actor != null && actor != target) {
            actor.sendMessage(prefix() + ChatColor.GRAY + target.getName() + (enabled ? ChatColor.GREEN + " a maintenant la vision active." : ChatColor.YELLOW + " n'a plus la vision active."));
        }

        if (actor != null) {
            target.sendMessage(prefix() + (enabled ? ChatColor.GREEN + " Vision activee." : ChatColor.YELLOW + " Vision desactivee."));
        }
    }

    public void handleJoin(Player player) {
        if (!isEnabled(player.getUniqueId())) return;
        applyEffect(player);
    }

    public void handleRespawn(Player player) {
        if (!isEnabled(player.getUniqueId())) return;
        Bukkit.getScheduler().runTask(plugin, () -> applyEffect(player));
    }

    public void handleQuit(Player player) {
    }

    private void syncEffects() {
        for (UUID uuid : enabledPlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) continue;

            PotionEffect current = player.getPotionEffect(effectType);
            if (current == null || current.getAmplifier() != effectAmplifier || current.getDuration() < 200) {
                applyEffect(player);
            }
        }
    }

    private void applyEffect(Player player) {
        if (effectType == null) return;

        PotionEffect effect = new PotionEffect(effectType, Integer.MAX_VALUE, effectAmplifier, false, false, false);
        player.addPotionEffect(effect, true);
    }

    private void removeEffect(Player player) {
        if (effectType == null) return;
        player.removePotionEffect(effectType);
    }

    private void loadConfiguredEffect() {
        String configuredType = plugin.getConfig().getString("vision.effect-type", "NIGHT_VISION");
        PotionEffectType resolved = PotionEffectType.getByName(configuredType.toUpperCase(Locale.ROOT));

        if (resolved == null) {
            plugin.getLogger().warning("[Vision] Effet invalide '" + configuredType + "', fallback vers NIGHT_VISION.");
            resolved = PotionEffectType.NIGHT_VISION;
        }

        effectType = resolved;
        effectAmplifier = Math.max(0, plugin.getConfig().getInt("vision.effect-amplifier", 0));
    }

    private void loadData() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        dataFile = new File(plugin.getDataFolder(), "vision.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("[Vision] Impossible de creer vision.yml: " + e.getMessage());
            }
        }

        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        enabledPlayers.clear();
        List<String> rawList = dataConfig.getStringList("enabled");
        for (String raw : rawList) {
            try {
                enabledPlayers.add(UUID.fromString(raw));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void saveData() {
        if (dataConfig == null || dataFile == null) return;

        List<String> serialized = enabledPlayers.stream()
                .map(UUID::toString)
                .sorted()
                .toList();
        dataConfig.set("enabled", serialized);

        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("[Vision] Impossible de sauvegarder vision.yml: " + e.getMessage());
        }
    }

    private String prefix() {
        return ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("prefix", "&f[&bAevumCore&f]"));
    }
}
