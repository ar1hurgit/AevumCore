package me.ar1hurgit.aevumcore.modules.salary;

import me.ar1hurgit.aevumcore.AevumCore;
import me.ar1hurgit.aevumcore.core.module.AbstractModule;
import me.ar1hurgit.aevumcore.storage.database.DatabaseManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class SalaryModule extends AbstractModule {

    private final AevumCore plugin;
    private final DatabaseManager databaseManager;
    private FileConfiguration salariesConfig;
    private File salariesFile;
    private Economy economy;

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

        // Create table asynchronously
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection con = databaseManager.getConnection();
                 PreparedStatement stmt = con.prepareStatement(
                     "CREATE TABLE IF NOT EXISTS player_data (" +
                     "uuid VARCHAR(36) PRIMARY KEY," +
                     "last_salary BIGINT DEFAULT 0" +
                     ")"
                 )) {
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("[Salary] Erreur lors de la création de la table player_data : " + e.getMessage());
            }
        });

        // Vault setup (optional like in FirstJoin)
        if (plugin.getServer().getPluginManager().getPlugin("Vault") != null) {
            RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
            if (rsp != null) economy = rsp.getProvider();
        }

        // Register commands
        plugin.getCommand("salary").setExecutor(new SalaryCommand(plugin, this));
        plugin.getCommand("playtime").setExecutor(new PlaytimeCommand(plugin));

        // Register listener
        plugin.getServer().getPluginManager().registerEvents(new SalaryListener(plugin, this), plugin);

        // Start task
        new SalaryTask(plugin, this).runTaskTimer(plugin, 20L * 60, 20L * 60);

        Bukkit.getLogger().info(plugin.getConfig().getString("prefix", "[AevumCore]") + " Salary module enabled");
    }

    @Override
    protected void onDisable() {
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
            e.printStackTrace();
        }
    }

    public Economy getEconomy() {
        return economy;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
}
