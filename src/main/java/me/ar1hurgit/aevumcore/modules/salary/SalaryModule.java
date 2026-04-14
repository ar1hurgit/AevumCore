package me.ar1hurgit.aevumcore.modules.salary;

import me.ar1hurgit.aevumcore.AevumCore;
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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class SalaryModule extends AbstractModule {

    private final AevumCore plugin;
    private final DatabaseManager databaseManager;
    private FileConfiguration salariesConfig;
    private File salariesFile;
    private Economy economy;
    private SalaryTask salaryTask;
    private final Map<UUID, Long> progressCache = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> loadingPlayers = new ConcurrentHashMap<>();

    public SalaryModule(AevumCore plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    @Override
    public String getName() {
        return "salary";
    }

    @Override
    protected void onEnable() {
        if (!plugin.getConfig().getBoolean("salary.enabled", true)) return;

        loadSalariesConfig();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection con = databaseManager.getConnection();
                 PreparedStatement stmt = con.prepareStatement(
                         "CREATE TABLE IF NOT EXISTS player_data (" +
                                 "uuid VARCHAR(36) PRIMARY KEY," +
                                 "last_salary BIGINT DEFAULT 0," +
                                 "salary_progress BIGINT DEFAULT 0" +
                                 ")"
                 )) {
                stmt.executeUpdate();

                try (PreparedStatement alter = con.prepareStatement("ALTER TABLE player_data ADD COLUMN salary_progress BIGINT DEFAULT 0")) {
                    alter.executeUpdate();
                } catch (SQLException ignored) {
                    // Column already exists.
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("[Salary] Erreur lors de la creation de la table player_data : " + e.getMessage());
            }
        });

        if (plugin.getServer().getPluginManager().getPlugin("Vault") != null) {
            RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp != null) economy = rsp.getProvider();
        }

        if (plugin.getCommand("salary") != null) {
            SalaryCommand salaryCommand = new SalaryCommand(plugin, this);
            plugin.getCommand("salary").setExecutor(salaryCommand);
            plugin.getCommand("salary").setTabCompleter(salaryCommand);
        }

        if (plugin.getCommand("playtime") != null) {
            plugin.getCommand("playtime").setExecutor(new PlaytimeCommand(plugin));
        }

        plugin.getServer().getPluginManager().registerEvents(new SalaryListener(plugin, this), plugin);

        salaryTask = new SalaryTask(plugin, this);
        salaryTask.runTaskTimer(plugin, 20L, 20L);

        for (Player player : Bukkit.getOnlinePlayers()) {
            loadPlayerProgress(player.getUniqueId());
        }

        Bukkit.getLogger().info(plugin.getConfig().getString("prefix", "[AevumCore]") + " Salary module enabled");
    }

    @Override
    protected void onDisable() {
        if (salaryTask != null) {
            salaryTask.cancel();
        }
        saveAllProgressSync();
        Bukkit.getLogger().info(plugin.getConfig().getString("prefix", "[AevumCore]") + " Salary module disabled");
    }

    private void loadSalariesConfig() {
        salariesFile = new File(plugin.getDataFolder(), "salaries.yml");
        if (!salariesFile.exists()) {
            plugin.saveResource("salaries.yml", false);
        }
        salariesConfig = YamlConfiguration.loadConfiguration(salariesFile);
    }

    public FileConfiguration getSalariesConfig() {
        return salariesConfig;
    }

    public void saveSalariesConfig() {
        try {
            salariesConfig.save(salariesFile);
        } catch (IOException e) {
            plugin.getLogger().severe("[Salary] Impossible de sauvegarder salaries.yml : " + e.getMessage());
        }
    }

    public Economy getEconomy() {
        return economy;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
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
        if (isProgressLoaded(uuid) || loadingPlayers.putIfAbsent(uuid, true) != null) {
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            long progress = 0L;
            long now = System.currentTimeMillis();
            try (Connection con = databaseManager.getConnection();
                 PreparedStatement stmt = con.prepareStatement("SELECT salary_progress FROM player_data WHERE uuid = ?")) {
                stmt.setString(1, uuid.toString());
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    progress = Math.max(0L, rs.getLong("salary_progress"));
                } else {
                    try (PreparedStatement insert = con.prepareStatement("INSERT INTO player_data (uuid, last_salary, salary_progress) VALUES (?, ?, ?)")) {
                        insert.setString(1, uuid.toString());
                        insert.setLong(2, now);
                        insert.setLong(3, 0L);
                        insert.executeUpdate();
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("[Salary] Erreur lors du chargement du cooldown salaire de " + uuid + " : " + e.getMessage());
            }

            long finalProgress = progress;
            Bukkit.getScheduler().runTask(plugin, () -> {
                setProgress(uuid, finalProgress);
                loadingPlayers.remove(uuid);
            });
        });
    }

    public void fetchProgressAsync(UUID uuid, Consumer<Long> callback) {
        if (isProgressLoaded(uuid)) {
            callback.accept(getProgress(uuid));
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            long progress = 0L;
            try (Connection con = databaseManager.getConnection();
                 PreparedStatement stmt = con.prepareStatement("SELECT salary_progress FROM player_data WHERE uuid = ?")) {
                stmt.setString(1, uuid.toString());
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    progress = Math.max(0L, rs.getLong("salary_progress"));
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("[Salary] Erreur lors de la lecture du cooldown salaire de " + uuid + " : " + e.getMessage());
            }

            long finalProgress = progress;
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(finalProgress));
        });
    }

    public void savePlayerProgressAsync(UUID uuid) {
        if (!isProgressLoaded(uuid)) return;
        saveProgressAsync(uuid, getProgress(uuid));
    }

    public void saveProgressAsync(UUID uuid, long progress) {
        long safeProgress = Math.max(0L, progress);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection con = databaseManager.getConnection()) {
                boolean updated;
                try (PreparedStatement update = con.prepareStatement("UPDATE player_data SET salary_progress = ? WHERE uuid = ?")) {
                    update.setLong(1, safeProgress);
                    update.setString(2, uuid.toString());
                    updated = update.executeUpdate() > 0;
                }

                if (!updated) {
                    try (PreparedStatement insert = con.prepareStatement("INSERT INTO player_data (uuid, last_salary, salary_progress) VALUES (?, ?, ?)")) {
                        insert.setString(1, uuid.toString());
                        insert.setLong(2, System.currentTimeMillis());
                        insert.setLong(3, safeProgress);
                        insert.executeUpdate();
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("[Salary] Erreur lors de la sauvegarde du cooldown salaire de " + uuid + " : " + e.getMessage());
            }
        });
    }

    private void saveAllProgressSync() {
        if (progressCache.isEmpty()) return;
        try (Connection con = databaseManager.getConnection()) {
            for (Map.Entry<UUID, Long> entry : progressCache.entrySet()) {
                boolean updated;
                try (PreparedStatement update = con.prepareStatement("UPDATE player_data SET salary_progress = ? WHERE uuid = ?")) {
                    update.setLong(1, Math.max(0L, entry.getValue()));
                    update.setString(2, entry.getKey().toString());
                    updated = update.executeUpdate() > 0;
                }

                if (!updated) {
                    try (PreparedStatement insert = con.prepareStatement("INSERT INTO player_data (uuid, last_salary, salary_progress) VALUES (?, ?, ?)")) {
                        insert.setString(1, entry.getKey().toString());
                        insert.setLong(2, System.currentTimeMillis());
                        insert.setLong(3, Math.max(0L, entry.getValue()));
                        insert.executeUpdate();
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[Salary] Erreur lors de la sauvegarde finale des cooldowns : " + e.getMessage());
        }
    }
}
