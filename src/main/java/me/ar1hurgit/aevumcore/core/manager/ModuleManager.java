package me.ar1hurgit.aevumcore.core.manager;

import me.ar1hurgit.aevumcore.core.module.Module;

import java.util.HashMap;
import java.util.Map;

public class ModuleManager {

    private final Map<String, Module> modules = new HashMap<>();

    public void register(Module module) {
        modules.put(module.getName().toLowerCase(), module);
    }

    public void enableAll() {
        modules.values().forEach(Module::enable);
    }

    public void disableAll() {
        modules.values().forEach(Module::disable);
    }

    public Module get(String name) {
        return modules.get(name.toLowerCase());
    }
}