package me.ar1hurgit.aevumcore.modules.dice;

import me.ar1hurgit.aevumcore.core.tab.BaseTabCompleter;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.List;

public class DiceTab extends BaseTabCompleter {

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return empty();
    }
}
