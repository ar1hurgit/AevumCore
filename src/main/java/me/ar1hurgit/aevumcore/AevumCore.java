package me.ar1hurgit.aevumcore;

import me.ar1hurgit.aevumcore.core.manager.ModuleManager;
import me.ar1hurgit.aevumcore.modules.antiafk.AntiAFKModule;
import me.ar1hurgit.aevumcore.modules.chat.ChatModule;
import me.ar1hurgit.aevumcore.modules.dice.DiceModule;
import me.ar1hurgit.aevumcore.modules.explosion.ExplosionModule;
import me.ar1hurgit.aevumcore.modules.firstjoin.FirstJoinModule;
import me.ar1hurgit.aevumcore.modules.godmode.GodModeModule;
import me.ar1hurgit.aevumcore.modules.lastconnection.LastConnectionModule;
import me.ar1hurgit.aevumcore.modules.maintenance.MaintenanceModule;
import me.ar1hurgit.aevumcore.modules.nickname.NicknameModule;
import me.ar1hurgit.aevumcore.modules.report.ReportModule;
import me.ar1hurgit.aevumcore.modules.salary.SalaryModule;
import me.ar1hurgit.aevumcore.modules.vanish.VanishModule;
import me.ar1hurgit.aevumcore.modules.vision.VisionModule;
import me.ar1hurgit.aevumcore.storage.database.DatabaseManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class AevumCore extends JavaPlugin {

    private ModuleManager moduleManager;
    private DatabaseManager databaseManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // Database
        databaseManager = new DatabaseManager(this);
        databaseManager.connect();

        // Modules
        moduleManager = new ModuleManager();
        moduleManager.register(new DiceModule(this));
        moduleManager.register(new FirstJoinModule(this));
        moduleManager.register(new MaintenanceModule(this));
        moduleManager.register(new LastConnectionModule(this, databaseManager));
        moduleManager.register(new AntiAFKModule(this));
        moduleManager.register(new VanishModule(this));
        moduleManager.register(new NicknameModule(this, databaseManager));
        moduleManager.register(new ExplosionModule(this));
        moduleManager.register(new GodModeModule(this));
        moduleManager.register(new VisionModule(this));
        moduleManager.register(new SalaryModule(this, databaseManager));
        moduleManager.register(new ChatModule(this));
        moduleManager.register(new ReportModule(this, databaseManager));
        moduleManager.enableAll();
    }

    @Override
    public void onDisable() {
        moduleManager.disableAll();
        if (databaseManager != null) databaseManager.disconnect();
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public ModuleManager getModuleManager() {
        return moduleManager;
    }
}
