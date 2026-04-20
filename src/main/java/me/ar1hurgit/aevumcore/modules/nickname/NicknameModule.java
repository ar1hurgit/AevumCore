package me.ar1hurgit.aevumcore.modules.nickname;

import me.ar1hurgit.aevumcore.AevumCore;
import me.ar1hurgit.aevumcore.core.command.CommandBindings;
import me.ar1hurgit.aevumcore.core.module.AbstractModule;
import me.ar1hurgit.aevumcore.storage.database.DatabaseManager;
import org.bukkit.Bukkit;

public class NicknameModule extends AbstractModule {

    private final AevumCore plugin;
    private final DatabaseManager databaseManager;
    private NicknameManager manager;

    public NicknameModule(AevumCore plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    @Override
    public String getName() {
        return "nickname";
    }

    @Override
    protected void onEnable() {
        if (!plugin.getConfig().getBoolean("nickname.enabled", true)) return;

        manager = new NicknameManager(plugin, databaseManager);
        manager.enable();

        plugin.getServer().getPluginManager().registerEvents(new NicknameListener(manager), plugin);

        NicknameCommand command = new NicknameCommand(plugin, manager);
        CommandBindings.bind(plugin, "nom", command, command);
        CommandBindings.bind(plugin, "realname", command, command);

        Bukkit.getLogger().info(plugin.getConfig().getString("prefix", "[AevumCore]") + " Nickname module enabled");
    }

    @Override
    protected void onDisable() {
        if (manager != null) {
            manager.disable();
        }

        Bukkit.getLogger().info(plugin.getConfig().getString("prefix", "[AevumCore]") + " Nickname module disabled");
    }

    public NicknameManager getManager() {
        return manager;
    }
}
