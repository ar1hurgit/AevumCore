package me.ar1hurgit.aevumcore.core.tab;

import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;

public abstract class BaseTabCompleter implements TabCompleter {

    protected List<String> empty() {
        return new ArrayList<>();
    }
}