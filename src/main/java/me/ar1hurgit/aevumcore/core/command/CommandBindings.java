package me.ar1hurgit.aevumcore.core.command;

import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

public final class CommandBindings {

    private CommandBindings() {
    }

    public static boolean bind(JavaPlugin plugin, String commandName, CommandExecutor executor) {
        return bind(plugin, commandName, executor, null);
    }

    public static boolean bind(JavaPlugin plugin, String commandName, CommandExecutor executor, TabCompleter tabCompleter) {
        PluginCommand command = plugin.getCommand(commandName);
        if (command == null) {
            return false;
        }

        command.setExecutor(executor);
        if (tabCompleter != null) {
            command.setTabCompleter(tabCompleter);
        }
        return true;
    }
}
