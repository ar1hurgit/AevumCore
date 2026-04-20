package me.ar1hurgit.aevumcore.modules.salary;

import me.ar1hurgit.aevumcore.AevumCore;
import me.ar1hurgit.aevumcore.core.command.CommandBindings;
import me.ar1hurgit.aevumcore.core.module.AbstractModule;
import me.ar1hurgit.aevumcore.core.module.Module;
import me.ar1hurgit.aevumcore.modules.antiafk.AntiAFKModule;
import me.ar1hurgit.aevumcore.storage.database.DatabaseManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class SalaryModule extends AbstractModule {

    private final AevumCore plugin;
    private final DatabaseManager databaseManager;
    private final SalaryProgressRepository progressRepository;
    private FileConfiguration salariesConfig;
    private File salariesFile;
    private Economy economy;
    private SalaryTask salaryTask;
    private final Map<UUID, Long> progressCache = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> loadingPlayers = new ConcurrentHashMap<>();
    private volatile boolean ready;

    public SalaryModule(AevumCore plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.progressRepository = new SalaryProgressRepository(databaseManager);
    }

    @Override
    public String getName() {
        return "salary";
    }

    @Override
    protected void onEnable() {
        if (!plugin.getConfig().getBoolean("salary.enabled", true)) {
            return;
        }

        loadSalariesConfig();
        ready = false;

        if (plugin.getServer().getPluginManager().getPlugin("Vault") != null) {
            RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp != null) {
                economy = rsp.getProvider();
            }
        }

        databaseManager.runAsync(progressRepository::initializeSchema)
                .whenComplete((unused, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!plugin.isEnabled() || throwable != null) {
                        ready = false;
                        if (throwable != null) {
                            plugin.getLogger().severe("[Salary] Erreur lors de la creation de la table player_data : " + throwable.getMessage());
                        }
                        return;
                    }

                    ready = true;
                    registerRuntime();
                    Bukkit.getLogger().info(plugin.getConfig().getString("prefix", "[AevumCore]") + " Salary module enabled");
                }));
    }

    @Override
    protected void onDisable() {
        ready = false;
        if (salaryTask != null) {
            salaryTask.cancel();
            salaryTask = null;
        }
        saveAllProgressSync();
        Bukkit.getLogger().info(plugin.getConfig().getString("prefix", "[AevumCore]") + " Salary module disabled");
    }

    private void registerRuntime() {
        SalaryCommand salaryCommand = new SalaryCommand(plugin, this);
        CommandBindings.bind(plugin, "salary", salaryCommand, salaryCommand);
        CommandBindings.bind(plugin, "playtime", new PlaytimeCommand(plugin, this));

        plugin.getServer().getPluginManager().registerEvents(new SalaryListener(plugin, this), plugin);

        salaryTask = new SalaryTask(plugin, this);
        salaryTask.runTaskTimer(plugin, 20L, 20L);

        for (Player player : Bukkit.getOnlinePlayers()) {
            loadPlayerProgress(player.getUniqueId());
        }
    }

    private void loadSalariesConfig() {
        salariesFile = new File(plugin.getDataFolder(), "salaries.yml");
        if (!salariesFile.exists()) {
            plugin.saveResource("salaries.yml", false);
        }
        salariesConfig = YamlConfiguration.loadConfiguration(salariesFile);
    }

    public boolean isReady() {
        return ready;
    }

    public FileConfiguration getSalariesConfig() {
        return salariesConfig;
    }

    public void saveSalariesConfig() {
        try {
            salariesConfig.save(salariesFile);
        } catch (IOException exception) {
            plugin.getLogger().severe("[Salary] Impossible de sauvegarder salaries.yml : " + exception.getMessage());
        }
    }

    public Economy getEconomy() {
        return economy;
    }

    public long getIntervalMillis() {
        int intervalMinutes = Math.max(1, plugin.getConfig().getInt("salary.interval", 60));
        return intervalMinutes * 60L * 1000L;
    }

    public boolean isPlayerAfk(Player player) {
        Module antiAfk = plugin.getModuleManager().get("antiafk");
        if (antiAfk instanceof AntiAFKModule antiAFKModule && antiAfk.isEnabled()) {
            return antiAFKModule.isAfk(player.getUniqueId());
        }
        return false;
    }

    public boolean isProgressLoaded(UUID uuid) {
        return progressCache.containsKey(uuid);
    }

    public long getProgress(UUID uuid) {
        return progressCache.getOrDefault(uuid, 0L);
    }

    public void setProgress(UUID uuid, long progress) {
        progressCache.put(uuid, Math.max(0L, progress));
    }

    public long addProgress(UUID uuid, long delta) {
        return progressCache.merge(uuid, Math.max(0L, delta), Long::sum);
    }

    public void unloadPlayer(UUID uuid) {
        progressCache.remove(uuid);
        loadingPlayers.remove(uuid);
    }

    public void loadPlayerProgress(UUID uuid) {
        if (!ready || isProgressLoaded(uuid) || loadingPlayers.putIfAbsent(uuid, true) != null) {
            return;
        }

        databaseManager.supplyAsync(() -> {
            long progress = progressRepository.loadProgress(uuid);
            long now = System.currentTimeMillis();
            if (progress <= 0L) {
                progressRepository.ensurePlayerRow(uuid, now);
            }
            return progress;
        }).whenComplete((progress, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (throwable != null) {
                plugin.getLogger().severe("[Salary] Erreur lors du chargement du cooldown salaire de " + uuid + " : " + throwable.getMessage());
                loadingPlayers.remove(uuid);
                return;
            }

            setProgress(uuid, progress);
            loadingPlayers.remove(uuid);
        }));
    }

    public void fetchProgressAsync(UUID uuid, Consumer<Long> callback) {
        if (!ready) {
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(null));
            return;
        }

        if (isProgressLoaded(uuid)) {
            callback.accept(getProgress(uuid));
            return;
        }

        databaseManager.supplyAsync(() -> progressRepository.loadProgress(uuid))
                .whenComplete((progress, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (throwable != null) {
                        plugin.getLogger().severe("[Salary] Erreur lors de la lecture du cooldown salaire de " + uuid + " : " + throwable.getMessage());
                        callback.accept(null);
                        return;
                    }

                    callback.accept(progress);
                }));
    }

    public void savePlayerProgressAsync(UUID uuid) {
        if (!ready || !isProgressLoaded(uuid)) {
            return;
        }
        saveProgressAsync(uuid, getProgress(uuid));
    }

    public void saveProgressAsync(UUID uuid, long progress) {
        if (!ready) {
            return;
        }

        long safeProgress = Math.max(0L, progress);
        databaseManager.runAsync(() -> progressRepository.saveProgress(uuid, safeProgress))
                .exceptionally(throwable -> {
                    plugin.getLogger().severe("[Salary] Erreur lors de la sauvegarde du cooldown salaire de " + uuid + " : " + throwable.getMessage());
                    return null;
                });
    }

    private void saveAllProgressSync() {
        if (progressCache.isEmpty()) {
            return;
        }

        for (Map.Entry<UUID, Long> entry : progressCache.entrySet()) {
            try {
                progressRepository.saveProgress(entry.getKey(), entry.getValue());
            } catch (SQLException exception) {
                plugin.getLogger().severe("[Salary] Erreur lors de la sauvegarde finale des cooldowns : " + exception.getMessage());
            }
        }
    }
}
