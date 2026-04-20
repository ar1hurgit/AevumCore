package me.ar1hurgit.aevumcore.modules.salary;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;

import java.util.Locale;

public final class SalaryRewardResolver {

    private SalaryRewardResolver() {
    }

    public static Material resolveMaterial(String configured) {
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

        if (!trimmed.contains(":")) {
            return null;
        }

        String[] split = trimmed.split(":", 2);
        if (split.length != 2 || split[1].isBlank()) {
            return null;
        }

        return Material.matchMaterial(split[1]);
    }
}
