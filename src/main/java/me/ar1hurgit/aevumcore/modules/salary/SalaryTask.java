package me.ar1hurgit.aevumcore.modules.salary;

import me.ar1hurgit.aevumcore.AevumCore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class SalaryTask extends BukkitRunnable {

    private final AevumCore plugin;
    private final SalaryModule module;
    private final Map<UUID, Long> lastTick = new HashMap<>();
    private long lastPeriodicSave = 0L;

    public SalaryTask(AevumCore plugin, SalaryModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    @Override
    public void run() {
        if (!plugin.getConfig().getBoolean("salary.enabled", true)) return;

        long now = System.currentTimeMillis();
        long intervalMillis = module.getIntervalMillis();

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();

            if (!module.isProgressLoaded(uuid)) {
                module.loadPlayerProgress(uuid);
                lastTick.put(uuid, now);
                continue;
            }

            long previousTick = lastTick.getOrDefault(uuid, now);
            long delta = Math.max(0L, now - previousTick);
            lastTick.put(uuid, now);

            if (delta <= 0L) continue;
            if (module.isPlayerAfk(player)) continue;

            long progress = module.addProgress(uuid, delta);
            if (progress < intervalMillis) continue;

            long cycles = progress / intervalMillis;
            long remainingProgress = progress % intervalMillis;
            module.setProgress(uuid, remainingProgress);
            module.savePlayerProgressAsync(uuid);

            int salaryPerCycle = getSalary(player);
            if (salaryPerCycle <= 0) continue;

            long totalLong = cycles * salaryPerCycle;
            int totalToPay = (int) Math.min(Integer.MAX_VALUE, totalLong);
            int cyclesInt = (int) Math.min(Integer.MAX_VALUE, cycles);
            deliverSalary(player, totalToPay, cyclesInt);
        }

        if ((now - lastPeriodicSave) >= 60_000L) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                module.savePlayerProgressAsync(player.getUniqueId());
            }
            lastPeriodicSave = now;
        }

        Iterator<UUID> iterator = lastTick.keySet().iterator();
        while (iterator.hasNext()) {
            UUID uuid = iterator.next();
            if (Bukkit.getPlayer(uuid) == null) {
                iterator.remove();
            }
        }
    }

    private int getSalary(Player player) {
        if (module.getSalariesConfig().getConfigurationSection("salaries") == null) return 0;

        int highest = 0;
        for (String rank : module.getSalariesConfig().getConfigurationSection("salaries").getKeys(false)) {
            if (player.hasPermission("aevumcore.salary.rank." + rank)) {
                int amount = module.getSalariesConfig().getInt("salaries." + rank);
                if (amount > highest) highest = amount;
            }
        }

        if (highest == 0) {
            highest = module.getSalariesConfig().getInt("salaries.default", 0);
        }
        return highest;
    }

    private void deliverSalary(Player player, int amount, int cycles) {
        if (amount <= 0) return;

        String mode = plugin.getConfig().getString("salary.reward-type", "ITEM");

        if ("VAULT".equalsIgnoreCase(mode) && module.getEconomy() != null) {
            module.getEconomy().depositPlayer(player, amount);
        } else {
            String configuredItem = plugin.getConfig().getString("salary.starting-money-item", "EMERALD");
            Material material = resolveRewardMaterial(configuredItem);
            if (material != null) {
                giveMaterial(player, material, amount);
            } else if (!tryGiveNamespacedByCommand(player, configuredItem, amount)) {
                plugin.getLogger().warning("[Salary] Item invalide '" + configuredItem + "', fallback vers EMERALD.");
                giveMaterial(player, Material.EMERALD, amount);
            }
        }

        String prefix = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("prefix", "&f[&bAevumCore&f]"));
        if (cycles > 1) {
            player.sendMessage(prefix + ChatColor.GREEN + " Vous avez recu " + ChatColor.GOLD + cycles + ChatColor.GREEN + " salaires pour un total de " + ChatColor.GOLD + amount + ChatColor.GREEN + " !");
        } else {
            player.sendMessage(prefix + ChatColor.GREEN + " Vous avez recu votre salaire de " + ChatColor.GOLD + amount + ChatColor.GREEN + " !");
        }
    }

    private Material resolveRewardMaterial(String configured) {
        if (configured == null || configured.isBlank()) {
            return Material.EMERALD;
        }

        String trimmed = configured.trim();

        NamespacedKey key = NamespacedKey.fromString(trimmed.toLowerCase(Locale.ROOT));
        if (key != null) {
            Material byRegistry = Registry.MATERIAL.get(key);
            if (byRegistry != null) {
                return byRegistry;
            }
        }

        Material byMatch = Material.matchMaterial(trimmed);
        if (byMatch != null) {
            return byMatch;
        }

        if (trimmed.contains(":")) {
            String[] split = trimmed.split(":", 2);
            if (split.length == 2 && !split[1].isBlank()) {
                Material fromPath = Material.matchMaterial(split[1]);
                if (fromPath != null) {
                    return fromPath;
                }
            }
        }

        return null;
    }

    private void giveMaterial(Player player, Material material, int amount) {
        int given = 0;
        while (given < amount) {
            int toGive = Math.min(material.getMaxStackSize(), amount - given);
            if (toGive <= 0) break;

            ItemStack item = new ItemStack(material, toGive);
            player.getInventory().addItem(item).values().forEach(remaining -> {
                player.getWorld().dropItemNaturally(player.getLocation(), remaining);
            });
            given += toGive;
        }
    }

    private boolean tryGiveNamespacedByCommand(Player player, String rawItemId, int amount) {
        if (rawItemId == null || rawItemId.isBlank()) return false;

        String itemId = rawItemId.trim().toLowerCase(Locale.ROOT);
        if (!itemId.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            return false;
        }

        ConsoleCommandSender console = Bukkit.getConsoleSender();
        return Bukkit.dispatchCommand(console, "minecraft:give " + player.getName() + " " + itemId + " " + amount);
    }
}
